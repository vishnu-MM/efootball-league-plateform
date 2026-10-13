package com.leagueapp.notify;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.request;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.Duration;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.mock.web.MockHttpServletResponse;
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
class NotifyApiTests {

	@Autowired
	MockMvc mvc;

	ApiSupport api;

	@BeforeEach
	void setUp() {
		api = new ApiSupport(mvc);
	}

	@Test
	void peopleHearAboutWhatConcernsThemAndNotAboutTheirOwnActions() throws Exception {
		User owner = api.login("Note Owner");
		User ann = api.login("Note Ann");
		User ben = api.login("Note Ben");
		String slug = api.createLeague(owner, "Notify Cup", "PRIVATE");
		api.join(owner, slug, ann, ben);

		// Ben has the app open: his stream gets the live events.
		MockHttpServletResponse benStream = mvc.perform(get("/api/stream").cookie(ben.session()))
			.andExpect(request().asyncStarted())
			.andReturn().getResponse();

		api.send(post("/api/leagues/" + slug + "/seasons"), owner,
				"{\"participantIds\":[\"" + ann.id() + "\",\"" + ben.id() + "\"],\"format\":\"SINGLE\",\"pointsWin\":3,\"pointsDraw\":1}")
			.andExpect(status().isCreated());
		Map<String, Object> fixture = JsonPath.read(api.get("/api/public/leagues/" + slug + "/season", ann)
			.andReturn().getResponse().getContentAsString(), "$.season.fixtures[0]");
		String id = (String) fixture.get("id");
		api.send(post("/api/leagues/" + slug + "/fixtures/" + id + "/report"), ann, "{\"homeGoals\":1,\"awayGoals\":0}")
			.andExpect(status().isNoContent());

		await().atMost(Duration.ofSeconds(10)).untilAsserted(() -> assertThat(types(ben))
			.containsExactly("RESULT_REPORTED", "SEASON_STARTED"));
		assertThat(types(ann)).containsExactly("SEASON_STARTED");
		assertThat(types(owner)).isEmpty();
		assertThat(unread(ben)).isEqualTo(2);
		String text = JsonPath.read(api.get("/api/notifications", ben).andReturn().getResponse().getContentAsString(),
				"$.items[0].text");
		assertThat(text).isEqualTo("Note Ann reported " + (ann.id().equals(((Map<?, ?>) fixture.get("home")).get("userId"))
				? "Note Ann 1–0 Note Ben" : "Note Ben 1–0 Note Ann") + ". Confirm it or dispute it.");
		await().atMost(Duration.ofSeconds(10)).untilAsserted(() -> assertThat(benStream.getContentAsString())
			.contains("event:notification").contains("event:league").contains("\"slug\":\"" + slug + "\""));

		// Ben disputes: Ann (the reporter) and the owner (an admin) hear about it.
		api.send(post("/api/leagues/" + slug + "/fixtures/" + id + "/dispute"), ben, "{\"homeGoals\":0,\"awayGoals\":0}")
			.andExpect(status().isNoContent());
		await().atMost(Duration.ofSeconds(10)).untilAsserted(() -> assertThat(types(owner)).containsExactly("DISPUTE_OPENED"));
		assertThat(types(ann)).containsExactly("DISPUTE_OPENED", "SEASON_STARTED");

		// Reading: one by id, then all.
		String first = JsonPath.read(api.get("/api/notifications", ben).andReturn().getResponse().getContentAsString(), "$.items[0].id");
		api.send(post("/api/notifications/read"), ben, "{\"ids\":[\"" + first + "\"]}").andExpect(status().isNoContent());
		assertThat(unread(ben)).isEqualTo(1);
		api.send(post("/api/notifications/read"), ben, "{}").andExpect(status().isNoContent());
		assertThat(unread(ben)).isZero();
		api.get("/api/notifications", null).andExpect(status().isUnauthorized());
	}

	private List<String> types(User user) throws Exception {
		return JsonPath.read(api.get("/api/notifications", user).andReturn().getResponse().getContentAsString(), "$.items[*].type");
	}

	private int unread(User user) throws Exception {
		return JsonPath.read(api.get("/api/notifications/unread-count", user).andReturn().getResponse().getContentAsString(), "$.unread");
	}

}
