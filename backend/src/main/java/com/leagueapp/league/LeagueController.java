package com.leagueapp.league;

import java.util.List;
import java.util.UUID;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
class LeagueController {

	private final LeagueService leagueService;

	LeagueController(LeagueService leagueService) {
		this.leagueService = leagueService;
	}

	@GetMapping("/api/public/leagues")
	List<LeagueSummary> publicLeagues() {
		return leagueService.publicLeagues().stream().map(LeagueSummary::from).toList();
	}

	record LeagueSummary(UUID id, String name, String slug) {

		static LeagueSummary from(League league) {
			return new LeagueSummary(league.getId(), league.getName(), league.getSlug());
		}

	}

}
