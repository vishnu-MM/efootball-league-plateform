package com.leagueapp.match;

import java.time.OffsetDateTime;
import java.util.UUID;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

/** Points an owner or admin took from a participant in a season. Revoking keeps the row. */
@Entity
@Table(name = "point_adjustment")
class PointAdjustment {

	static final int MAX_POINTS = 99;

	@Id
	private UUID id;

	@Column(name = "season_id", nullable = false)
	private UUID seasonId;

	@Column(name = "user_id", nullable = false)
	private UUID userId;

	@Column(nullable = false)
	private short points;

	@Column(nullable = false, length = ScoreReport.NOTE_MAX)
	private String reason;

	@Column(name = "created_by", nullable = false)
	private UUID createdBy;

	@Column(name = "created_at", nullable = false, insertable = false, updatable = false)
	private OffsetDateTime createdAt;

	@Column(name = "revoked_by")
	private UUID revokedBy;

	@Column(name = "revoked_at")
	private OffsetDateTime revokedAt;

	@Column(name = "revoke_reason", length = ScoreReport.NOTE_MAX)
	private String revokeReason;

	protected PointAdjustment() {
	}

	PointAdjustment(UUID seasonId, UUID userId, int points, String reason, UUID createdBy) {
		this.id = UUID.randomUUID();
		this.seasonId = seasonId;
		this.userId = userId;
		this.points = (short) points;
		this.reason = reason;
		this.createdBy = createdBy;
	}

	void revoke(UUID adminId, String why) {
		this.revokedBy = adminId;
		this.revokedAt = OffsetDateTime.now();
		this.revokeReason = why;
	}

	boolean isRevoked() {
		return revokedAt != null;
	}

	UUID getId() {
		return id;
	}

	UUID getSeasonId() {
		return seasonId;
	}

	UUID getUserId() {
		return userId;
	}

	int getPoints() {
		return points;
	}

	String getReason() {
		return reason;
	}

	OffsetDateTime getCreatedAt() {
		return createdAt;
	}

}
