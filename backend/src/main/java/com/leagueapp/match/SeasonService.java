package com.leagueapp.match;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;

import org.springframework.context.ApplicationEventPublisher;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import com.leagueapp.league.LeagueService;
import com.leagueapp.league.LeagueService.LeagueAccess;
import com.leagueapp.roster.Squads;

@Service
@Transactional(readOnly = true)
class SeasonService {

	static final int MAX_PLAYERS = 40;

	private final LeagueService leagueService;
	private final SeasonRepository seasons;
	private final SeasonParticipantRepository participants;
	private final FixtureRepository fixtures;
	private final ScoreReportRepository reports;
	private final TeamNames teamNames;
	private final Squads squads;
	private final ApplicationEventPublisher events;

	SeasonService(LeagueService leagueService, SeasonRepository seasons, SeasonParticipantRepository participants,
			FixtureRepository fixtures, ScoreReportRepository reports, TeamNames teamNames, Squads squads,
			ApplicationEventPublisher events) {
		this.leagueService = leagueService;
		this.seasons = seasons;
		this.participants = participants;
		this.fixtures = fixtures;
		this.reports = reports;
		this.teamNames = teamNames;
		this.squads = squads;
		this.events = events;
	}

	/** Creates the season and its whole fixture list. Owners and admins only; one active season per league. */
	@Transactional
	Season create(String slug, UUID actorId, NewSeason request) {
		LeagueAccess league = leagueService.access(slug, actorId);
		if (!league.canManage()) {
			throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Only the owner or an admin can start a season");
		}
		if (seasons.existsByLeagueIdAndStatus(league.leagueId(), Season.Status.ACTIVE)) {
			throw activeSeasonExists();
		}
		List<UUID> players = validPlayers(league.leagueId(), request.participantIds());
		if ((request.startDate() == null) != (request.daysPerRound() == null)) {
			throw badRequest("Play-by dates need both a start date and the days per round");
		}
		if (request.pointsDraw() > request.pointsWin()) {
			throw badRequest("A draw can't be worth more than a win");
		}
		requireLegalSquads(league.leagueId(), players);

		int number = seasons.maxNumber(league.leagueId()) + 1;
		String name = request.name() == null || request.name().isBlank() ? "Season " + number : request.name().strip();
		Season season = new Season(league.leagueId(), number, name, request.format(), request.pointsWin(),
				request.pointsDraw(), request.startDate(), request.daysPerRound(), actorId);
		try {
			seasons.saveAndFlush(season);
		}
		catch (DataIntegrityViolationException e) {
			throw activeSeasonExists(); // another admin started one at the same moment
		}
		participants.saveAll(players.stream().map(id -> new SeasonParticipant(season.getId(), id)).toList());

		List<UUID> order = new ArrayList<>(players);
		Collections.shuffle(order);
		fixtures.saveAll(FixtureGenerator.roundRobin(order, request.format() == Season.Format.HOME_AND_AWAY).stream()
			.map(p -> new Fixture(season.getId(), p.round(), p.home(), p.away(), season.playBy(p.round())))
			.toList());
		squads.closeWindow(league.leagueId());
		events.publishEvent(new MatchEvents.SeasonStarted(league.leagueId(), actorId, season.getId(), season.getName(),
				players));
		return season;
	}

	/** Every player's squad must follow the league's rules before they can play. */
	private void requireLegalSquads(UUID leagueId, List<UUID> players) {
		Map<UUID, List<String>> problems = squads.problems(leagueId, players);
		if (!problems.isEmpty()) {
			Map<UUID, String> names = teamNames.of(leagueId, problems.keySet());
			String who = problems.keySet().stream()
				.map(id -> names.getOrDefault(id, "Player"))
				.sorted()
				.collect(Collectors.joining(", "));
			throw new ResponseStatusException(HttpStatus.CONFLICT,
					"These squads break the squad rules and need fixing first: " + who);
		}
	}

	/** The active season, or the latest finished one, with every fixture and what the viewer may do with each. */
	Optional<SeasonView> current(String slug, UUID viewerId) {
		LeagueAccess league = leagueService.access(slug, viewerId);
		return seasons.findFirstByLeagueIdOrderByNumberDesc(league.leagueId()).map(season -> view(season, league, viewerId));
	}

	private SeasonView view(Season season, LeagueAccess league, UUID viewerId) {
		List<Fixture> all = fixtures.findBySeasonIdOrderByRoundAscIdAsc(season.getId());
		List<UUID> playerIds = participants.findByKeySeasonId(season.getId()).stream()
			.map(SeasonParticipant::getUserId)
			.toList();
		Map<UUID, String> names = teamNames.of(league.leagueId(), playerIds);
		Map<UUID, ScoreReport> pending = pendingReports(all);

		List<FixtureSummary> summaries = all.stream()
			.map(f -> FixtureSummary.of(f, names, pending.get(f.getId()), viewerId, league.canManage(),
					season.isActive()))
			.toList();
		List<Player> players = playerIds.stream()
			.map(id -> new Player(id, names.getOrDefault(id, "Player")))
			.sorted(Comparator.comparing(Player::name, String.CASE_INSENSITIVE_ORDER))
			.toList();
		return new SeasonView(season, players, summaries, league.canManage());
	}

	/** The latest plain report for each fixture awaiting confirmation (whose score is shown, and who entered it). */
	private Map<UUID, ScoreReport> pendingReports(List<Fixture> all) {
		List<UUID> awaiting = all.stream()
			.filter(f -> f.getStatus() == Fixture.Status.AWAITING_CONFIRMATION)
			.map(Fixture::getId)
			.toList();
		if (awaiting.isEmpty()) {
			return Map.of();
		}
		return reports.findByFixtureIdIn(awaiting).stream()
			.filter(r -> r.getKind() == ScoreReport.Kind.REPORT)
			.collect(Collectors.toMap(ScoreReport::getFixtureId, Function.identity(),
					(a, b) -> a.getCreatedAt().isAfter(b.getCreatedAt()) ? a : b));
	}

	private List<UUID> validPlayers(UUID leagueId, List<UUID> requested) {
		Set<UUID> unique = new LinkedHashSet<>(requested);
		if (unique.size() < 2) {
			throw badRequest("Pick at least two players");
		}
		if (unique.size() > MAX_PLAYERS) {
			throw badRequest("A season can have at most " + MAX_PLAYERS + " players");
		}
		Set<UUID> members = leagueService.memberInfos(leagueId).stream()
			.map(LeagueService.MemberInfo::userId)
			.collect(Collectors.toSet());
		if (!members.containsAll(unique)) {
			throw badRequest("Everyone in the season must be a member of the league");
		}
		return List.copyOf(unique);
	}

	private static ResponseStatusException activeSeasonExists() {
		return new ResponseStatusException(HttpStatus.CONFLICT, "This league already has a season in progress");
	}

	private static ResponseStatusException badRequest(String message) {
		return new ResponseStatusException(HttpStatus.BAD_REQUEST, message);
	}

	record NewSeason(String name, List<UUID> participantIds, Season.Format format, int pointsWin, int pointsDraw,
			LocalDate startDate, Integer daysPerRound) {
	}

	record Player(UUID userId, String name) {
	}

	record SeasonView(Season season, List<Player> players, List<FixtureSummary> fixtures, boolean canManage) {
	}

}
