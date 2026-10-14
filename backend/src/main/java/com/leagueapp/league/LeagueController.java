package com.leagueapp.league;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import com.leagueapp.identity.CurrentUser;

@RestController
class LeagueController {

	private final LeagueService leagueService;
	private final CurrentUser currentUser;

	LeagueController(LeagueService leagueService, CurrentUser currentUser) {
		this.leagueService = leagueService;
		this.currentUser = currentUser;
	}

	@GetMapping("/api/public/leagues")
	List<LeagueSummary> publicLeagues() {
		return leagueService.publicLeagues().stream().map(LeagueSummary::from).toList();
	}

	/** Public leagues are visible to anyone; private ones only to members. */
	@GetMapping("/api/public/leagues/{slug}")
	LeagueDetailResponse league(@PathVariable String slug) {
		LeagueService.LeagueDetail detail = leagueService.detail(slug, currentUser.id().orElse(null));
		League league = detail.league();
		return new LeagueDetailResponse(league.getId(), league.getName(), league.getSlug(), league.getVisibility(),
				detail.myRole(), detail.memberCount(), league.getDeletedAt(), league.getRestoreUntil(), league.isHidden());
	}

	@PostMapping("/api/leagues")
	@ResponseStatus(HttpStatus.CREATED)
	LeagueSummary create(@Valid @RequestBody CreateLeagueRequest body) {
		return LeagueSummary.from(leagueService.create(currentUser.requireId(), body.name(), body.visibility()));
	}

	@PutMapping("/api/leagues/{slug}")
	LeagueSummary update(@PathVariable String slug, @Valid @RequestBody UpdateLeagueRequest body) {
		return LeagueSummary.from(leagueService.update(slug, currentUser.requireId(), body.name(), body.visibility()));
	}

	@DeleteMapping("/api/leagues/{slug}")
	@ResponseStatus(HttpStatus.NO_CONTENT)
	void delete(@PathVariable String slug, @Valid @RequestBody DeleteLeagueRequest body) {
		leagueService.delete(slug, currentUser.requireId(), body.confirmName());
	}

	@PostMapping("/api/leagues/{slug}/restore")
	@ResponseStatus(HttpStatus.NO_CONTENT)
	void restore(@PathVariable String slug) {
		leagueService.restore(slug, currentUser.requireId());
	}

	@GetMapping("/api/leagues/mine")
	List<MyLeagueResponse> mine() {
		return leagueService.leaguesOf(currentUser.requireId()).stream()
			.map(m -> new MyLeagueResponse(m.league().getId(), m.league().getName(), m.league().getSlug(), m.role(),
					m.league().getDeletedAt(), m.league().getRestoreUntil(), m.league().isHidden()))
			.toList();
	}

	record CreateLeagueRequest(@NotBlank @Size(min = 3, max = 80) String name,
			@NotNull League.Visibility visibility) {
	}

	record UpdateLeagueRequest(@NotBlank @Size(max = 80) String name, @NotNull League.Visibility visibility) {
	}

	record LeagueSummary(UUID id, String name, String slug) {

		static LeagueSummary from(League league) {
			return new LeagueSummary(league.getId(), league.getName(), league.getSlug());
		}

	}

	record DeleteLeagueRequest(@NotNull String confirmName) {
	}

	record MyLeagueResponse(UUID id, String name, String slug, LeagueRole role, OffsetDateTime deletedAt,
			OffsetDateTime restoreUntil, boolean hidden) {
	}

	record LeagueDetailResponse(UUID id, String name, String slug, League.Visibility visibility,
			LeagueRole myRole, long memberCount, OffsetDateTime deletedAt, OffsetDateTime restoreUntil,
			boolean hidden) {
	}

}
