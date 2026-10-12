package com.leagueapp.match;

import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import jakarta.persistence.CollectionTable;
import jakarta.persistence.Column;
import jakarta.persistence.ElementCollection;
import jakarta.persistence.Embeddable;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.OrderColumn;
import jakarta.persistence.Table;

/** A score someone entered for a fixture: the first report, a dispute's counter-score, or an admin's resolution. */
@Entity
@Table(name = "score_report")
class ScoreReport {

	static final int NOTE_MAX = 280;

	static final int NAME_MAX = 60;

	@Id
	private UUID id;

	@Column(name = "fixture_id", nullable = false)
	private UUID fixtureId;

	@Enumerated(EnumType.STRING)
	@Column(nullable = false, length = 20)
	private Kind kind;

	@Column(name = "reported_by", nullable = false)
	private UUID reportedBy;

	@Column(name = "home_goals", nullable = false)
	private short homeGoals;

	@Column(name = "away_goals", nullable = false)
	private short awayGoals;

	@Column(length = NOTE_MAX)
	private String note;

	@Column(name = "created_at", nullable = false, insertable = false, updatable = false)
	private OffsetDateTime createdAt;

	@ElementCollection(fetch = FetchType.EAGER)
	@CollectionTable(name = "report_goal", joinColumns = @JoinColumn(name = "report_id"))
	@OrderColumn(name = "position")
	private List<Goal> goals = new ArrayList<>();

	protected ScoreReport() {
	}

	ScoreReport(UUID fixtureId, Kind kind, UUID reportedBy, int homeGoals, int awayGoals, String note,
			List<Goal> goals) {
		this.id = UUID.randomUUID();
		this.fixtureId = fixtureId;
		this.kind = kind;
		this.reportedBy = reportedBy;
		this.homeGoals = (short) homeGoals;
		this.awayGoals = (short) awayGoals;
		this.note = note;
		this.goals = new ArrayList<>(goals);
	}

	UUID getId() {
		return id;
	}

	UUID getFixtureId() {
		return fixtureId;
	}

	Kind getKind() {
		return kind;
	}

	UUID getReportedBy() {
		return reportedBy;
	}

	int getHomeGoals() {
		return homeGoals;
	}

	int getAwayGoals() {
		return awayGoals;
	}

	String getNote() {
		return note;
	}

	OffsetDateTime getCreatedAt() {
		return createdAt;
	}

	List<Goal> getGoals() {
		return goals;
	}

	enum Kind {
		REPORT, DISPUTE, RESOLUTION
	}

	enum Side {
		HOME, AWAY
	}

	/**
	 * A scorer (and optional assist) for one side. With squads, the card ids point at the squad cards and the names
	 * are a snapshot of their player names; without, the names were typed and the ids are null.
	 */
	@Embeddable
	record Goal(@Enumerated(EnumType.STRING) @Column(nullable = false, length = 4) Side side,
			@Column(nullable = false, length = NAME_MAX) String scorer, @Column(length = NAME_MAX) String assist,
			@Column(name = "scorer_card_id") UUID scorerCardId, @Column(name = "assist_card_id") UUID assistCardId) {
	}

}
