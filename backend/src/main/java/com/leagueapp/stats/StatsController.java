package com.leagueapp.stats;

import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.leagueapp.identity.CurrentUser;
import com.leagueapp.league.LeagueService;
import com.leagueapp.match.MatchResults;
import com.leagueapp.match.MatchResults.Credit;
import com.leagueapp.roster.CardType;
import com.leagueapp.roster.Position;
import com.leagueapp.roster.Squads;

@RestController
class StatsController {

	static final int LIMIT = 20;

	private final LeagueService leagueService;
	private final MatchResults matchResults;
	private final Squads squads;
	private final CurrentUser currentUser;

	StatsController(LeagueService leagueService, MatchResults matchResults, Squads squads, CurrentUser currentUser) {
		this.leagueService = leagueService;
		this.matchResults = matchResults;
		this.squads = squads;
		this.currentUser = currentUser;
	}

	/** Top scorers, assists and MVPs of a season (by default the active or latest one), from final results. */
	@GetMapping("/api/public/leagues/{slug}/stats")
	StatsResponse stats(@PathVariable String slug, @RequestParam(required = false) UUID season) {
		LeagueService.LeagueAccess league = leagueService.access(slug, currentUser.id().orElse(null));
		return matchResults.seasonCredits(league.leagueId(), season).map(s -> {
			List<UUID> cardIds = s.credits().stream().map(Credit::squadCardId).filter(Objects::nonNull).distinct().toList();
			Map<UUID, Squads.SquadPlayer> cards = squads.cardsById(cardIds);
			return new StatsResponse(new SeasonRef(s.seasonId(), s.name(), s.active()),
					rows(s, Credit.Kind.GOAL, cards), rows(s, Credit.Kind.ASSIST, cards), rows(s, Credit.Kind.MVP, cards));
		}).orElse(new StatsResponse(null, List.of(), List.of(), List.of()));
	}

	private static List<Row> rows(MatchResults.SeasonCredits season, Credit.Kind kind, Map<UUID, Squads.SquadPlayer> cards) {
		return LeaderboardCalculator.board(season.credits(), kind, LIMIT).stream().map(e -> {
			Squads.SquadPlayer card = e.squadCardId() == null ? null : cards.get(e.squadCardId());
			return new Row(e.rank(), e.name(), e.userId(), season.teamNames().getOrDefault(e.userId(), "Player"),
					card == null ? null : card.cardType(), card == null ? null : card.rating(),
					card == null ? null : card.position(), e.count());
		}).toList();
	}

	record StatsResponse(SeasonRef season, List<Row> scorers, List<Row> assists, List<Row> mvps) {
	}

	record SeasonRef(UUID id, String name, boolean active) {
	}

	/** One player on a board. Card details are null for typed names. */
	record Row(int rank, String name, UUID userId, String teamName, CardType cardType, Integer rating,
			Position position, int count) {
	}

}
