package com.leagueapp.match;

import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import com.leagueapp.league.LeagueService;
import com.leagueapp.league.LeagueService.LeagueAccess;

/** One fixture's details, and moving it through report → confirm, or report → dispute → resolve. */
@Service
@Transactional(readOnly = true)
class FixtureService {

	private final LeagueService leagueService;
	private final SeasonRepository seasons;
	private final FixtureRepository fixtures;
	private final ScoreReportRepository reports;
	private final TeamNames teamNames;

	FixtureService(LeagueService leagueService, SeasonRepository seasons, FixtureRepository fixtures,
			ScoreReportRepository reports, TeamNames teamNames) {
		this.leagueService = leagueService;
		this.seasons = seasons;
		this.fixtures = fixtures;
		this.reports = reports;
		this.teamNames = teamNames;
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
				league.canManage());
		List<ReportView> reportViews = history.stream()
			.map(r -> new ReportView(r.getId(), r.getKind(), r.getReportedBy(), names.getOrDefault(r.getReportedBy(), "Player"),
					r.getHomeGoals(), r.getAwayGoals(), r.getNote(), r.getCreatedAt(), List.copyOf(r.getGoals())))
			.toList();
		return new FixtureDetail(summary, season.getName(), reportViews, fixture.getFinalReportId());
	}

	/** A player enters the score. The fixture then waits for the opponent (or an admin) to confirm it. */
	@Transactional
	void report(String slug, UUID fixtureId, UUID actorId, ScoreInput score) {
		Locked locked = lockFor(slug, fixtureId, actorId, ResultRules.Action.REPORT);
		reports.save(newReport(locked.fixture(), ScoreReport.Kind.REPORT, actorId, score));
		locked.fixture().markReported();
	}

	/** The opponent (or an admin on their behalf) accepts the reported score; it becomes final. */
	@Transactional
	void confirm(String slug, UUID fixtureId, UUID actorId) {
		Locked locked = lockFor(slug, fixtureId, actorId, ResultRules.Action.CONFIRM);
		locked.fixture().finalizeWith(locked.pending(), null);
		completeSeasonIfDone(locked.season());
	}

	/** The opponent disagrees and enters their own score; an owner or admin will decide. */
	@Transactional
	void dispute(String slug, UUID fixtureId, UUID actorId, ScoreInput score) {
		Locked locked = lockFor(slug, fixtureId, actorId, ResultRules.Action.DISPUTE);
		reports.save(newReport(locked.fixture(), ScoreReport.Kind.DISPUTE, actorId, score));
		locked.fixture().markDisputed();
	}

	/** An owner or admin settles a dispute with the correct score (either player's version, or their own). */
	@Transactional
	void resolve(String slug, UUID fixtureId, UUID actorId, ScoreInput score) {
		Locked locked = lockFor(slug, fixtureId, actorId, ResultRules.Action.RESOLVE);
		ScoreReport resolution = reports.save(newReport(locked.fixture(), ScoreReport.Kind.RESOLUTION, actorId, score));
		locked.fixture().finalizeWith(resolution, actorId);
		completeSeasonIfDone(locked.season());
	}

	private Locked lockFor(String slug, UUID fixtureId, UUID actorId, ResultRules.Action action) {
		LeagueAccess league = leagueService.access(slug, actorId);
		Fixture fixture = fixtures.lockById(fixtureId).orElseThrow(FixtureService::notFound);
		Season season = seasonOf(fixture, league);
		ScoreReport pending = pendingReport(fixture, reports.findByFixtureIdOrderByCreatedAtAsc(fixtureId));
		Set<ResultRules.Action> allowed = ResultRules.allowed(fixture.getStatus(), actorId, fixture.getHomeUserId(),
				fixture.getAwayUserId(), pending == null ? null : pending.getReportedBy(), league.canManage());
		if (!allowed.contains(action)) {
			throw new ResponseStatusException(HttpStatus.CONFLICT, cannot(action, fixture.getStatus()));
		}
		return new Locked(fixture, season, pending);
	}

	private static String cannot(ResultRules.Action action, Fixture.Status status) {
		if (status == Fixture.Status.CONFIRMED) {
			return "This result is already final";
		}
		return switch (action) {
			case REPORT -> "Only the two players can report this match, and only before a score is in";
			case CONFIRM -> "Only the opponent or an owner or admin can confirm this score";
			case DISPUTE -> "Only the opponent can dispute this score";
			case RESOLVE -> "Only an owner or admin can settle a disputed result";
		};
	}

	private void completeSeasonIfDone(Season season) {
		if (season.isActive() && !fixtures.existsBySeasonIdAndStatusNot(season.getId(), Fixture.Status.CONFIRMED)) {
			season.complete();
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

	private static ScoreReport newReport(Fixture fixture, ScoreReport.Kind kind, UUID actorId, ScoreInput score) {
		score.validate();
		return new ScoreReport(fixture.getId(), kind, actorId, score.homeGoals(), score.awayGoals(), score.cleanNote(),
				score.cleanGoals());
	}

	private static ResponseStatusException notFound() {
		return new ResponseStatusException(HttpStatus.NOT_FOUND, "Match not found");
	}

	private record Locked(Fixture fixture, Season season, ScoreReport pending) {
	}

	record ReportView(UUID id, ScoreReport.Kind kind, UUID reportedBy, String reportedByName, int homeGoals,
			int awayGoals, String note, OffsetDateTime createdAt, List<ScoreReport.Goal> goals) {
	}

	record FixtureDetail(FixtureSummary fixture, String seasonName, List<ReportView> reports, UUID finalReportId) {
	}

}
