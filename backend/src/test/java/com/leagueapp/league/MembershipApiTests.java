package com.leagueapp.league;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
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

import com.leagueapp.ApiSupport;
import com.leagueapp.ApiSupport.User;
import com.leagueapp.TestcontainersConfiguration;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("dev")
@Import(TestcontainersConfiguration.class)
class MembershipApiTests {

	@Autowired
	MockMvc mvc;

	ApiSupport api;

	@BeforeEach
	void setUp() {
		api = new ApiSupport(mvc);
	}

	@Test
	void ownerPromotesAndDemotesMembers() throws Exception {
		User owner = api.login("Role Owner");
		User member = api.login("Role Member");
		String slug = api.createLeague(owner, "Role Cup", "PRIVATE");
		api.join(owner, slug, member);

		api.send(put(rolePath(slug, member)), owner, "{\"role\":\"ADMIN\"}").andExpect(status().isNoContent());
		api.get("/api/public/leagues/" + slug, member).andExpect(jsonPath("$.myRole").value("ADMIN"));

		// An admin can now manage invites, but still can't change roles.
		api.send(put(rolePath(slug, owner)), member, "{\"role\":\"MANAGER\"}").andExpect(status().isForbidden());

		api.send(put(rolePath(slug, member)), owner, "{\"role\":\"MANAGER\"}").andExpect(status().isNoContent());
		api.get("/api/public/leagues/" + slug, member).andExpect(jsonPath("$.myRole").value("MANAGER"));
	}

	@Test
	void ownerRoleIsFixed() throws Exception {
		User owner = api.login("Fixed Owner");
		String slug = api.createLeague(owner, "Fixed Cup", "PUBLIC");
		User member = api.login("Fixed Member");
		api.join(owner, slug, member);

		api.send(put(rolePath(slug, owner)), owner, "{\"role\":\"MANAGER\"}").andExpect(status().isBadRequest());
		api.send(put(rolePath(slug, member)), owner, "{\"role\":\"OWNER\"}").andExpect(status().isBadRequest());
		api.send(delete("/api/leagues/" + slug + "/members/" + owner.id()), owner, null).andExpect(status().isBadRequest());
	}

	@Test
	void ownerRemovesAMember() throws Exception {
		User owner = api.login("Remove Owner");
		User member = api.login("Remove Member");
		String slug = api.createLeague(owner, "Remove Cup", "PRIVATE");
		api.join(owner, slug, member);

		api.send(delete("/api/leagues/" + slug + "/members/" + member.id()), member, null).andExpect(status().isForbidden());
		api.send(delete("/api/leagues/" + slug + "/members/" + member.id()), owner, null).andExpect(status().isNoContent());
		api.get("/api/public/leagues/" + slug, member).andExpect(status().isNotFound());
	}

	@Test
	void membersNameTheirClubs() throws Exception {
		User owner = api.login("Club Owner");
		User member = api.login("Club Member");
		String slug = api.createLeague(owner, "Club Cup", "PUBLIC");
		api.join(owner, slug, member);

		api.send(put("/api/leagues/" + slug + "/club"), member, "{\"name\":\"  Real   Madrid \"}")
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.name").value("Real Madrid"));
		api.send(put("/api/leagues/" + slug + "/club"), owner, "{\"name\":\"real madrid\"}").andExpect(status().isConflict());
		api.send(put("/api/leagues/" + slug + "/club"), member, "{\"name\":\"REAL MADRID\"}").andExpect(status().isOk());
		api.send(put("/api/leagues/" + slug + "/club"), member, "{\"name\":\" x \"}").andExpect(status().isBadRequest());

		api.get("/api/leagues/" + slug + "/members", owner)
			.andExpect(jsonPath("$[1].clubName").value("REAL MADRID"))
			.andExpect(jsonPath("$[0].clubName").doesNotExist());

		User outsider = api.login("Club Outsider");
		api.send(put("/api/leagues/" + slug + "/club"), outsider, "{\"name\":\"Gate Crashers\"}").andExpect(status().isForbidden());
	}

	private static String rolePath(String slug, User user) {
		return "/api/leagues/" + slug + "/members/" + user.id() + "/role";
	}

}
