package com.leagueapp.standings;

import java.util.List;
import java.util.UUID;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RestController;

import com.leagueapp.identity.CurrentUser;
import com.leagueapp.league.LeagueService;
import com.leagueapp.match.MatchResults;

@RestController
class StandingsController {

	private final LeagueService leagueService;
	private final MatchResults matchResults;
	private final CurrentUser currentUser;

	StandingsController(LeagueService leagueService, MatchResults matchResults, CurrentUser currentUser) {
		this.leagueService = leagueService;
		this.matchResults = matchResults;
		this.currentUser = currentUser;
	}

	/** The table for the active (or latest) season, recalculated from final results on every read. */
	@GetMapping("/api/public/leagues/{slug}/standings")
	StandingsResponse standings(@PathVariable String slug) {
		LeagueService.LeagueAccess league = leagueService.access(slug, currentUser.id().orElse(null));
		return matchResults.latestSeason(league.leagueId())
			.map(s -> new StandingsResponse(new SeasonRef(s.seasonId(), s.name(), s.active()),
					StandingsCalculator.table(s.players(), s.teamNames(), s.results(), s.pointsWin(), s.pointsDraw())))
			.orElse(new StandingsResponse(null, List.of()));
	}

	record StandingsResponse(SeasonRef season, List<StandingsCalculator.Row> rows) {
	}

	record SeasonRef(UUID id, String name, boolean active) {
	}

}
