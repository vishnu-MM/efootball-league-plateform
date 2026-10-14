package com.leagueapp.match;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

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
class PastSeasonsApiTests {

	@Autowired
	MockMvc mvc;

	ApiSupport api;

	@BeforeEach
	void setUp() {
		api = new ApiSupport(mvc);
	}

	@Test
	void listsSeasonsNewestFirstAndOpensAPastOne() throws Exception {
		User owner = api.login("Past Owner");
		User ann = api.login("Past Ann");
		String slug = api.createLeague(owner, "Past Cup", "PUBLIC");
		api.join(owner, slug, ann);

		String first = startSeason(owner, slug, "First Season", owner, ann);
		String fixture = JsonPath.read(api.get("/api/public/leagues/" + slug + "/season", owner)
			.andReturn().getResponse().getContentAsString(), "$.season.fixtures[0].id");
		api.send(post("/api/leagues/" + slug + "/fixtures/" + fixture + "/forfeit"), owner,
				"{\"forfeiting\":\"HOME\",\"reason\":\"No show\"}")
			.andExpect(status().isNoContent());
		String second = startSeason(owner, slug, "Second Season", owner, ann);

		api.get("/api/public/leagues/" + slug + "/seasons", null)
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.length()").value(2))
			.andExpect(jsonPath("$[0].id").value(second))
			.andExpect(jsonPath("$[0].status").value("ACTIVE"))
			.andExpect(jsonPath("$[1].name").value("First Season"))
			.andExpect(jsonPath("$[1].status").value("COMPLETED"))
			.andExpect(jsonPath("$[1].completedAt").isNotEmpty());

		api.get("/api/public/leagues/" + slug + "/season?season=" + first, null)
			.andExpect(jsonPath("$.season.id").value(first))
			.andExpect(jsonPath("$.season.fixtures[0].id").value(fixture));
		api.get("/api/public/leagues/" + slug + "/standings?season=" + first, null)
			.andExpect(jsonPath("$.season.id").value(first))
			.andExpect(jsonPath("$.rows[0].points").value(3));
		api.get("/api/public/leagues/" + slug + "/stats?season=" + first, null)
			.andExpect(jsonPath("$.season.id").value(first));

		api.get("/api/public/leagues/" + slug + "/season", null).andExpect(jsonPath("$.season.id").value(second));
		api.get("/api/public/leagues/" + slug + "/standings", null).andExpect(jsonPath("$.season.id").value(second));
		api.get("/api/public/leagues/" + slug + "/stats", null).andExpect(jsonPath("$.season.id").value(second));
	}

	@Test
	void seasonFromAnotherLeagueIs404() throws Exception {
		User owner = api.login("Other Season Owner");
		User ann = api.login("Other Season Ann");
		String slug = api.createLeague(owner, "Season Home Cup", "PUBLIC");
		String other = api.createLeague(owner, "Season Away Cup", "PUBLIC");
		api.join(owner, slug, ann);
		api.join(owner, other, ann);
		startSeason(owner, slug, "Home Season", owner, ann);
		String foreign = startSeason(owner, other, "Away Season", owner, ann);

		api.get("/api/public/leagues/" + slug + "/season?season=" + foreign, null).andExpect(status().isNotFound());
		api.get("/api/public/leagues/" + slug + "/standings?season=" + foreign, null).andExpect(status().isNotFound());
		api.get("/api/public/leagues/" + slug + "/stats?season=" + foreign, null).andExpect(status().isNotFound());
	}

	private String startSeason(User owner, String slug, String name, User a, User b) throws Exception {
		String body = api.send(post("/api/leagues/" + slug + "/seasons"), owner, "{\"name\":\"" + name
				+ "\",\"participantIds\":[\"" + a.id() + "\",\"" + b.id() + "\"],\"format\":\"SINGLE\",\"pointsWin\":3,\"pointsDraw\":1}")
			.andExpect(status().isCreated())
			.andReturn().getResponse().getContentAsString();
		return JsonPath.read(body, "$.id");
	}

}
