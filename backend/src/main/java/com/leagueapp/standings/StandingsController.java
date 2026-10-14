package com.leagueapp.standings;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestParam;
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

	/** The table for a season (by default the active or latest one), recalculated from final results on every read. */
	@GetMapping("/api/public/leagues/{slug}/standings")
	StandingsResponse standings(@PathVariable String slug, @RequestParam(required = false) UUID season) {
		LeagueService.LeagueAccess league = leagueService.access(slug, currentUser.id().orElse(null));
		return matchResults.season(league.leagueId(), season).map(s -> {
			Map<UUID, Integer> deducted = s.deductions().stream()
				.collect(Collectors.groupingBy(MatchResults.Deduction::userId,
						Collectors.summingInt(MatchResults.Deduction::points)));
			List<DeductionView> deductions = s.deductions().stream()
				.map(d -> new DeductionView(d.id(), d.userId(), s.teamNames().getOrDefault(d.userId(), "Player"),
						d.points(), d.reason(), d.createdAt()))
				.toList();
			return new StandingsResponse(new SeasonRef(s.seasonId(), s.name(), s.active()), StandingsCalculator
				.table(s.players(), s.teamNames(), s.results(), s.pointsWin(), s.pointsDraw(), deducted), deductions);
		}).orElse(new StandingsResponse(null, List.of(), List.of()));
	}

	record StandingsResponse(SeasonRef season, List<StandingsCalculator.Row> rows, List<DeductionView> deductions) {
	}

	record DeductionView(UUID id, UUID userId, String teamName, int points, String reason, OffsetDateTime createdAt) {
	}

	record SeasonRef(UUID id, String name, boolean active) {
	}

}
