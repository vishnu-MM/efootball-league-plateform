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

@Entity
@Table(name = "season")
class Season {

	static final int NAME_MAX = 60;

	@Id
	private UUID id;

	@Column(name = "league_id", nullable = false)
	private UUID leagueId;

	@Column(nullable = false)
	private int number;

	@Column(nullable = false, length = NAME_MAX)
	private String name;

	@Enumerated(EnumType.STRING)
	@Column(nullable = false, length = 20)
	private Format format;

	@Column(name = "points_win", nullable = false)
	private short pointsWin;

	@Column(name = "points_draw", nullable = false)
	private short pointsDraw;

	@Column(name = "start_date")
	private LocalDate startDate;

	@Column(name = "days_per_round")
	private Short daysPerRound;

	@Enumerated(EnumType.STRING)
	@Column(nullable = false, length = 20)
	private Status status;

	@Column(name = "created_by", nullable = false)
	private UUID createdBy;

	@Column(name = "created_at", nullable = false, insertable = false, updatable = false)
	private OffsetDateTime createdAt;

	@Column(name = "completed_at")
	private OffsetDateTime completedAt;

	protected Season() {
	}

	Season(UUID leagueId, int number, String name, Format format, int pointsWin, int pointsDraw, LocalDate startDate,
			Integer daysPerRound, UUID createdBy) {
		this.id = UUID.randomUUID();
		this.leagueId = leagueId;
		this.number = number;
		this.name = name;
		this.format = format;
		this.pointsWin = (short) pointsWin;
		this.pointsDraw = (short) pointsDraw;
		this.startDate = startDate;
		this.daysPerRound = daysPerRound == null ? null : daysPerRound.shortValue();
		this.status = Status.ACTIVE;
		this.createdBy = createdBy;
	}

	/** The last day to play a round, or null when the season has no dates. */
	LocalDate playBy(int round) {
		return startDate == null ? null : startDate.plusDays((long) daysPerRound * round - 1);
	}

	void complete() {
		this.status = Status.COMPLETED;
		this.completedAt = OffsetDateTime.now();
	}

	UUID getId() {
		return id;
	}

	UUID getLeagueId() {
		return leagueId;
	}

	int getNumber() {
		return number;
	}

	OffsetDateTime getCreatedAt() {
		return createdAt;
	}

	OffsetDateTime getCompletedAt() {
		return completedAt;
	}

	String getName() {
		return name;
	}

	Format getFormat() {
		return format;
	}

	int getPointsWin() {
		return pointsWin;
	}

	int getPointsDraw() {
		return pointsDraw;
	}

	LocalDate getStartDate() {
		return startDate;
	}

	Integer getDaysPerRound() {
		return daysPerRound == null ? null : daysPerRound.intValue();
	}

	Status getStatus() {
		return status;
	}

	boolean isActive() {
		return status == Status.ACTIVE;
	}

	enum Format {
		SINGLE, HOME_AND_AWAY
	}

	enum Status {
		ACTIVE, COMPLETED
	}

}
