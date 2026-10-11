package com.leagueapp;

import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import jakarta.servlet.http.Cookie;

import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders;

import com.jayway.jsonpath.JsonPath;

/** Test helpers for API tests: sign in as someone, set up leagues, send JSON with CSRF. */
public final class ApiSupport {

	private final MockMvc mvc;

	public ApiSupport(MockMvc mvc) {
		this.mvc = mvc;
	}

	/** A signed-in user: their session cookie and user id. */
	public record User(Cookie session, String id) {
	}

	public User login(String displayName) throws Exception {
		Cookie session = mvc.perform(post("/api/dev/login").with(csrf())
				.contentType(MediaType.APPLICATION_JSON)
				.content("{\"displayName\":\"" + displayName + "\"}"))
			.andExpect(status().isNoContent())
			.andReturn().getResponse().getCookie("SESSION");
		String id = JsonPath.read(mvc.perform(MockMvcRequestBuilders.get("/api/me").cookie(session)).andReturn().getResponse().getContentAsString(),
				"$.id");
		return new User(session, id);
	}

	/** Creates a league as {@code owner} and returns its slug. */
	public String createLeague(User owner, String name, String visibility) throws Exception {
		String body = send(post("/api/leagues"), owner, "{\"name\":\"" + name + "\",\"visibility\":\"" + visibility + "\"}")
			.andExpect(status().isCreated())
			.andReturn().getResponse().getContentAsString();
		return JsonPath.read(body, "$.slug");
	}

	/** Joins each user to the league through its invite link. */
	public void join(User owner, String slug, User... joiners) throws Exception {
		String code = JsonPath.read(send(post("/api/leagues/" + slug + "/invite"), owner, null)
			.andExpect(status().isOk())
			.andReturn().getResponse().getContentAsString(), "$.code");
		for (User joiner : joiners) {
			send(post("/api/invites/" + code + "/accept"), joiner, null).andExpect(status().isOk());
		}
	}

	/** Sends a request as {@code user}, with a JSON body if given. */
	public ResultActions send(MockHttpServletRequestBuilder request, User user, String json) throws Exception {
		request.cookie(user.session()).with(csrf());
		if (json != null) {
			request.contentType(MediaType.APPLICATION_JSON).content(json);
		}
		return mvc.perform(request);
	}

	public ResultActions get(String path, User user) throws Exception {
		MockHttpServletRequestBuilder request = MockMvcRequestBuilders.get(path);
		if (user != null) {
			request.cookie(user.session());
		}
		return mvc.perform(request);
	}

}
