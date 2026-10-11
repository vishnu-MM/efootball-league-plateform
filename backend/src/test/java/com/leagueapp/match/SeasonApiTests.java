package com.leagueapp.match;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import com.jayway.jsonpath.JsonPath;
import com.leagueapp.ApiSupport;
import com.leagueapp.ApiSupport.User;
import com.leagueapp.TestcontainersConfiguration;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("dev")
@Import(TestcontainersConfiguration.class)
class SeasonApiTests {

	@Autowired
	MockMvc mvc;

	ApiSupport api;

	@BeforeEach
	void setUp() {
		api = new ApiSupport(mvc);
	}

	@Test
	void aWholeSeasonFromFixturesToFinalTable() throws Exception {
		User owner = api.login("Season Owner");
		User ann = api.login("Season Ann");
		User ben = api.login("Season Ben");
		String slug = api.createLeague(owner, "Season Flow Cup", "PRIVATE");
		api.join(owner, slug, ann, ben);

		createSeason(owner, slug, List.of(owner, ann, ben), "SINGLE", null).andExpect(status().isCreated())
			.andExpect(jsonPath("$.name").value("Season 1"));
		api.get(seasonPath(slug), ann)
			.andExpect(jsonPath("$.season.status").value("ACTIVE"))
			.andExpect(jsonPath("$.season.fixtures.length()").value(3))
			.andExpect(jsonPath("$.season.players.length()").value(3));

		// Ann reports a win over Ben with a scorer; Ben confirms.
		String annBen = fixtureBetween(slug, ann, ann, ben);
		report(slug, annBen, ann, ann, ben, 2, 1, "{\"side\":\"%s\",\"scorer\":\"Messi\",\"assist\":\"Pedri\"}")
			.andExpect(status().isNoContent());
		api.get(detailPath(slug, annBen), ben)
			.andExpect(jsonPath("$.fixture.status").value("AWAITING_CONFIRMATION"))
			.andExpect(jsonPath("$.fixture.actions.length()").value(2));
		api.get(detailPath(slug, annBen), ann).andExpect(jsonPath("$.fixture.actions.length()").value(0));
		api.send(post(fixturePath(slug, annBen) + "/confirm"), ann, null).andExpect(status().isConflict());
		api.send(post(fixturePath(slug, annBen) + "/confirm"), ben, null).andExpect(status().isNoContent());
		api.get(detailPath(slug, annBen), ben)
			.andExpect(jsonPath("$.fixture.status").value("CONFIRMED"))
			.andExpect(jsonPath("$.reports[0].goals[0].scorer").value("Messi"));

		// Ann reports against the owner; the owner disputes, then (as owner) settles it.
		String ownerAnn = fixtureBetween(slug, owner, owner, ann);
		report(slug, ownerAnn, ann, owner, ann, 0, 3, null).andExpect(status().isNoContent());
		api.send(post(fixturePath(slug, ownerAnn) + "/dispute"), owner, scoreJson(owner, ann, ownerAnn, slug, 1, 1, "It was a draw"))
			.andExpect(status().isNoContent());
		api.get(detailPath(slug, ownerAnn), ann)
			.andExpect(jsonPath("$.fixture.status").value("DISPUTED"))
			.andExpect(jsonPath("$.reports.length()").value(2));
		api.send(post(fixturePath(slug, ownerAnn) + "/resolve"), ann, scoreJson(owner, ann, ownerAnn, slug, 0, 3, null))
			.andExpect(status().isConflict());
		api.send(post(fixturePath(slug, ownerAnn) + "/resolve"), owner, scoreJson(owner, ann, ownerAnn, slug, 1, 1, null))
			.andExpect(status().isNoContent());
		api.get(detailPath(slug, ownerAnn), ann)
			.andExpect(jsonPath("$.fixture.decidedByAdmin").value(true))
			.andExpect(jsonPath("$.fixture.homeGoals").value(1));

		// Players in the active season can't be removed.
		api.send(delete("/api/leagues/" + slug + "/members/" + ben.id()), owner, null).andExpect(status().isConflict());

		// Last match: Ben reports, the owner confirms, and the season completes.
		String ownerBen = fixtureBetween(slug, owner, owner, ben);
		report(slug, ownerBen, ben, owner, ben, 0, 0, null).andExpect(status().isNoContent());
		api.send(post(fixturePath(slug, ownerBen) + "/confirm"), owner, null).andExpect(status().isNoContent());
		api.get(seasonPath(slug), owner).andExpect(jsonPath("$.season.status").value("COMPLETED"));

		// Ann: W 2-1, D 1-1 = 4 pts. Owner: D, D = 2. Ben: L, D = 1.
		api.get("/api/public/leagues/" + slug + "/standings", ann)
			.andExpect(jsonPath("$.season.active").value(false))
			.andExpect(jsonPath("$.rows[0].userId").value(ann.id()))
			.andExpect(jsonPath("$.rows[0].points").value(4))
			.andExpect(jsonPath("$.rows[0].goalsFor").value(3))
			.andExpect(jsonPath("$.rows[1].userId").value(owner.id()))
			.andExpect(jsonPath("$.rows[2].points").value(1));

		api.send(delete("/api/leagues/" + slug + "/members/" + ben.id()), owner, null).andExpect(status().isNoContent());
	}

	@Test
	void onlyOwnersAndAdminsStartSeasonsAndOnlyOneAtATime() throws Exception {
		User owner = api.login("Rules Owner");
		User member = api.login("Rules Member");
		User outsider = api.login("Rules Outsider");
		String slug = api.createLeague(owner, "Rules Cup", "PUBLIC");
		api.join(owner, slug, member);

		createSeason(member, slug, List.of(owner, member), "SINGLE", null).andExpect(status().isForbidden());
		createSeason(owner, slug, List.of(owner), "SINGLE", null).andExpect(status().isBadRequest());
		createSeason(owner, slug, List.of(owner, outsider), "SINGLE", null).andExpect(status().isBadRequest());
		api.send(post("/api/leagues/" + slug + "/seasons"), owner, """
				{"participantIds":["%s","%s"],"format":"SINGLE","pointsWin":3,"pointsDraw":1,"startDate":"2026-11-01"}"""
			.formatted(owner.id(), member.id())).andExpect(status().isBadRequest());

		createSeason(owner, slug, List.of(owner, member), "SINGLE", null).andExpect(status().isCreated());
		createSeason(owner, slug, List.of(owner, member), "SINGLE", null).andExpect(status().isConflict());
	}

	@Test
	void homeAndAwayWithPlayByDates() throws Exception {
		User owner = api.login("Dates Owner");
		User member = api.login("Dates Member");
		String slug = api.createLeague(owner, "Dates Cup", "PUBLIC");
		api.join(owner, slug, member);

		createSeason(owner, slug, List.of(owner, member), "HOME_AND_AWAY", "\"startDate\":\"2026-11-01\",\"daysPerRound\":7")
			.andExpect(status().isCreated());

		List<Map<String, Object>> fixtures = fixtures(slug, null);
		assertThat(fixtures).hasSize(2);
		assertThat(fixtures).extracting(f -> f.get("round")).containsExactly(1, 2);
		assertThat(fixtures).extracting(f -> f.get("playBy")).containsExactly("2026-11-07", "2026-11-14");
		assertThat(((Map<?, ?>) fixtures.get(0).get("home")).get("userId"))
			.isEqualTo(((Map<?, ?>) fixtures.get(1).get("away")).get("userId"));
	}

	@Test
	void scoresAreValidated() throws Exception {
		User owner = api.login("Valid Owner");
		User member = api.login("Valid Member");
		User stranger = api.login("Valid Stranger");
		String slug = api.createLeague(owner, "Valid Cup", "PUBLIC");
		api.join(owner, slug, member);
		createSeason(owner, slug, List.of(owner, member), "SINGLE", null).andExpect(status().isCreated());
		String id = fixtureBetween(slug, owner, owner, member);

		api.send(post(fixturePath(slug, id) + "/report"), owner,
				"{\"homeGoals\":1,\"awayGoals\":0,\"goals\":[{\"side\":\"HOME\",\"scorer\":\"A\"},{\"side\":\"HOME\",\"scorer\":\"B\"}]}")
			.andExpect(status().isBadRequest());
		api.send(post(fixturePath(slug, id) + "/report"), owner, "{\"homeGoals\":-1,\"awayGoals\":0}")
			.andExpect(status().isBadRequest());
		api.send(post(fixturePath(slug, id) + "/report"), stranger, "{\"homeGoals\":1,\"awayGoals\":0}")
			.andExpect(status().isConflict());
		api.get(detailPath(slug, "00000000-0000-0000-0000-000000000000"), owner).andExpect(status().isNotFound());
	}

	@Test
	void seasonIsNullBeforeTheFirstOne() throws Exception {
		User owner = api.login("Empty Owner");
		String slug = api.createLeague(owner, "Empty Cup", "PUBLIC");
		api.get(seasonPath(slug), null).andExpect(jsonPath("$.season").doesNotExist());
		api.get("/api/public/leagues/" + slug + "/standings", null)
			.andExpect(jsonPath("$.season").doesNotExist())
			.andExpect(jsonPath("$.rows.length()").value(0));
	}

	private org.springframework.test.web.servlet.ResultActions createSeason(User actor, String slug, List<User> players,
			String format, String timing) throws Exception {
		String ids = String.join(",", players.stream().map(u -> "\"" + u.id() + "\"").toList());
		String json = "{\"participantIds\":[" + ids + "],\"format\":\"" + format + "\",\"pointsWin\":3,\"pointsDraw\":1"
				+ (timing == null ? "" : "," + timing) + "}";
		return api.send(post("/api/leagues/" + slug + "/seasons"), actor, json);
	}

	/** Reports {@code homeOrA}–{@code awayOrB} goals, mapping a/b goals onto the fixture's real home/away sides. */
	private org.springframework.test.web.servlet.ResultActions report(String slug, String fixtureId, User reporter, User a,
			User b, int aGoals, int bGoals, String aGoalTemplate) throws Exception {
		Map<String, Object> fixture = fixture(slug, fixtureId, reporter);
		boolean aIsHome = a.id().equals(((Map<?, ?>) fixture.get("home")).get("userId"));
		int home = aIsHome ? aGoals : bGoals;
		int away = aIsHome ? bGoals : aGoals;
		String goals = aGoalTemplate == null ? "" : ",\"goals\":[" + aGoalTemplate.formatted(aIsHome ? "HOME" : "AWAY") + "]";
		return api.send(post(fixturePath(slug, fixtureId) + "/report"), reporter,
				"{\"homeGoals\":" + home + ",\"awayGoals\":" + away + goals + "}");
	}

	private String scoreJson(User a, User b, String fixtureId, String slug, int aGoals, int bGoals, String note)
			throws Exception {
		Map<String, Object> fixture = fixture(slug, fixtureId, a);
		boolean aIsHome = a.id().equals(((Map<?, ?>) fixture.get("home")).get("userId"));
		return "{\"homeGoals\":" + (aIsHome ? aGoals : bGoals) + ",\"awayGoals\":" + (aIsHome ? bGoals : aGoals)
				+ (note == null ? "" : ",\"note\":\"" + note + "\"") + "}";
	}

	private String fixtureBetween(String slug, User viewer, User a, User b) throws Exception {
		return fixtures(slug, viewer).stream()
			.filter(f -> {
				Object home = ((Map<?, ?>) f.get("home")).get("userId");
				Object away = ((Map<?, ?>) f.get("away")).get("userId");
				return (a.id().equals(home) && b.id().equals(away)) || (b.id().equals(home) && a.id().equals(away));
			})
			.map(f -> (String) f.get("id"))
			.findFirst()
			.orElseThrow();
	}

	private List<Map<String, Object>> fixtures(String slug, User viewer) throws Exception {
		return JsonPath.read(api.get(seasonPath(slug), viewer).andReturn().getResponse().getContentAsString(),
				"$.season.fixtures");
	}

	private Map<String, Object> fixture(String slug, String id, User viewer) throws Exception {
		return JsonPath.read(api.get(detailPath(slug, id), viewer).andReturn().getResponse().getContentAsString(),
				"$.fixture");
	}

	private static String seasonPath(String slug) {
		return "/api/public/leagues/" + slug + "/season";
	}

	private static String detailPath(String slug, String id) {
		return "/api/public/leagues/" + slug + "/fixtures/" + id;
	}

	private static String fixturePath(String slug, String id) {
		return "/api/leagues/" + slug + "/fixtures/" + id;
	}

}
