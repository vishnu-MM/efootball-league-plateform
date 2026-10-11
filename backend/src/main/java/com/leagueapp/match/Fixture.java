package com.leagueapp.match;

import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.UUID;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

/** One match between two players. See {@link ResultRules} for who may move it between states. */
@Entity
@Table(name = "fixture")
class Fixture {

	@Id
	private UUID id;

	@Column(name = "season_id", nullable = false)
	private UUID seasonId;

	@Column(nullable = false)
	private short round;

	@Column(name = "home_user_id", nullable = false)
	private UUID homeUserId;

	@Column(name = "away_user_id", nullable = false)
	private UUID awayUserId;

	@Column(name = "play_by")
	private LocalDate playBy;

	@Enumerated(EnumType.STRING)
	@Column(nullable = false, length = 30)
	private Status status;

	@Column(name = "home_goals")
	private Short homeGoals;

	@Column(name = "away_goals")
	private Short awayGoals;

	@Column(name = "final_report_id")
	private UUID finalReportId;

	@Column(name = "resolved_by")
	private UUID resolvedBy;

	@Column(name = "finalized_at")
	private OffsetDateTime finalizedAt;

	protected Fixture() {
	}

	Fixture(UUID seasonId, int round, UUID homeUserId, UUID awayUserId, LocalDate playBy) {
		this.id = UUID.randomUUID();
		this.seasonId = seasonId;
		this.round = (short) round;
		this.homeUserId = homeUserId;
		this.awayUserId = awayUserId;
		this.playBy = playBy;
		this.status = Status.SCHEDULED;
	}

	void markReported() {
		this.status = Status.AWAITING_CONFIRMATION;
	}

	void markDisputed() {
		this.status = Status.DISPUTED;
	}

	/** Makes a report's score the official result. {@code resolvedBy} is set when an admin settled a dispute. */
	void finalizeWith(ScoreReport report, UUID resolvedBy) {
		this.status = Status.CONFIRMED;
		this.homeGoals = (short) report.getHomeGoals();
		this.awayGoals = (short) report.getAwayGoals();
		this.finalReportId = report.getId();
		this.resolvedBy = resolvedBy;
		this.finalizedAt = OffsetDateTime.now();
	}

	boolean involves(UUID userId) {
		return homeUserId.equals(userId) || awayUserId.equals(userId);
	}

	UUID getId() {
		return id;
	}

	UUID getSeasonId() {
		return seasonId;
	}

	int getRound() {
		return round;
	}

	UUID getHomeUserId() {
		return homeUserId;
	}

	UUID getAwayUserId() {
		return awayUserId;
	}

	LocalDate getPlayBy() {
		return playBy;
	}

	Status getStatus() {
		return status;
	}

	Integer getHomeGoals() {
		return homeGoals == null ? null : homeGoals.intValue();
	}

	Integer getAwayGoals() {
		return awayGoals == null ? null : awayGoals.intValue();
	}

	UUID getFinalReportId() {
		return finalReportId;
	}

	UUID getResolvedBy() {
		return resolvedBy;
	}

	enum Status {
		SCHEDULED, AWAITING_CONFIRMATION, DISPUTED, CONFIRMED
	}

}
