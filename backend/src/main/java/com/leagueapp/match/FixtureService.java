package com.leagueapp.match;

import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

import org.springframework.context.ApplicationEventPublisher;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import com.leagueapp.league.LeagueService;
import com.leagueapp.league.LeagueService.LeagueAccess;
import com.leagueapp.roster.Squads;

/**
 * One fixture's details, and moving it through report → confirm, or report → dispute → resolve. Owners and admins
 * can also override a final result or record a forfeit while the season is active.
 */
@Service
@Transactional(readOnly = true)
class FixtureService {

	static final int FORFEIT_GOALS = 3;

	private final LeagueService leagueService;
	private final SeasonRepository seasons;
	private final SeasonParticipantRepository participants;
	private final FixtureRepository fixtures;
	private final ScoreReportRepository reports;
	private final TeamNames teamNames;
	private final Squads squads;
	private final ApplicationEventPublisher events;

	FixtureService(LeagueService leagueService, SeasonRepository seasons, SeasonParticipantRepository participants,
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

	FixtureDetail detail(String slug, UUID fixtureId, UUID viewerId) {
		LeagueAccess league = leagueService.access(slug, viewerId);
		Fixture fixture = fixtures.findById(fixtureId).orElseThrow(FixtureService::notFound);
		Season season = seasonOf(fixture, league);
		List<ScoreReport> history = reports.findByFixtureIdOrderByCreatedAtAsc(fixtureId);

		List<UUID> people = new ArrayList<>(List.of(fixture.getHomeUserId(), fixture.getAwayUserId()));
		history.forEach(r -> people.add(r.getReportedBy()));
		Map<UUID, String> names = teamNames.of(league.leagueId(), people);

		FixtureSummary summary = FixtureSummary.of(fixture, names, pendingReport(fixture, history), viewerId,
				league.canManage(), season.isActive());
		List<ReportView> reportViews = history.stream()
			.map(r -> new ReportView(r.getId(), r.getKind(), r.getReportedBy(), names.getOrDefault(r.getReportedBy(), "Player"),
					r.getHomeGoals(), r.getAwayGoals(), r.getNote(), r.getCreatedAt(), List.copyOf(r.getGoals()), r.getMvp()))
			.toList();
		return new FixtureDetail(summary, season.getName(), reportViews, fixture.getFinalReportId());
	}

	/** A player enters the score. The fixture then waits for the opponent (or an admin) to confirm it. */
	@Transactional
	void report(String slug, UUID fixtureId, UUID actorId, ScoreInput score) {
		Locked locked = lockFor(slug, fixtureId, actorId, ResultRules.Action.REPORT);
		ScoreReport report = reports.save(newReport(locked, ScoreReport.Kind.REPORT, actorId, score));
		locked.fixture().markReported();
		events.publishEvent(new MatchEvents.ResultReported(locked.leagueId(), actorId, match(locked.fixture(), report)));
	}

	/** The opponent (or an admin on their behalf) accepts the reported score; it becomes final. */
	@Transactional
	void confirm(String slug, UUID fixtureId, UUID actorId) {
		Locked locked = lockFor(slug, fixtureId, actorId, ResultRules.Action.CONFIRM);
		locked.fixture().finalizeWith(locked.pending(), null);
		events.publishEvent(new MatchEvents.ResultConfirmed(locked.leagueId(), actorId,
				locked.pending().getReportedBy(), match(locked.fixture(), locked.pending())));
		completeSeasonIfDone(locked.season());
	}

	/** The opponent disagrees and enters their own score; an owner or admin will decide. */
	@Transactional
	void dispute(String slug, UUID fixtureId, UUID actorId, ScoreInput score) {
		Locked locked = lockFor(slug, fixtureId, actorId, ResultRules.Action.DISPUTE);
		ScoreReport counter = reports.save(newReport(locked, ScoreReport.Kind.DISPUTE, actorId, score));
		locked.fixture().markDisputed();
		events.publishEvent(new MatchEvents.DisputeOpened(locked.leagueId(), actorId, locked.pending().getReportedBy(),
				match(locked.fixture(), counter)));
	}

	/** An owner or admin settles a dispute with the correct score (either player's version, or their own). */
	@Transactional
	void resolve(String slug, UUID fixtureId, UUID actorId, ScoreInput score) {
		Locked locked = lockFor(slug, fixtureId, actorId, ResultRules.Action.RESOLVE);
		ScoreReport resolution = reports.save(newReport(locked, ScoreReport.Kind.RESOLUTION, actorId, score));
		locked.fixture().finalizeWith(resolution, actorId);
		events.publishEvent(
				new MatchEvents.DisputeResolved(locked.leagueId(), actorId, match(locked.fixture(), resolution)));
		completeSeasonIfDone(locked.season());
	}

	/** An owner or admin replaces a final result (score and scorers); the note is their reason. Active season only. */
	@Transactional
	void override(String slug, UUID fixtureId, UUID actorId, ScoreInput score) {
		requireReason(score.note());
		Locked locked = lockFor(slug, fixtureId, actorId, ResultRules.Action.OVERRIDE);
		Fixture fixture = locked.fixture();
		int oldHome = fixture.getHomeGoals();
		int oldAway = fixture.getAwayGoals();
		ScoreReport override = reports.save(newReport(locked, ScoreReport.Kind.OVERRIDE, actorId, score));
		fixture.overrideWith(override, actorId);
		events.publishEvent(new MatchEvents.ResultOverridden(locked.leagueId(), actorId, oldHome, oldAway,
				override.getNote(), match(fixture, override)));
	}

	/**
	 * An owner or admin settles a fixture as forfeited. One side: 3–0 to the other unless a score is given (the
	 * forfeiting side must lose it). Both sides: a 0–0 draw. No scorers either way.
	 */
	@Transactional
	void forfeit(String slug, UUID fixtureId, UUID actorId, Fixture.Forfeit who, Integer homeGoals, Integer awayGoals,
			String reason) {
		String why = requireReason(reason);
		if (who == null) {
			throw badRequest("Say which side forfeits");
		}
		int[] score = forfeitScore(who, homeGoals, awayGoals);
		Locked locked = lockFor(slug, fixtureId, actorId, ResultRules.Action.FORFEIT);
		ScoreReport forfeit = reports.save(new ScoreReport(fixtureId, ScoreReport.Kind.FORFEIT, actorId, score[0],
				score[1], why, List.of(), null));
		locked.fixture().forfeitWith(forfeit, actorId, who);
		events.publishEvent(new MatchEvents.ForfeitAwarded(locked.leagueId(), actorId, who.name(), why,
				match(locked.fixture(), forfeit)));
		completeSeasonIfDone(locked.season());
	}

	/** The final score of a forfeit: [home, away]. */
	static int[] forfeitScore(Fixture.Forfeit who, Integer homeGoals, Integer awayGoals) {
		if (who == Fixture.Forfeit.BOTH) {
			if ((homeGoals != null && homeGoals != 0) || (awayGoals != null && awayGoals != 0)) {
				throw badRequest("A double forfeit is always 0–0");
			}
			return new int[] { 0, 0 };
		}
		if (homeGoals == null && awayGoals == null) {
			return who == Fixture.Forfeit.HOME ? new int[] { 0, FORFEIT_GOALS } : new int[] { FORFEIT_GOALS, 0 };
		}
		if (homeGoals == null || awayGoals == null) {
			throw badRequest("Enter both goals, or leave both empty for 3–0");
		}
		if (homeGoals < 0 || awayGoals < 0 || homeGoals > ScoreInput.MAX_GOALS || awayGoals > ScoreInput.MAX_GOALS) {
			throw badRequest("Goals must be between 0 and " + ScoreInput.MAX_GOALS);
		}
		boolean forfeiterLoses = who == Fixture.Forfeit.HOME ? homeGoals < awayGoals : awayGoals < homeGoals;
		if (!forfeiterLoses) {
			throw badRequest("The side that forfeits has to lose");
		}
		return new int[] { homeGoals, awayGoals };
	}

	/** Corrections need a reason, since the whole league sees them in the log. */
	static String requireReason(String reason) {
		if (reason == null || reason.isBlank()) {
			throw badRequest("Give a reason; the whole league will see it in the log");
		}
		String clean = reason.strip();
		if (clean.length() > ScoreReport.NOTE_MAX) {
			throw badRequest("Keep the reason under " + ScoreReport.NOTE_MAX + " characters");
		}
		return clean;
	}

	private Locked lockFor(String slug, UUID fixtureId, UUID actorId, ResultRules.Action action) {
		LeagueAccess league = leagueService.access(slug, actorId);
		Fixture fixture = fixtures.lockById(fixtureId).orElseThrow(FixtureService::notFound);
		Season season = seasonOf(fixture, league);
		ScoreReport pending = pendingReport(fixture, reports.findByFixtureIdOrderByCreatedAtAsc(fixtureId));
		Set<ResultRules.Action> allowed = ResultRules.allowed(fixture.getStatus(), actorId, fixture.getHomeUserId(),
				fixture.getAwayUserId(), pending == null ? null : pending.getReportedBy(), league.canManage(),
				season.isActive());
		if (!allowed.contains(action)) {
			throw new ResponseStatusException(HttpStatus.CONFLICT, cannot(action, fixture.getStatus(), season));
		}
		return new Locked(fixture, season, pending);
	}

	private static String cannot(ResultRules.Action action, Fixture.Status status, Season season) {
		if (action == ResultRules.Action.OVERRIDE || action == ResultRules.Action.FORFEIT) {
			if (!season.isActive()) {
				return "This season is finished, so its results can't be changed";
			}
			if (action == ResultRules.Action.OVERRIDE && status != Fixture.Status.CONFIRMED) {
				return "Only final results can be overridden";
			}
			return "Only an owner or admin can correct results";
		}
		if (status == Fixture.Status.CONFIRMED) {
			return "This result is already final";
		}
		return switch (action) {
			case REPORT -> "Only the two players can report this match, and only before a score is in";
			case CONFIRM -> "Only the opponent or an owner or admin can confirm this score";
			case DISPUTE -> "Only the opponent can dispute this score";
			case RESOLVE -> "Only an owner or admin can settle a disputed result";
			case OVERRIDE, FORFEIT -> throw new IllegalStateException(action.name());
		};
	}

	private void completeSeasonIfDone(Season season) {
		if (season.isActive() && !fixtures.existsBySeasonIdAndStatusNot(season.getId(), Fixture.Status.CONFIRMED)) {
			season.complete();
			squads.openWindow(season.getLeagueId());
			List<UUID> players = participants.findByKeySeasonId(season.getId()).stream()
				.map(SeasonParticipant::getUserId)
				.toList();
			events.publishEvent(new MatchEvents.SeasonCompleted(season.getLeagueId(), season.getId(), season.getName(),
					players));
		}
	}

	/** Fixtures belong to a league through their season; one from another league looks like it doesn't exist. */
	private Season seasonOf(Fixture fixture, LeagueAccess league) {
		return seasons.findById(fixture.getSeasonId())
			.filter(s -> s.getLeagueId().equals(league.leagueId()))
			.orElseThrow(FixtureService::notFound);
	}

	/** The plain report the fixture is waiting on, if it is awaiting confirmation. */
	private static ScoreReport pendingReport(Fixture fixture, List<ScoreReport> history) {
		if (fixture.getStatus() != Fixture.Status.AWAITING_CONFIRMATION) {
			return null;
		}
		ScoreReport latest = null;
		for (ScoreReport r : history) {
			if (r.getKind() == ScoreReport.Kind.REPORT) {
				latest = r;
			}
		}
		return latest;
	}

	private ScoreReport newReport(Locked locked, ScoreReport.Kind kind, UUID actorId, ScoreInput score) {
		Fixture fixture = locked.fixture();
		UUID leagueId = locked.leagueId();
		Map<ScoreReport.Side, Map<UUID, String>> sideSquads = Map.of(
				ScoreReport.Side.HOME, squadNames(leagueId, fixture.getHomeUserId()),
				ScoreReport.Side.AWAY, squadNames(leagueId, fixture.getAwayUserId()));
		List<ScoreReport.Goal> goals = score.validGoals(sideSquads);
		return new ScoreReport(fixture.getId(), kind, actorId, score.homeGoals(), score.awayGoals(), score.cleanNote(),
				goals, score.validMvp(sideSquads));
	}

	/** A player's approved squad cards: squad card id → player name. */
	private Map<UUID, String> squadNames(UUID leagueId, UUID userId) {
		return squads.approvedCards(leagueId, userId).stream()
			.collect(Collectors.toMap(Squads.SquadPlayer::squadCardId, Squads.SquadPlayer::playerName));
	}

	private static MatchEvents.Match match(Fixture fixture, ScoreReport score) {
		return new MatchEvents.Match(fixture.getId(), fixture.getHomeUserId(), fixture.getAwayUserId(),
				score.getHomeGoals(), score.getAwayGoals());
	}

	private static ResponseStatusException notFound() {
		return new ResponseStatusException(HttpStatus.NOT_FOUND, "Match not found");
	}

	private static ResponseStatusException badRequest(String message) {
		return new ResponseStatusException(HttpStatus.BAD_REQUEST, message);
	}

	private record Locked(Fixture fixture, Season season, ScoreReport pending) {

		UUID leagueId() {
			return season.getLeagueId();
		}

	}

	record ReportView(UUID id, ScoreReport.Kind kind, UUID reportedBy, String reportedByName, int homeGoals,
			int awayGoals, String note, OffsetDateTime createdAt, List<ScoreReport.Goal> goals, ScoreReport.Mvp mvp) {
	}

	record FixtureDetail(FixtureSummary fixture, String seasonName, List<ReportView> reports, UUID finalReportId) {
	}

}
