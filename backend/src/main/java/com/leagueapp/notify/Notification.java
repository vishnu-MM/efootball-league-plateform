package com.leagueapp.notify;

import java.time.OffsetDateTime;
import java.util.UUID;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

/** Something in a league that concerns one user, with a link to where to look. */
@Entity
@Table(name = "notification")
class Notification {

	static final int TEXT_MAX = 400;

	@Id
	private UUID id;

	@Column(name = "user_id", nullable = false)
	private UUID userId;

	@Column(name = "league_id", nullable = false)
	private UUID leagueId;

	@Column(nullable = false, length = 40)
	private String type;

	@Column(nullable = false, length = TEXT_MAX)
	private String text;

	@Column(nullable = false, length = 200)
	private String link;

	@Column(name = "created_at", nullable = false, insertable = false, updatable = false)
	private OffsetDateTime createdAt;

	@Column(name = "read_at")
	private OffsetDateTime readAt;

	protected Notification() {
	}

	Notification(UUID userId, UUID leagueId, String type, String text, String link) {
		this.id = UUID.randomUUID();
		this.userId = userId;
		this.leagueId = leagueId;
		this.type = type;
		this.text = text.length() > TEXT_MAX ? text.substring(0, TEXT_MAX - 1) + "…" : text;
		this.link = link;
	}

	UUID getId() {
		return id;
	}

	UUID getUserId() {
		return userId;
	}

	UUID getLeagueId() {
		return leagueId;
	}

	String getType() {
		return type;
	}

	String getText() {
		return text;
	}

	String getLink() {
		return link;
	}

	OffsetDateTime getCreatedAt() {
		return createdAt;
	}

	OffsetDateTime getReadAt() {
		return readAt;
	}

}
