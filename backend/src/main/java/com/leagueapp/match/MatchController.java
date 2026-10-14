package com.leagueapp.match;

import java.time.Duration;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import org.springframework.core.io.InputStreamResource;
import org.springframework.http.CacheControl;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import com.leagueapp.files.Uploads;
import com.leagueapp.identity.CurrentUser;

@RestController
class MatchController {

	private final SeasonService seasonService;
	private final FixtureService fixtureService;
	private final DeductionService deductionService;
	private final CurrentUser currentUser;

	MatchController(SeasonService seasonService, FixtureService fixtureService, DeductionService deductionService,
			CurrentUser currentUser) {
		this.seasonService = seasonService;
		this.fixtureService = fixtureService;
		this.deductionService = deductionService;
		this.currentUser = currentUser;
	}

	/** The active season (or the latest finished one); {@code season} is null if the league hasn't had one. */
	@GetMapping("/api/public/leagues/{slug}/season")
	CurrentSeasonResponse currentSeason(@PathVariable String slug, @RequestParam(required = false) UUID season) {
		return new CurrentSeasonResponse(seasonService.current(slug, currentUser.id().orElse(null), season)
			.map(SeasonResponse::from)
			.orElse(null));
	}

	/** Every season of the league, newest first, for the season picker. */
	@GetMapping("/api/public/leagues/{slug}/seasons")
	List<SeasonListItem> seasons(@PathVariable String slug) {
		return seasonService.all(slug, currentUser.id().orElse(null)).stream()
			.map(s -> new SeasonListItem(s.getId(), s.getName(), s.getStatus(), s.getCreatedAt(), s.getCompletedAt()))
			.toList();
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

	/** A screenshot attached to one of this match's reports: for the two players and owners/admins. */
	@GetMapping("/api/leagues/{slug}/fixtures/{id}/evidence/{uploadId}")
	ResponseEntity<InputStreamResource> evidence(@PathVariable String slug, @PathVariable UUID id,
			@PathVariable UUID uploadId) {
		Uploads.StoredImage image = fixtureService.evidence(slug, id, currentUser.requireId(), uploadId);
		return ResponseEntity.ok()
			.contentType(MediaType.parseMediaType(image.contentType()))
			.contentLength(image.size())
			.cacheControl(CacheControl.maxAge(Duration.ofHours(1)).cachePrivate())
			.body(new InputStreamResource(image.stream()));
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

	/** Owner/admin: replace a final result. {@code note} is the reason, and is required. */
	@PostMapping("/api/leagues/{slug}/fixtures/{id}/override")
	@ResponseStatus(HttpStatus.NO_CONTENT)
	void override(@PathVariable String slug, @PathVariable UUID id, @Valid @RequestBody OverrideRequest body) {
		fixtureService.override(slug, id, currentUser.requireId(),
				new ScoreInput(body.homeGoals(), body.awayGoals(), body.goals(), body.reason(), body.mvp(), List.of()));
	}

	/** Owner/admin: settle a fixture as forfeited by one side (3–0 unless a score is given) or both (0–0). */
	@PostMapping("/api/leagues/{slug}/fixtures/{id}/forfeit")
	@ResponseStatus(HttpStatus.NO_CONTENT)
	void forfeit(@PathVariable String slug, @PathVariable UUID id, @Valid @RequestBody ForfeitRequest body) {
		fixtureService.forfeit(slug, id, currentUser.requireId(), body.forfeiting(), body.homeGoals(), body.awayGoals(),
				body.reason());
	}

	@PostMapping("/api/leagues/{slug}/deductions")
	@ResponseStatus(HttpStatus.CREATED)
	CreatedDeduction deduct(@PathVariable String slug, @Valid @RequestBody DeductionRequest body) {
		return new CreatedDeduction(
				deductionService.deduct(slug, currentUser.requireId(), body.userId(), body.points(), body.reason()).getId());
	}

	@PostMapping("/api/leagues/{slug}/deductions/{id}/revoke")
	@ResponseStatus(HttpStatus.NO_CONTENT)
	void revokeDeduction(@PathVariable String slug, @PathVariable UUID id, @Valid @RequestBody ReasonRequest body) {
		deductionService.revoke(slug, currentUser.requireId(), id, body.reason());
	}

	record OverrideRequest(@Min(0) @Max(ScoreInput.MAX_GOALS) int homeGoals,
			@Min(0) @Max(ScoreInput.MAX_GOALS) int awayGoals,
			@Size(max = 2 * ScoreInput.MAX_GOALS) List<ScoreInput.GoalInput> goals,
			@Size(max = ScoreReport.NOTE_MAX) String reason, ScoreInput.MvpInput mvp) {
	}

	record ForfeitRequest(Fixture.Forfeit forfeiting, Integer homeGoals, Integer awayGoals,
			@Size(max = ScoreReport.NOTE_MAX) String reason) {
	}

	record DeductionRequest(UUID userId, int points, @Size(max = ScoreReport.NOTE_MAX) String reason) {
	}

	record ReasonRequest(@Size(max = ScoreReport.NOTE_MAX) String reason) {
	}

	record CreatedDeduction(UUID id) {
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
			@Size(max = ScoreReport.NOTE_MAX) String note, ScoreInput.MvpInput mvp,
			@Size(max = ScoreInput.MAX_EVIDENCE) List<UUID> evidenceUploadIds) {

		ScoreInput toInput() {
			return new ScoreInput(homeGoals, awayGoals, goals, note, mvp, evidenceUploadIds);
		}

	}

	record CurrentSeasonResponse(SeasonResponse season) {
	}

	record SeasonListItem(UUID id, String name, Season.Status status, OffsetDateTime startedAt,
			OffsetDateTime completedAt) {
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
