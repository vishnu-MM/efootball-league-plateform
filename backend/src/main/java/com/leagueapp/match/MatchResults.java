package com.leagueapp.match;

import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** The match module's read API for other modules: a season's players and final results. */
@Service
@Transactional(readOnly = true)
public class MatchResults {

	private final SeasonRepository seasons;
	private final SeasonParticipantRepository participants;
	private final FixtureRepository fixtures;
	private final PointAdjustmentRepository adjustments;
	private final ScoreReportRepository reports;
	private final TeamNames teamNames;

	MatchResults(SeasonRepository seasons, SeasonParticipantRepository participants, FixtureRepository fixtures,
			PointAdjustmentRepository adjustments, ScoreReportRepository reports, TeamNames teamNames) {
		this.seasons = seasons;
		this.participants = participants;
		this.fixtures = fixtures;
		this.adjustments = adjustments;
		this.reports = reports;
		this.teamNames = teamNames;
	}

	/**
	 * Goals, assists and MVPs from the final results of a season (null: the active or latest one): one credit per
	 * goal, assist or MVP award, with the manager whose side it was for. A season of another league is a 404.
	 */
	public Optional<SeasonCredits> seasonCredits(UUID leagueId, UUID seasonId) {
		return seasons.pick(leagueId, seasonId).map(season -> {
			Map<UUID, Fixture> byReport = new HashMap<>();
			for (Fixture f : fixtures.findBySeasonIdOrderByRoundAscIdAsc(season.getId())) {
				if (f.getStatus() == Fixture.Status.CONFIRMED && f.getFinalReportId() != null) {
					byReport.put(f.getFinalReportId(), f);
				}
			}
			List<Credit> credits = new ArrayList<>();
			for (ScoreReport report : reports.findAllById(byReport.keySet())) {
				Fixture f = byReport.get(report.getId());
				for (ScoreReport.Goal goal : report.getGoals()) {
					UUID userId = goal.side() == ScoreReport.Side.HOME ? f.getHomeUserId() : f.getAwayUserId();
					credits.add(new Credit(Credit.Kind.GOAL, userId, goal.scorer(), goal.scorerCardId()));
					if (goal.assist() != null) {
						credits.add(new Credit(Credit.Kind.ASSIST, userId, goal.assist(), goal.assistCardId()));
					}
				}
				ScoreReport.Mvp mvp = report.getMvp();
				if (mvp != null) {
					UUID userId = mvp.side() == ScoreReport.Side.HOME ? f.getHomeUserId() : f.getAwayUserId();
					credits.add(new Credit(Credit.Kind.MVP, userId, mvp.name(), mvp.cardId()));
				}
			}
			Set<UUID> managers = new HashSet<>();
			credits.forEach(c -> managers.add(c.userId()));
			return new SeasonCredits(season.getId(), season.getName(), season.isActive(),
					teamNames.of(leagueId, managers), credits);
		});
	}

	/**
	 * A season's players, final results and deductions (null: the active season, or the latest finished one);
	 * empty if the league has never had a season. A season of another league is a 404.
	 */
	public Optional<SeasonResults> season(UUID leagueId, UUID seasonId) {
		return seasons.pick(leagueId, seasonId).map(season -> {
			List<UUID> players = participants.findByKeySeasonId(season.getId()).stream()
				.map(SeasonParticipant::getUserId)
				.toList();
			List<FinalResult> results = fixtures.findBySeasonIdOrderByRoundAscIdAsc(season.getId()).stream()
				.filter(f -> f.getStatus() == Fixture.Status.CONFIRMED)
				.map(f -> new FinalResult(f.getHomeUserId(), f.getAwayUserId(), f.getHomeGoals(), f.getAwayGoals()))
				.toList();
			List<Deduction> deductions = adjustments.findBySeasonIdOrderByCreatedAtAsc(season.getId()).stream()
				.filter(a -> !a.isRevoked())
				.map(a -> new Deduction(a.getId(), a.getUserId(), a.getPoints(), a.getReason(), a.getCreatedAt()))
				.toList();
			return new SeasonResults(season.getId(), season.getName(), season.isActive(), season.getPointsWin(),
					season.getPointsDraw(), players, teamNames.of(leagueId, players), results, deductions);
		});
	}

	public record SeasonResults(UUID seasonId, String name, boolean active, int pointsWin, int pointsDraw,
			List<UUID> players, Map<UUID, String> teamNames, List<FinalResult> results, List<Deduction> deductions) {
	}

	/** Points taken from a player this season (revoked ones are left out). */
	public record Deduction(UUID id, UUID userId, int points, String reason, OffsetDateTime createdAt) {
	}

	public record SeasonCredits(UUID seasonId, String name, boolean active, Map<UUID, String> teamNames,
			List<Credit> credits) {
	}

	/** One goal, assist or MVP award for a player: a squad card (with its id), or a typed name (no id). */
	public record Credit(Kind kind, UUID userId, String name, UUID squadCardId) {

		public enum Kind {
			GOAL, ASSIST, MVP
		}

	}

	public record FinalResult(UUID homeId, UUID awayId, int homeGoals, int awayGoals) {
	}

}
