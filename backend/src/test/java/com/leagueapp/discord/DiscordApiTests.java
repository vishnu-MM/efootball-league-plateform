package com.leagueapp.discord;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.atomic.AtomicInteger;

import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;

import com.leagueapp.ApiSupport;
import com.leagueapp.ApiSupport.User;
import com.leagueapp.TestcontainersConfiguration;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("dev")
@Import(TestcontainersConfiguration.class)
class DiscordApiTests {

	/** Stands in for discord.com: records what was posted, answers with {@link #answer}. */
	static final HttpServer DISCORD;
	static final List<String> POSTED = new CopyOnWriteArrayList<>();
	static final AtomicInteger answer = new AtomicInteger(204);

	static {
		try {
			DISCORD = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
		}
		catch (IOException e) {
			throw new IllegalStateException(e);
		}
		DISCORD.createContext("/api/webhooks/", exchange -> {
			POSTED.add(exchange.getRequestURI().getPath() + " " + new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8));
			exchange.sendResponseHeaders(answer.get(), -1);
			exchange.close();
		});
		DISCORD.start();
	}

	static final String WEBHOOK = "https://discord.com/api/webhooks/123456789012/abcdefghijklmnopqrstuvwxyz_-0123";

	@DynamicPropertySource
	static void discordBaseUrl(DynamicPropertyRegistry registry) {
		registry.add("app.discord.base-url", () -> "http://127.0.0.1:" + DISCORD.getAddress().getPort());
	}

	@AfterAll
	static void stop() {
		DISCORD.stop(0);
	}

	@Autowired
	MockMvc mvc;

	ApiSupport api;

	@BeforeEach
	void setUp() {
		api = new ApiSupport(mvc);
		POSTED.clear();
		answer.set(204);
	}

	@Test
	void theOwnerConnectsAChannelAndPickedEventsArePosted() throws Exception {
		User owner = api.login("Discord Owner");
		User ann = api.login("Discord Ann");
		String slug = api.createLeague(owner, "Discord Cup", "PUBLIC");
		api.join(owner, slug, ann);
		String path = "/api/leagues/" + slug + "/discord";

		api.get(path, ann).andExpect(status().isForbidden());
		api.get(path, owner).andExpect(jsonPath("$.configured").value(false));
		api.send(put(path), owner, "{\"url\":\"https://example.com/api/webhooks/1/x\",\"topics\":[\"SEASONS\"]}")
			.andExpect(status().isBadRequest());
		api.send(put(path), owner, "{\"url\":\"" + WEBHOOK + "\",\"topics\":[]}").andExpect(status().isBadRequest());
		api.send(put(path), owner, "{\"url\":\"" + WEBHOOK + "\",\"topics\":[\"SEASONS\",\"RESULTS\"]}")
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.maskedUrl").value("https://discord.com/api/webhooks/1234…/****"))
			.andExpect(jsonPath("$.enabled").value(true));
		String settings = api.get(path, owner).andReturn().getResponse().getContentAsString();
		assertThat(settings).doesNotContain("abcdefghijklmnop");

		api.send(post(path + "/test"), owner, null).andExpect(status().isNoContent());
		assertThat(POSTED).singleElement().satisfies(p -> assertThat(p)
			.startsWith("/api/webhooks/123456789012/abcdefghijklmnopqrstuvwxyz_-0123 ")
			.contains("Discord is connected")
			.contains("\"allowed_mentions\":{\"parse\":[]}"));

		// A ticked kind of event is posted after the change commits.
		api.send(post("/api/leagues/" + slug + "/seasons"), owner,
				"{\"participantIds\":[\"" + owner.id() + "\",\"" + ann.id() + "\"],\"format\":\"SINGLE\",\"pointsWin\":3,\"pointsDraw\":1}")
			.andExpect(status().isCreated());
		await().atMost(Duration.ofSeconds(10)).until(() -> POSTED.stream().anyMatch(p -> p.contains("Season 1 has started")));
		assertThat(POSTED).noneMatch(p -> p.contains("Transfer window")); // not ticked

		// The webhook was deleted in Discord: the test fails, and the webhook switches off with the reason.
		answer.set(404);
		api.send(post(path + "/test"), owner, null).andExpect(status().isBadGateway());
		api.get(path, owner)
			.andExpect(jsonPath("$.enabled").value(false))
			.andExpect(jsonPath("$.lastError").value("Discord says this webhook no longer exists. Paste a new one."));

		// Saving again (keeping the URL) switches it back on.
		api.send(put(path), owner, "{\"topics\":[\"SEASONS\"]}").andExpect(jsonPath("$.enabled").value(true))
			.andExpect(jsonPath("$.lastError").isEmpty());
	}

}
