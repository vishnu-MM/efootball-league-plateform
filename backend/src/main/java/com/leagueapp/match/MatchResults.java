package com.leagueapp.match;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.Map;
import java.util.Optional;
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
	private final TeamNames teamNames;

	MatchResults(SeasonRepository seasons, SeasonParticipantRepository participants, FixtureRepository fixtures,
			PointAdjustmentRepository adjustments, TeamNames teamNames) {
		this.seasons = seasons;
		this.participants = participants;
		this.fixtures = fixtures;
		this.adjustments = adjustments;
		this.teamNames = teamNames;
	}

	/** The active season, or the latest finished one; empty if the league has never had a season. */
	public Optional<SeasonResults> latestSeason(UUID leagueId) {
		return seasons.findFirstByLeagueIdOrderByNumberDesc(leagueId).map(season -> {
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

	public record FinalResult(UUID homeId, UUID awayId, int homeGoals, int awayGoals) {
	}

}
