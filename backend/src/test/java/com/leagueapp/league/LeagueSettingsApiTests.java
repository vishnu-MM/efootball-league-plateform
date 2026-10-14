package com.leagueapp.league;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.Duration;
import java.util.List;

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
class LeagueSettingsApiTests {

	@Autowired
	MockMvc mvc;

	ApiSupport api;

	@BeforeEach
	void setUp() {
		api = new ApiSupport(mvc);
	}

	@Test
	void ownerRenamesAndChangesVisibility() throws Exception {
		User owner = api.login("Settings Owner");
		User admin = api.login("Settings Admin");
		String slug = api.createLeague(owner, "Settings Cup", "PRIVATE");
		api.join(owner, slug, admin);
		api.send(put("/api/leagues/" + slug + "/members/" + admin.id() + "/role"), owner, "{\"role\":\"ADMIN\"}")
			.andExpect(status().isNoContent());

		api.send(put("/api/leagues/" + slug), owner, "{\"name\":\"Renamed Cup\",\"visibility\":\"PUBLIC\"}")
			.andExpect(status().isOk());
		api.get("/api/public/leagues/" + slug, null)
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.name").value("Renamed Cup"))
			.andExpect(jsonPath("$.slug").value(slug))
			.andExpect(jsonPath("$.visibility").value("PUBLIC"));

		api.send(put("/api/leagues/" + slug), admin, "{\"name\":\"Admin Cup\",\"visibility\":\"PUBLIC\"}")
			.andExpect(status().isForbidden());
		api.send(put("/api/leagues/" + slug), owner, "{\"name\":\"  \",\"visibility\":\"PUBLIC\"}")
			.andExpect(status().isBadRequest());
	}

	@Test
	void ownerTransfersOwnership() throws Exception {
		User owner = api.login("Transfer Owner");
		User member = api.login("Transfer Member");
		User admin = api.login("Transfer Admin");
		User outsider = api.login("Transfer Outsider");
		String slug = api.createLeague(owner, "Transfer Cup", "PRIVATE");
		api.join(owner, slug, member, admin);
		api.send(put("/api/leagues/" + slug + "/members/" + admin.id() + "/role"), owner, "{\"role\":\"ADMIN\"}")
			.andExpect(status().isNoContent());

		api.send(post(transferPath(slug)), admin, "{\"userId\":\"" + member.id() + "\"}").andExpect(status().isForbidden());
		api.send(post(transferPath(slug)), owner, "{\"userId\":\"" + outsider.id() + "\"}").andExpect(status().isNotFound());

		api.send(post(transferPath(slug)), owner, "{\"userId\":\"" + member.id() + "\"}").andExpect(status().isNoContent());
		api.get("/api/public/leagues/" + slug, member).andExpect(jsonPath("$.myRole").value("OWNER"));
		api.get("/api/public/leagues/" + slug, owner).andExpect(jsonPath("$.myRole").value("ADMIN"));

		api.send(put("/api/leagues/" + slug), member, "{\"name\":\"New Owner Cup\",\"visibility\":\"PRIVATE\"}")
			.andExpect(status().isOk());
		api.send(post("/api/leagues/" + slug + "/leave"), owner, null).andExpect(status().isNoContent());
	}

	@Test
	void memberLeaves() throws Exception {
		User owner = api.login("Leave Owner");
		User member = api.login("Leave Member");
		User outsider = api.login("Leave Outsider");
		String slug = api.createLeague(owner, "Leave Cup", "PRIVATE");
		api.join(owner, slug, member);

		api.send(post("/api/leagues/" + slug + "/leave"), member, null).andExpect(status().isNoContent());
		api.get("/api/public/leagues/" + slug, member).andExpect(status().isNotFound());

		api.send(post("/api/leagues/" + slug + "/leave"), owner, null)
			.andExpect(status().isConflict())
			.andExpect(jsonPath("$.detail").value(org.hamcrest.Matchers.containsString("Transfer")));
		api.send(post("/api/leagues/" + slug + "/leave"), outsider, null).andExpect(status().isNotFound());
	}

	@Test
	void leavingIsBlockedInTheActiveSeason() throws Exception {
		User owner = api.login("Season Leave Owner");
		User member = api.login("Season Leave Member");
		String slug = api.createLeague(owner, "Season Leave Cup", "PRIVATE");
		api.join(owner, slug, member);
		api.send(post("/api/leagues/" + slug + "/seasons"), owner, "{\"participantIds\":[\"" + owner.id() + "\",\""
				+ member.id() + "\"],\"format\":\"SINGLE\",\"pointsWin\":3,\"pointsDraw\":1}")
			.andExpect(status().isCreated());

		api.send(post("/api/leagues/" + slug + "/leave"), member, null).andExpect(status().isConflict());
	}

	@Test
	void transferIsLoggedAndNotified() throws Exception {
		User owner = api.login("Logged Owner");
		User member = api.login("Logged Member");
		String slug = api.createLeague(owner, "Logged Cup", "PRIVATE");
		api.join(owner, slug, member);

		api.send(post(transferPath(slug)), owner, "{\"userId\":\"" + member.id() + "\"}").andExpect(status().isNoContent());

		api.get("/api/public/leagues/" + slug + "/log", member)
			.andExpect(jsonPath("$.entries[0].type").value("OWNERSHIP_TRANSFERRED"));
		await().atMost(Duration.ofSeconds(10)).untilAsserted(() -> assertThat(types(member)).contains("OWNERSHIP_TRANSFERRED"));
	}

	private List<String> types(User user) throws Exception {
		return JsonPath.read(api.get("/api/notifications", user).andReturn().getResponse().getContentAsString(), "$.items[*].type");
	}

	private static String transferPath(String slug) {
		return "/api/leagues/" + slug + "/transfer-ownership";
	}

}
