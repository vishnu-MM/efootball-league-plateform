package com.leagueapp.league;

import java.time.OffsetDateTime;
import java.util.UUID;

import jakarta.persistence.Column;
import jakarta.persistence.EmbeddedId;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;

/** A member's club name in one league. Shares the membership's key. */
@Entity
@Table(name = "club")
class Club {

	static final int NAME_MAX = 40;

	@EmbeddedId
	private LeagueMembership.Key key;

	@Column(nullable = false, length = NAME_MAX)
	private String name;

	@Column(name = "updated_at", nullable = false)
	private OffsetDateTime updatedAt;

	protected Club() {
	}

	Club(UUID leagueId, UUID userId, String name) {
		this.key = new LeagueMembership.Key(leagueId, userId);
		rename(name);
	}

	UUID getUserId() {
		return key.userId();
	}

	String getName() {
		return name;
	}

	void rename(String name) {
		this.name = name;
		this.updatedAt = OffsetDateTime.now();
	}

}
