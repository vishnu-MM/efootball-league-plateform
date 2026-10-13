package com.leagueapp.discord;

import java.net.http.HttpClient;
import java.time.Duration;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.MediaType;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestClientResponseException;

/**
 * Posts messages to Discord webhooks. Only the webhook's path is taken from the saved URL; the host is always the
 * configured base URL, so a saved URL can't make the server call anywhere else.
 */
@Component
class DiscordClient {

	private static final Pattern WEBHOOK_URL = Pattern
		.compile("^https://(?:(?:ptb|canary)\\.)?discord(?:app)?\\.com(/api/webhooks/\\d{5,25}/[A-Za-z0-9_-]{20,100})/?$");

	static final Duration TIMEOUT = Duration.ofSeconds(5);

	private final RestClient rest;

	DiscordClient(@Value("${app.discord.base-url:https://discord.com}") String baseUrl) {
		JdkClientHttpRequestFactory factory = new JdkClientHttpRequestFactory(
				HttpClient.newBuilder().connectTimeout(TIMEOUT).followRedirects(HttpClient.Redirect.NEVER).build());
		factory.setReadTimeout(TIMEOUT);
		this.rest = RestClient.builder().baseUrl(baseUrl).requestFactory(factory).build();
	}

	/** The webhook path ("/api/webhooks/{id}/{token}") if this is a Discord webhook URL. */
	static Optional<String> pathOf(String url) {
		if (url == null) {
			return Optional.empty();
		}
		Matcher m = WEBHOOK_URL.matcher(url.strip());
		return m.matches() ? Optional.of(m.group(1)) : Optional.empty();
	}

	Result post(String webhookUrl, DiscordMessages.Message message) {
		String path = pathOf(webhookUrl).orElse(null);
		if (path == null) {
			return new Result(false, "The saved URL isn't a Discord webhook", true);
		}
		Map<String, Object> embed = new LinkedHashMap<>();
		embed.put("title", message.title());
		if (message.description() != null) {
			embed.put("description", message.description());
		}
		if (message.url() != null) {
			embed.put("url", message.url());
		}
		embed.put("color", message.color());
		embed.put("footer", Map.of("text", message.footer()));
		Map<String, Object> body = Map.of("username", "League Platform", "embeds", List.of(embed),
				// Never ping anyone, whatever a reason or name contains (e.g. "@everyone").
				"allowed_mentions", Map.of("parse", List.of()));
		try {
			rest.post().uri(path).contentType(MediaType.APPLICATION_JSON).body(body).retrieve().toBodilessEntity();
			return new Result(true, null, false);
		}
		catch (RestClientResponseException e) {
			HttpStatusCode status = e.getStatusCode();
			boolean gone = status.value() == 404 || status.value() == 401;
			return new Result(false, gone ? "Discord says this webhook no longer exists. Paste a new one."
					: "Discord answered " + status.value(), gone);
		}
		catch (RestClientException e) {
			return new Result(false, "Couldn't reach Discord", false);
		}
	}

	/** {@code gone}: the webhook was deleted on Discord's side, so it should be switched off. */
	record Result(boolean ok, String error, boolean gone) {
	}

}
