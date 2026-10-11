package com.leagueapp.league;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import jakarta.servlet.http.Cookie;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;

import com.jayway.jsonpath.JsonPath;
import com.leagueapp.TestcontainersConfiguration;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("dev")
@Import(TestcontainersConfiguration.class)
class InviteApiTests {

	@Autowired
	MockMvc mvc;

	@Test
	void managerJoinsPrivateLeagueThroughLink() throws Exception {
		Cookie owner = login("Invite Owner");
		String slug = createLeague(owner, "Invite Private Cup", "PRIVATE");

		mvc.perform(get("/api/leagues/" + slug + "/invite").cookie(owner))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.code").doesNotExist());
		String code = inviteCode(owner, slug);
		assertThat(inviteCode(owner, slug)).as("same link until rotated").isEqualTo(code);

		Cookie manager = login("Invite Joiner");
		mvc.perform(get("/api/public/invites/" + code).cookie(manager))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.name").value("Invite Private Cup"))
			.andExpect(jsonPath("$.memberCount").value(1))
			.andExpect(jsonPath("$.myRole").doesNotExist());

		accept(manager, code).andExpect(status().isOk())
			.andExpect(jsonPath("$.slug").value(slug))
			.andExpect(jsonPath("$.role").value("MANAGER"));
		accept(manager, code).andExpect(status().isOk()).andExpect(jsonPath("$.role").value("MANAGER"));

		mvc.perform(get("/api/public/leagues/" + slug).cookie(manager))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.myRole").value("MANAGER"))
			.andExpect(jsonPath("$.memberCount").value(2));
		mvc.perform(get("/api/leagues/" + slug + "/members").cookie(manager))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.length()").value(2))
			.andExpect(jsonPath("$[0].displayName").value("Invite Owner"))
			.andExpect(jsonPath("$[0].role").value("OWNER"))
			.andExpect(jsonPath("$[1].displayName").value("Invite Joiner"))
			.andExpect(jsonPath("$[1].role").value("MANAGER"));
	}

	@Test
	void newLinkRevokesTheOldOne() throws Exception {
		Cookie owner = login("Rotate Owner");
		String slug = createLeague(owner, "Rotate Cup", "PUBLIC");
		String oldCode = inviteCode(owner, slug);

		String newCode = JsonPath.read(mvc.perform(post("/api/leagues/" + slug + "/invite/rotate").cookie(owner).with(csrf()))
			.andExpect(status().isOk())
			.andReturn().getResponse().getContentAsString(), "$.code");

		assertThat(newCode).isNotEqualTo(oldCode).hasSize(InviteCodes.LENGTH);
		mvc.perform(get("/api/public/invites/" + oldCode)).andExpect(status().isNotFound());
		accept(login("Late Joiner"), oldCode).andExpect(status().isNotFound());
		accept(login("On Time Joiner"), newCode).andExpect(status().isOk());
	}

	@Test
	void onlyOwnerOrAdminCanManageLinks() throws Exception {
		Cookie owner = login("Gatekeeper");
		String publicSlug = createLeague(owner, "Gate Public Cup", "PUBLIC");
		String privateSlug = createLeague(owner, "Gate Private Cup", "PRIVATE");

		Cookie manager = login("Gate Manager");
		accept(manager, inviteCode(owner, publicSlug)).andExpect(status().isOk());
		mvc.perform(post("/api/leagues/" + publicSlug + "/invite").cookie(manager).with(csrf()))
			.andExpect(status().isForbidden());

		Cookie outsider = login("Gate Outsider");
		mvc.perform(post("/api/leagues/" + publicSlug + "/invite").cookie(outsider).with(csrf()))
			.andExpect(status().isForbidden());
		mvc.perform(post("/api/leagues/" + privateSlug + "/invite").cookie(outsider).with(csrf()))
			.andExpect(status().isNotFound());
	}

	@Test
	void ownerKeepsTheirRoleWhenOpeningTheirOwnLink() throws Exception {
		Cookie owner = login("Self Inviter");
		String slug = createLeague(owner, "Self Invite Cup", "PUBLIC");
		accept(owner, inviteCode(owner, slug)).andExpect(jsonPath("$.role").value("OWNER"));
	}

	@Test
	void joiningRequiresSignIn() throws Exception {
		Cookie owner = login("Anon Owner");
		String code = inviteCode(owner, createLeague(owner, "Anon Cup", "PUBLIC"));
		mvc.perform(post("/api/invites/" + code + "/accept").with(csrf())).andExpect(status().isUnauthorized());
	}

	@Test
	void membersAreVisibleToMembersOnly() throws Exception {
		Cookie owner = login("Roster Owner");
		String slug = createLeague(owner, "Roster Cup", "PUBLIC");
		mvc.perform(get("/api/leagues/" + slug + "/members").cookie(login("Roster Outsider")))
			.andExpect(status().isForbidden());
	}

	@Test
	void unknownCodeIsNotFound() throws Exception {
		mvc.perform(get("/api/public/invites/doesNotExist")).andExpect(status().isNotFound());
	}

	private Cookie login(String displayName) throws Exception {
		return mvc.perform(post("/api/dev/login").with(csrf())
				.contentType(MediaType.APPLICATION_JSON)
				.content("{\"displayName\":\"" + displayName + "\"}"))
			.andExpect(status().isNoContent())
			.andReturn()
			.getResponse()
			.getCookie("SESSION");
	}

	private String createLeague(Cookie session, String name, String visibility) throws Exception {
		String body = mvc.perform(post("/api/leagues").cookie(session).with(csrf())
				.contentType(MediaType.APPLICATION_JSON)
				.content("{\"name\":\"" + name + "\",\"visibility\":\"" + visibility + "\"}"))
			.andExpect(status().isCreated())
			.andReturn().getResponse().getContentAsString();
		return JsonPath.read(body, "$.slug");
	}

	private String inviteCode(Cookie session, String slug) throws Exception {
		String body = mvc.perform(post("/api/leagues/" + slug + "/invite").cookie(session).with(csrf()))
			.andExpect(status().isOk())
			.andReturn().getResponse().getContentAsString();
		return JsonPath.read(body, "$.code");
	}

	private ResultActions accept(Cookie session, String code) throws Exception {
		return mvc.perform(post("/api/invites/" + code + "/accept").cookie(session).with(csrf()));
	}

}
