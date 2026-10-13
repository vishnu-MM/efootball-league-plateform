package com.leagueapp.discord;

import java.time.OffsetDateTime;
import java.util.EnumSet;
import java.util.Set;
import java.util.UUID;

import org.springframework.http.HttpStatus;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

import com.leagueapp.identity.CurrentUser;
import com.leagueapp.league.LeagueRole;
import com.leagueapp.league.LeagueService;

/** The league's Discord webhook: owner only. The saved URL is never sent back in full. */
@RestController
class DiscordController {

	private final LeagueService leagueService;
	private final WebhookRepository webhooks;
	private final DiscordPoster poster;
	private final CurrentUser currentUser;

	DiscordController(LeagueService leagueService, WebhookRepository webhooks, DiscordPoster poster,
			CurrentUser currentUser) {
		this.leagueService = leagueService;
		this.webhooks = webhooks;
		this.poster = poster;
		this.currentUser = currentUser;
	}

	@GetMapping("/api/leagues/{slug}/discord")
	@Transactional(readOnly = true)
	Settings get(@PathVariable String slug) {
		return webhooks.findById(requireOwner(slug)).map(Settings::of).orElse(Settings.NONE);
	}

	/** Saves the settings. Leave {@code url} empty to keep the current one; saving switches the webhook back on. */
	@PutMapping("/api/leagues/{slug}/discord")
	@Transactional
	Settings save(@PathVariable String slug, @RequestBody SaveRequest body) {
		UUID leagueId = requireOwner(slug);
		UUID me = currentUser.requireId();
		Set<DiscordTopic> topics = body.topics() == null || body.topics().isEmpty() ? EnumSet.noneOf(DiscordTopic.class)
				: EnumSet.copyOf(body.topics());
		if (topics.isEmpty()) {
			throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Pick at least one kind of update to post");
		}
		LeagueWebhook existing = webhooks.findById(leagueId).orElse(null);
		String url = body.url() == null || body.url().isBlank() ? null : body.url().strip();
		if (url == null && existing == null) {
			throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Paste the channel's webhook URL");
		}
		if (url != null && DiscordClient.pathOf(url).isEmpty()) {
			throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
					"That isn't a Discord webhook URL. In Discord: channel settings → Integrations → Webhooks → Copy Webhook URL.");
		}
		LeagueWebhook saved;
		if (existing == null) {
			saved = webhooks.save(new LeagueWebhook(leagueId, url, topics, me));
		}
		else {
			existing.update(url == null ? existing.getUrl() : url, topics, me);
			saved = existing;
		}
		return Settings.of(saved);
	}

	@DeleteMapping("/api/leagues/{slug}/discord")
	@ResponseStatus(HttpStatus.NO_CONTENT)
	@Transactional
	void remove(@PathVariable String slug) {
		webhooks.deleteById(requireOwner(slug));
	}

	/** Posts a test message right away; 502 with Discord's answer if it didn't work. */
	@PostMapping("/api/leagues/{slug}/discord/test")
	@ResponseStatus(HttpStatus.NO_CONTENT)
	void test(@PathVariable String slug) {
		LeagueWebhook webhook = webhooks.findById(requireOwner(slug))
			.orElseThrow(() -> new ResponseStatusException(HttpStatus.CONFLICT, "Save a webhook first"));
		DiscordClient.Result result = poster.sendTest(webhook);
		if (!result.ok()) {
			throw new ResponseStatusException(HttpStatus.BAD_GATEWAY, result.error());
		}
	}

	private UUID requireOwner(String slug) {
		LeagueService.LeagueAccess league = leagueService.access(slug, currentUser.requireId());
		if (league.viewerRole() != LeagueRole.OWNER) {
			throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Only the owner can set up Discord");
		}
		return league.leagueId();
	}

	record SaveRequest(String url, Set<DiscordTopic> topics) {
	}

	record Settings(boolean configured, String maskedUrl, Set<DiscordTopic> topics, boolean enabled, String lastError,
			OffsetDateTime lastErrorAt) {

		static final Settings NONE = new Settings(false, null, EnumSet.allOf(DiscordTopic.class), false, null, null);

		static Settings of(LeagueWebhook w) {
			return new Settings(true, w.maskedUrl(), w.topics(), w.isEnabled(), w.getLastError(), w.getLastErrorAt());
		}

	}

}
