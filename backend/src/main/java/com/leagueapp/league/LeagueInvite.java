package com.leagueapp.league;

import java.time.OffsetDateTime;
import java.util.UUID;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

@Entity
@Table(name = "league_invite")
class LeagueInvite {

	@Id
	@Column(length = 16)
	private String code;

	@Column(name = "league_id", nullable = false)
	private UUID leagueId;

	@Column(name = "created_by", nullable = false)
	private UUID createdBy;

	@Column(name = "created_at", nullable = false, insertable = false, updatable = false)
	private OffsetDateTime createdAt;

	@Column(name = "revoked_at")
	private OffsetDateTime revokedAt;

	protected LeagueInvite() {
	}

	LeagueInvite(String code, UUID leagueId, UUID createdBy) {
		this.code = code;
		this.leagueId = leagueId;
		this.createdBy = createdBy;
	}

	String getCode() {
		return code;
	}

	UUID getLeagueId() {
		return leagueId;
	}

	boolean isActive() {
		return revokedAt == null;
	}

	void revoke() {
		this.revokedAt = OffsetDateTime.now();
	}

}
