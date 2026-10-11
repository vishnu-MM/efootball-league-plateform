package com.leagueapp.match;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import com.leagueapp.identity.CurrentUser;

@RestController
class MatchController {

	private final SeasonService seasonService;
	private final FixtureService fixtureService;
	private final CurrentUser currentUser;

	MatchController(SeasonService seasonService, FixtureService fixtureService, CurrentUser currentUser) {
		this.seasonService = seasonService;
		this.fixtureService = fixtureService;
		this.currentUser = currentUser;
	}

	/** The active season (or the latest finished one); {@code season} is null if the league hasn't had one. */
	@GetMapping("/api/public/leagues/{slug}/season")
	CurrentSeasonResponse currentSeason(@PathVariable String slug) {
		return new CurrentSeasonResponse(seasonService.current(slug, currentUser.id().orElse(null))
			.map(SeasonResponse::from)
			.orElse(null));
	}

	@PostMapping("/api/leagues/{slug}/seasons")
	@ResponseStatus(HttpStatus.CREATED)
	CreatedSeason createSeason(@PathVariable String slug, @Valid @RequestBody CreateSeasonRequest body) {
		Season season = seasonService.create(slug, currentUser.requireId(),
				new SeasonService.NewSeason(body.name(), body.participantIds(), body.format(), body.pointsWin(),
						body.pointsDraw(), body.startDate(), body.daysPerRound()));
		return new CreatedSeason(season.getId(), season.getName());
	}

	@GetMapping("/api/public/leagues/{slug}/fixtures/{id}")
	FixtureService.FixtureDetail fixture(@PathVariable String slug, @PathVariable UUID id) {
		return fixtureService.detail(slug, id, currentUser.id().orElse(null));
	}

	@PostMapping("/api/leagues/{slug}/fixtures/{id}/report")
	@ResponseStatus(HttpStatus.NO_CONTENT)
	void report(@PathVariable String slug, @PathVariable UUID id, @Valid @RequestBody ScoreRequest body) {
		fixtureService.report(slug, id, currentUser.requireId(), body.toInput());
	}

	@PostMapping("/api/leagues/{slug}/fixtures/{id}/confirm")
	@ResponseStatus(HttpStatus.NO_CONTENT)
	void confirm(@PathVariable String slug, @PathVariable UUID id) {
		fixtureService.confirm(slug, id, currentUser.requireId());
	}

	@PostMapping("/api/leagues/{slug}/fixtures/{id}/dispute")
	@ResponseStatus(HttpStatus.NO_CONTENT)
	void dispute(@PathVariable String slug, @PathVariable UUID id, @Valid @RequestBody ScoreRequest body) {
		fixtureService.dispute(slug, id, currentUser.requireId(), body.toInput());
	}

	@PostMapping("/api/leagues/{slug}/fixtures/{id}/resolve")
	@ResponseStatus(HttpStatus.NO_CONTENT)
	void resolve(@PathVariable String slug, @PathVariable UUID id, @Valid @RequestBody ScoreRequest body) {
		fixtureService.resolve(slug, id, currentUser.requireId(), body.toInput());
	}

	record CreateSeasonRequest(@Size(max = Season.NAME_MAX) String name,
			@NotEmpty @Size(max = SeasonService.MAX_PLAYERS) List<UUID> participantIds, @NotNull Season.Format format,
			@Min(1) @Max(10) int pointsWin, @Min(0) @Max(10) int pointsDraw, LocalDate startDate,
			@Min(1) @Max(60) Integer daysPerRound) {
	}

	record CreatedSeason(UUID id, String name) {
	}

	record ScoreRequest(@Min(0) @Max(ScoreInput.MAX_GOALS) int homeGoals, @Min(0) @Max(ScoreInput.MAX_GOALS) int awayGoals,
			@Size(max = 2 * ScoreInput.MAX_GOALS) List<ScoreInput.GoalInput> goals,
			@Size(max = ScoreReport.NOTE_MAX) String note) {

		ScoreInput toInput() {
			return new ScoreInput(homeGoals, awayGoals, goals, note);
		}

	}

	record CurrentSeasonResponse(SeasonResponse season) {
	}

	record SeasonResponse(UUID id, int number, String name, Season.Format format, Season.Status status, int pointsWin,
			int pointsDraw, LocalDate startDate, Integer daysPerRound, List<SeasonService.Player> players,
			List<FixtureSummary> fixtures, boolean canManage) {

		static SeasonResponse from(SeasonService.SeasonView view) {
			Season s = view.season();
			return new SeasonResponse(s.getId(), s.getNumber(), s.getName(), s.getFormat(), s.getStatus(),
					s.getPointsWin(), s.getPointsDraw(), s.getStartDate(), s.getDaysPerRound(), view.players(),
					view.fixtures(), view.canManage());
		}

	}

}
