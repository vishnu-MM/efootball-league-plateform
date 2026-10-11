package com.leagueapp.league;

import java.util.UUID;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RestController;

import com.leagueapp.identity.CurrentUser;

@RestController
class InviteController {

	private final InviteService inviteService;
	private final CurrentUser currentUser;

	InviteController(InviteService inviteService, CurrentUser currentUser) {
		this.inviteService = inviteService;
		this.currentUser = currentUser;
	}

	/** The league's active link, or a null code if none has been made yet. Owners and admins only. */
	@GetMapping("/api/leagues/{slug}/invite")
	InviteResponse currentInvite(@PathVariable String slug) {
		return new InviteResponse(inviteService.currentCode(slug, currentUser.requireId()).orElse(null));
	}

	@PostMapping("/api/leagues/{slug}/invite")
	InviteResponse createInvite(@PathVariable String slug) {
		return new InviteResponse(inviteService.getOrCreate(slug, currentUser.requireId()));
	}

	@PostMapping("/api/leagues/{slug}/invite/rotate")
	InviteResponse rotateInvite(@PathVariable String slug) {
		return new InviteResponse(inviteService.rotate(slug, currentUser.requireId()));
	}

	/** What the link leads to. Public, so people can see the league before signing in. */
	@GetMapping("/api/public/invites/{code}")
	InvitePreviewResponse preview(@PathVariable String code) {
		InviteService.InvitePreview preview = inviteService.preview(code, currentUser.id().orElse(null));
		League league = preview.league();
		return new InvitePreviewResponse(league.getId(), league.getName(), league.getSlug(), preview.memberCount(),
				preview.myRole());
	}

	@PostMapping("/api/invites/{code}/accept")
	JoinedResponse accept(@PathVariable String code) {
		InviteService.Joined joined = inviteService.accept(code, currentUser.requireId());
		return new JoinedResponse(joined.league().getName(), joined.league().getSlug(), joined.role());
	}

	record InviteResponse(String code) {
	}

	record InvitePreviewResponse(UUID id, String name, String slug, long memberCount, LeagueRole myRole) {
	}

	record JoinedResponse(String name, String slug, LeagueRole role) {
	}

}
