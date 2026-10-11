package com.leagueapp.league;

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

import com.leagueapp.TestcontainersConfiguration;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("dev")
@Import(TestcontainersConfiguration.class)
class LeagueApiTests {

	@Autowired
	MockMvc mvc;

	@Test
	void meRequiresSignIn() throws Exception {
		mvc.perform(get("/api/me")).andExpect(status().isUnauthorized());
	}

	@Test
	void devLoginThenMe() throws Exception {
		Cookie session = login("Alice Tester");
		mvc.perform(get("/api/me").cookie(session))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.displayName").value("Alice Tester"));
	}

	@Test
	void createLeagueMakesCreatorOwner() throws Exception {
		Cookie session = login("Owner One");
		String name = "Owner One Cup";

		createLeague(session, name, "PUBLIC")
			.andExpect(status().isCreated())
			.andExpect(jsonPath("$.slug").value("owner-one-cup"));
		createLeague(session, name, "PUBLIC")
			.andExpect(status().isCreated())
			.andExpect(jsonPath("$.slug").value("owner-one-cup-2"));

		mvc.perform(get("/api/leagues/mine").cookie(session))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$[?(@.slug == 'owner-one-cup')].role").value("OWNER"));
		mvc.perform(get("/api/public/leagues/owner-one-cup").cookie(session))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.myRole").value("OWNER"))
			.andExpect(jsonPath("$.memberCount").value(1));
	}

	@Test
	void privateLeagueIsHiddenFromNonMembers() throws Exception {
		createLeague(login("Private Owner"), "Secret League", "PRIVATE").andExpect(status().isCreated());

		mvc.perform(get("/api/public/leagues/secret-league").cookie(login("Outsider")))
			.andExpect(status().isNotFound());
		mvc.perform(get("/api/public/leagues/secret-league")).andExpect(status().isNotFound());
	}

	@Test
	void publicLeagueIsVisibleToAnyone() throws Exception {
		createLeague(login("Public Owner"), "Open League", "PUBLIC").andExpect(status().isCreated());

		mvc.perform(get("/api/public/leagues/open-league"))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.myRole").doesNotExist());
	}

	@Test
	void createLeagueValidatesInput() throws Exception {
		createLeague(login("Validator"), "ab", "PUBLIC").andExpect(status().isBadRequest());
	}

	@Test
	void createLeagueRequiresSignIn() throws Exception {
		mvc.perform(post("/api/leagues").with(csrf())
				.contentType(MediaType.APPLICATION_JSON)
				.content("{\"name\":\"Anonymous League\",\"visibility\":\"PUBLIC\"}"))
			.andExpect(status().isUnauthorized());
	}

	@Test
	void createLeagueRequiresCsrfToken() throws Exception {
		mvc.perform(post("/api/leagues").cookie(login("No Csrf"))
				.contentType(MediaType.APPLICATION_JSON)
				.content("{\"name\":\"No Csrf League\",\"visibility\":\"PUBLIC\"}"))
			.andExpect(status().isForbidden());
	}

	/** Signs in through the dev endpoint and returns the Spring Session cookie. */
	private Cookie login(String displayName) throws Exception {
		return mvc.perform(post("/api/dev/login").with(csrf())
				.contentType(MediaType.APPLICATION_JSON)
				.content("{\"displayName\":\"" + displayName + "\"}"))
			.andExpect(status().isNoContent())
			.andReturn()
			.getResponse()
			.getCookie("SESSION");
	}

	private org.springframework.test.web.servlet.ResultActions createLeague(Cookie session, String name,
			String visibility) throws Exception {
		return mvc.perform(post("/api/leagues").cookie(session).with(csrf())
			.contentType(MediaType.APPLICATION_JSON)
			.content("{\"name\":\"" + name + "\",\"visibility\":\"" + visibility + "\"}"));
	}

}
