package com.leagueapp.discord;

import java.time.OffsetDateTime;
import java.util.Arrays;
import java.util.EnumSet;
import java.util.Set;
import java.util.UUID;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

/** A league's Discord channel webhook. The URL is a secret; only {@link #maskedUrl()} leaves the server. */
@Entity
@Table(name = "league_webhook")
class LeagueWebhook {

	@Id
	@Column(name = "league_id")
	private UUID leagueId;

	@Column(nullable = false, length = 300)
	private String url;

	@Column(name = "event_types", nullable = false)
	private String[] eventTypes;

	@Column(nullable = false)
	private boolean enabled;

	@Column(name = "last_error", length = 300)
	private String lastError;

	@Column(name = "last_error_at")
	private OffsetDateTime lastErrorAt;

	@Column(name = "updated_by", nullable = false)
	private UUID updatedBy;

	@Column(name = "updated_at", nullable = false)
	private OffsetDateTime updatedAt;

	protected LeagueWebhook() {
	}

	LeagueWebhook(UUID leagueId, String url, Set<DiscordTopic> topics, UUID updatedBy) {
		this.leagueId = leagueId;
		update(url, topics, updatedBy);
	}

	/** New settings switch the webhook back on and clear any old error. */
	void update(String newUrl, Set<DiscordTopic> topics, UUID by) {
		this.url = newUrl;
		this.eventTypes = topics.stream().map(Enum::name).sorted().toArray(String[]::new);
		this.enabled = true;
		this.lastError = null;
		this.lastErrorAt = null;
		this.updatedBy = by;
		this.updatedAt = OffsetDateTime.now();
	}

	void failed(String error, boolean disable) {
		this.lastError = error.length() > 300 ? error.substring(0, 300) : error;
		this.lastErrorAt = OffsetDateTime.now();
		if (disable) {
			this.enabled = false;
		}
	}

	boolean posts(DiscordTopic topic) {
		return enabled && Arrays.asList(eventTypes).contains(topic.name());
	}

	Set<DiscordTopic> topics() {
		Set<DiscordTopic> topics = EnumSet.noneOf(DiscordTopic.class);
		for (String t : eventTypes) {
			topics.add(DiscordTopic.valueOf(t));
		}
		return topics;
	}

	/** "https://discord.com/api/webhooks/1234…/****": enough to recognise it, not enough to use it. */
	String maskedUrl() {
		String[] parts = url.split("/");
		String id = parts.length >= 2 ? parts[parts.length - 2] : "";
		return "https://discord.com/api/webhooks/" + (id.length() > 4 ? id.substring(0, 4) + "…" : id) + "/****";
	}

	UUID getLeagueId() {
		return leagueId;
	}

	String getUrl() {
		return url;
	}

	boolean isEnabled() {
		return enabled;
	}

	String getLastError() {
		return lastError;
	}

	OffsetDateTime getLastErrorAt() {
		return lastErrorAt;
	}

}
