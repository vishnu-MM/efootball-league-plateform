package com.leagueapp.match;

import static org.assertj.core.api.Assertions.assertThat;
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

/** Admin corrections: overrides, forfeits and point deductions, and the league log they write. */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("dev")
@Import(TestcontainersConfiguration.class)
class CorrectionsApiTests {

	@Autowired
	MockMvc mvc;

	ApiSupport api;

	@BeforeEach
	void setUp() {
		api = new ApiSupport(mvc);
	}

	@Test
	void adminsCorrectResultsAndTheLeagueSeesItInTheLog() throws Exception {
		User owner = api.login("Fix Owner");
		User ann = api.login("Fix Ann");
		User ben = api.login("Fix Ben");
		User cat = api.login("Fix Cat");
		User stranger = api.login("Fix Stranger");
		String slug = api.createLeague(owner, "Corrections Cup", "PRIVATE");
		api.join(owner, slug, ann, ben, cat);
		startSeason(owner, slug, ann, ben, cat);

		// A played, confirmed match: only an owner or admin can override it, and only with a reason.
		Map<String, Object> annBen = fixtureBetween(slug, ann, ann, ben);
		String annBenId = (String) annBen.get("id");
		User home = side(annBen, "home", ann, ben);
		User away = home == ann ? ben : ann;
		api.send(post(path(slug, annBenId, "report")), home, "{\"homeGoals\":2,\"awayGoals\":1}").andExpect(status().isNoContent());
		api.send(post(path(slug, annBenId, "confirm")), away, null).andExpect(status().isNoContent());

		api.send(post(path(slug, annBenId, "override")), ann, "{\"homeGoals\":1,\"awayGoals\":1,\"reason\":\"no\"}")
			.andExpect(status().isConflict());
		api.send(post(path(slug, annBenId, "override")), owner, "{\"homeGoals\":1,\"awayGoals\":1}")
			.andExpect(status().isBadRequest());
		api.send(post(path(slug, annBenId, "override")), owner,
				"{\"homeGoals\":1,\"awayGoals\":1,\"reason\":\"Screenshot shows a draw\"}")
			.andExpect(status().isNoContent());
		api.get(detail(slug, annBenId), ann)
			.andExpect(jsonPath("$.fixture.status").value("CONFIRMED"))
			.andExpect(jsonPath("$.fixture.overridden").value(true))
			.andExpect(jsonPath("$.fixture.homeGoals").value(1))
			.andExpect(jsonPath("$.reports.length()").value(2))
			.andExpect(jsonPath("$.reports[1].kind").value("OVERRIDE"))
			.andExpect(jsonPath("$.reports[1].note").value("Screenshot shows a draw"));

		// Forfeits: the forfeiting side must lose; with no score it's 3–0.
		String annCat = (String) fixtureBetween(slug, ann, ann, cat).get("id");
		api.send(post(path(slug, annCat, "forfeit")), owner,
				"{\"forfeiting\":\"HOME\",\"homeGoals\":2,\"awayGoals\":1,\"reason\":\"No show\"}")
			.andExpect(status().isBadRequest());
		api.send(post(path(slug, annCat, "forfeit")), owner, "{\"forfeiting\":\"HOME\",\"reason\":\"No show\"}")
			.andExpect(status().isNoContent());
		api.get(detail(slug, annCat), cat)
			.andExpect(jsonPath("$.fixture.forfeit").value("HOME"))
			.andExpect(jsonPath("$.fixture.homeGoals").value(0))
			.andExpect(jsonPath("$.fixture.awayGoals").value(3));

		// Deductions: participants only, owner/admin only; revoking twice is refused.
		api.send(post("/api/leagues/" + slug + "/deductions"), ann,
				"{\"userId\":\"" + ben.id() + "\",\"points\":2,\"reason\":\"Late\"}")
			.andExpect(status().isForbidden());
		api.send(post("/api/leagues/" + slug + "/deductions"), owner,
				"{\"userId\":\"" + owner.id() + "\",\"points\":2,\"reason\":\"Late\"}")
			.andExpect(status().isBadRequest());
		String deduction = JsonPath.read(api.send(post("/api/leagues/" + slug + "/deductions"), owner,
				"{\"userId\":\"" + ben.id() + "\",\"points\":2,\"reason\":\"Played an ineligible card\"}")
			.andExpect(status().isCreated())
			.andReturn().getResponse().getContentAsString(), "$.id");
		api.get("/api/public/leagues/" + slug + "/standings", ann)
			.andExpect(jsonPath("$.deductions.length()").value(1))
			.andExpect(jsonPath("$.rows[?(@.userId=='" + ben.id() + "')].deducted").value(2))
			.andExpect(jsonPath("$.rows[?(@.userId=='" + ben.id() + "')].points").value(-1));
		api.send(post("/api/leagues/" + slug + "/deductions/" + deduction + "/revoke"), owner, "{\"reason\":\"Appeal won\"}")
			.andExpect(status().isNoContent());
		api.send(post("/api/leagues/" + slug + "/deductions/" + deduction + "/revoke"), owner, "{\"reason\":\"Again\"}")
			.andExpect(status().isConflict());
		api.get("/api/public/leagues/" + slug + "/standings", ann).andExpect(jsonPath("$.deductions.length()").value(0));

		// A double forfeit on the last fixture is a 0–0 draw and completes the season, which then can't change.
		String benCat = (String) fixtureBetween(slug, ann, ben, cat).get("id");
		api.send(post(path(slug, benCat, "forfeit")), owner, "{\"forfeiting\":\"BOTH\",\"reason\":\"Neither played\"}")
			.andExpect(status().isNoContent());
		api.get("/api/public/leagues/" + slug + "/season", ann).andExpect(jsonPath("$.season.status").value("COMPLETED"));
		api.send(post(path(slug, annBenId, "override")), owner, "{\"homeGoals\":3,\"awayGoals\":0,\"reason\":\"Late fix\"}")
			.andExpect(status().isConflict());

		// The log: newest first, members only for a private league, filterable to admin actions.
		String log = api.get("/api/public/leagues/" + slug + "/log", ann)
			.andExpect(status().isOk())
			.andReturn().getResponse().getContentAsString();
		List<String> types = JsonPath.read(log, "$.entries[*].type");
		assertThat(types).containsExactly("SEASON_COMPLETED", "FORFEIT", "DEDUCTION_REVOKED", "POINTS_DEDUCTED", "FORFEIT",
				"RESULT_OVERRIDDEN", "SEASON_STARTED");
		assertThat(JsonPath.<String>read(log, "$.entries[5].summary")).contains("Fix Owner changed").contains("2–1").contains("to 1–1");
		assertThat(JsonPath.<String>read(log, "$.entries[5].reason")).isEqualTo("Screenshot shows a draw");
		List<String> adminTypes = JsonPath.read(api.get("/api/public/leagues/" + slug + "/log?adminOnly=true", ann)
			.andReturn().getResponse().getContentAsString(), "$.entries[*].type");
		assertThat(adminTypes).doesNotContain("SEASON_COMPLETED").hasSize(6);
		api.get("/api/public/leagues/" + slug + "/log", stranger).andExpect(status().isNotFound());
	}

	private void startSeason(User owner, String slug, User... players) throws Exception {
		String ids = String.join(",", List.of(players).stream().map(u -> "\"" + u.id() + "\"").toList());
		api.send(post("/api/leagues/" + slug + "/seasons"), owner,
				"{\"participantIds\":[" + ids + "],\"format\":\"SINGLE\",\"pointsWin\":3,\"pointsDraw\":1}")
			.andExpect(status().isCreated());
	}

	private Map<String, Object> fixtureBetween(String slug, User viewer, User a, User b) throws Exception {
		List<Map<String, Object>> all = JsonPath.read(
				api.get("/api/public/leagues/" + slug + "/season", viewer).andReturn().getResponse().getContentAsString(),
				"$.season.fixtures");
		return all.stream().filter(f -> {
			Object home = ((Map<?, ?>) f.get("home")).get("userId");
			Object away = ((Map<?, ?>) f.get("away")).get("userId");
			return (a.id().equals(home) && b.id().equals(away)) || (b.id().equals(home) && a.id().equals(away));
		}).findFirst().orElseThrow();
	}

	private static User side(Map<String, Object> fixture, String side, User a, User b) {
		return a.id().equals(((Map<?, ?>) fixture.get(side)).get("userId")) ? a : b;
	}

	private static String path(String slug, String fixtureId, String action) {
		return "/api/leagues/" + slug + "/fixtures/" + fixtureId + "/" + action;
	}

	private static String detail(String slug, String fixtureId) {
		return "/api/public/leagues/" + slug + "/fixtures/" + fixtureId;
	}

}
