package com.leagueapp.stats;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

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
class StatsApiTests {

	@Autowired
	MockMvc mvc;

	ApiSupport api;

	@BeforeEach
	void setUp() {
		api = new ApiSupport(mvc);
	}

	@Test
	void leaderboardsCountFinalResultsOnly() throws Exception {
		User ann = api.login("Stats Ann");
		User ben = api.login("Stats Ben");
		String slug = api.createLeague(ann, "Stats Cup", "PUBLIC");
		api.join(ann, slug, ben);
		api.send(post("/api/leagues/" + slug + "/seasons"), ann,
				"{\"participantIds\":[\"" + ann.id() + "\",\"" + ben.id() + "\"],\"format\":\"SINGLE\",\"pointsWin\":3,\"pointsDraw\":1}")
			.andExpect(status().isCreated());
		Map<String, Object> fixture = JsonPath.read(api.get("/api/public/leagues/" + slug + "/season", ann)
			.andReturn().getResponse().getContentAsString(), "$.season.fixtures[0]");
		String id = (String) fixture.get("id");
		User home = ann.id().equals(((Map<?, ?>) fixture.get("home")).get("userId")) ? ann : ben;
		User away = home == ann ? ben : ann;

		// A typed MVP card id on a side without a squad is refused.
		api.send(post("/api/leagues/" + slug + "/fixtures/" + id + "/report"), home,
				"{\"homeGoals\":1,\"awayGoals\":0,\"mvp\":{\"side\":\"HOME\",\"cardId\":\"" + java.util.UUID.randomUUID() + "\"}}")
			.andExpect(status().isBadRequest());

		api.send(post("/api/leagues/" + slug + "/fixtures/" + id + "/report"), home, """
				{"homeGoals":2,"awayGoals":1,"goals":[
				  {"side":"HOME","scorer":"Kane","assist":"Saka"},
				  {"side":"HOME","scorer":"kane"},
				  {"side":"AWAY","scorer":"Haaland"}],
				 "mvp":{"side":"HOME","name":"Kane"}}""").andExpect(status().isNoContent());
		api.get("/api/public/leagues/" + slug + "/stats", null).andExpect(jsonPath("$.scorers.length()").value(0));
		api.get("/api/public/leagues/" + slug + "/fixtures/" + id, ann).andExpect(jsonPath("$.reports[0].mvp.name").value("Kane"));

		api.send(post("/api/leagues/" + slug + "/fixtures/" + id + "/confirm"), away, null).andExpect(status().isNoContent());
		api.get("/api/public/leagues/" + slug + "/stats", null)
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.season.name").value("Season 1"))
			.andExpect(jsonPath("$.scorers[0].name").value("Kane"))
			.andExpect(jsonPath("$.scorers[1].name").value("Haaland"))
			.andExpect(jsonPath("$.scorers[0].count").value(2))
			.andExpect(jsonPath("$.scorers[0].teamName").value(home == ann ? "Stats Ann" : "Stats Ben"))
			.andExpect(jsonPath("$.scorers[0].cardType").isEmpty())
			.andExpect(jsonPath("$.assists[0].name").value("Saka"))
			.andExpect(jsonPath("$.mvps[0].name").value("Kane"));
	}

}
