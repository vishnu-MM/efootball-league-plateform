package com.leagueapp.activity;

import java.time.OffsetDateTime;
import java.util.UUID;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

/** One entry in a league's log. The summary is written when it happens, with names as they were then. */
@Entity
@Table(name = "league_activity")
class LeagueActivity {

	static final int SUMMARY_MAX = 300;

	@Id
	private UUID id;

	@Column(name = "league_id", nullable = false)
	private UUID leagueId;

	@Column(name = "created_at", nullable = false, insertable = false, updatable = false)
	private OffsetDateTime createdAt;

	@Column(name = "actor_id")
	private UUID actorId;

	@Enumerated(EnumType.STRING)
	@Column(nullable = false, length = 40)
	private Type type;

	@Column(name = "admin_action", nullable = false)
	private boolean adminAction;

	@Column(nullable = false, length = SUMMARY_MAX)
	private String summary;

	@Column(length = 280)
	private String reason;

	@Column(name = "fixture_id")
	private UUID fixtureId;

	@Column(name = "subject_user_id")
	private UUID subjectUserId;

	protected LeagueActivity() {
	}

	LeagueActivity(UUID leagueId, UUID actorId, Type type, String summary, String reason, UUID fixtureId,
			UUID subjectUserId) {
		this.id = UUID.randomUUID();
		this.leagueId = leagueId;
		this.actorId = actorId;
		this.type = type;
		this.adminAction = type.adminAction;
		this.summary = summary.length() > SUMMARY_MAX ? summary.substring(0, SUMMARY_MAX) : summary;
		this.reason = reason;
		this.fixtureId = fixtureId;
		this.subjectUserId = subjectUserId;
	}

	UUID getId() {
		return id;
	}

	OffsetDateTime getCreatedAt() {
		return createdAt;
	}

	UUID getActorId() {
		return actorId;
	}

	Type getType() {
		return type;
	}

	boolean isAdminAction() {
		return adminAction;
	}

	String getSummary() {
		return summary;
	}

	String getReason() {
		return reason;
	}

	UUID getFixtureId() {
		return fixtureId;
	}

	UUID getSubjectUserId() {
		return subjectUserId;
	}

	enum Type {

		DISPUTE_RESOLVED(true), RESULT_OVERRIDDEN(true), FORFEIT(true), POINTS_DEDUCTED(true),
		DEDUCTION_REVOKED(true), CARD_APPROVED(true), CARD_REJECTED(true), SQUAD_RULES_CHANGED(true),
		WINDOW_OPENED(true), WINDOW_CLOSED(true), SEASON_STARTED(true), SEASON_COMPLETED(false);

		/** Done by an owner or admin, as opposed to something that happened on its own. */
		final boolean adminAction;

		Type(boolean adminAction) {
			this.adminAction = adminAction;
		}

	}

}
