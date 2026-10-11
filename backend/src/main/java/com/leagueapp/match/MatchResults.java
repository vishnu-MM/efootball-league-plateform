package com.leagueapp.match;

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
	private final TeamNames teamNames;

	MatchResults(SeasonRepository seasons, SeasonParticipantRepository participants, FixtureRepository fixtures,
			TeamNames teamNames) {
		this.seasons = seasons;
		this.participants = participants;
		this.fixtures = fixtures;
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
			return new SeasonResults(season.getId(), season.getName(), season.isActive(), season.getPointsWin(),
					season.getPointsDraw(), players, teamNames.of(leagueId, players), results);
		});
	}

	public record SeasonResults(UUID seasonId, String name, boolean active, int pointsWin, int pointsDraw,
			List<UUID> players, Map<UUID, String> teamNames, List<FinalResult> results) {
	}

	public record FinalResult(UUID homeId, UUID awayId, int homeGoals, int awayGoals) {
	}

}
