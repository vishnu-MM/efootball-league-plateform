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
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import com.leagueapp.identity.CurrentUser;

@RestController
class MembershipController {

	private final LeagueService leagueService;
	private final MembershipService membershipService;
	private final CurrentUser currentUser;

	MembershipController(LeagueService leagueService, MembershipService membershipService, CurrentUser currentUser) {
		this.leagueService = leagueService;
		this.membershipService = membershipService;
		this.currentUser = currentUser;
	}

	@GetMapping("/api/leagues/{slug}/members")
	List<MemberResponse> members(@PathVariable String slug) {
		return leagueService.members(slug, currentUser.requireId()).stream()
			.map(m -> new MemberResponse(m.userId(), m.displayName(), m.clubName(), m.role(), m.joinedAt()))
			.toList();
	}

	@PutMapping("/api/leagues/{slug}/members/{userId}/role")
	@ResponseStatus(HttpStatus.NO_CONTENT)
	void changeRole(@PathVariable String slug, @PathVariable UUID userId, @Valid @RequestBody RoleRequest body) {
		membershipService.changeRole(slug, currentUser.requireId(), userId, body.role());
	}

	@DeleteMapping("/api/leagues/{slug}/members/{userId}")
	@ResponseStatus(HttpStatus.NO_CONTENT)
	void remove(@PathVariable String slug, @PathVariable UUID userId) {
		membershipService.remove(slug, currentUser.requireId(), userId);
	}

	/** Sets the signed-in member's own club name in this league. */
	@PutMapping("/api/leagues/{slug}/club")
	ClubResponse setClub(@PathVariable String slug, @Valid @RequestBody ClubRequest body) {
		return new ClubResponse(membershipService.setClubName(slug, currentUser.requireId(), body.name()));
	}

	record RoleRequest(@NotNull LeagueRole role) {
	}

	record ClubRequest(@NotBlank @Size(min = 2, max = Club.NAME_MAX) String name) {
	}

	record ClubResponse(String name) {
	}

	record MemberResponse(UUID userId, String displayName, String clubName, LeagueRole role,
			OffsetDateTime joinedAt) {
	}

}
