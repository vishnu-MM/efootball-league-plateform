package com.leagueapp.league;

import java.util.Optional;
import java.util.UUID;

import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

/** Invite links: owners and admins share one link per league; anyone holding it can join as a manager. */
@Service
@Transactional(readOnly = true)
class InviteService {

	private final LeagueService leagueService;
	private final LeagueRepository leagues;
	private final LeagueMembershipRepository memberships;
	private final LeagueInviteRepository invites;

	InviteService(LeagueService leagueService, LeagueRepository leagues, LeagueMembershipRepository memberships,
			LeagueInviteRepository invites) {
		this.leagueService = leagueService;
		this.leagues = leagues;
		this.memberships = memberships;
		this.invites = invites;
	}

	Optional<String> currentCode(String slug, UUID userId) {
		League league = requireManager(slug, userId);
		return invites.findByLeagueIdAndRevokedAtIsNull(league.getId()).map(LeagueInvite::getCode);
	}

	@Transactional
	String getOrCreate(String slug, UUID userId) {
		League league = lockForInviteChange(slug, userId);
		return invites.findByLeagueIdAndRevokedAtIsNull(league.getId())
			.map(LeagueInvite::getCode)
			.orElseGet(() -> create(league, userId));
	}

	/** Replaces the active link, so the old one stops working. */
	@Transactional
	String rotate(String slug, UUID userId) {
		League league = lockForInviteChange(slug, userId);
		invites.findByLeagueIdAndRevokedAtIsNull(league.getId()).ifPresent(LeagueInvite::revoke);
		// Write the revoke before inserting: only one active link per league is allowed.
		invites.flush();
		return create(league, userId);
	}

	InvitePreview preview(String code, UUID viewerId) {
		League league = activeInviteLeague(code);
		LeagueRole myRole = viewerId == null ? null
				: memberships.findById(new LeagueMembership.Key(league.getId(), viewerId))
					.map(LeagueMembership::getRole)
					.orElse(null);
		return new InvitePreview(league, memberships.countByKeyLeagueId(league.getId()), myRole);
	}

	/** Joins as a manager. Joining a league you're already in keeps your current role. */
	@Transactional
	Joined accept(String code, UUID userId) {
		League league = activeInviteLeague(code);
		memberships.addManagerIfAbsent(league.getId(), userId);
		LeagueRole role = memberships.findById(new LeagueMembership.Key(league.getId(), userId))
			.map(LeagueMembership::getRole)
			.orElseThrow();
		return new Joined(league, role);
	}

	private String create(League league, UUID userId) {
		return invites.save(new LeagueInvite(InviteCodes.next(), league.getId(), userId)).getCode();
	}

	private League lockForInviteChange(String slug, UUID userId) {
		League league = requireManager(slug, userId);
		return leagues.lockById(league.getId()).orElseThrow(LeagueService::notFound);
	}

	private League requireManager(String slug, UUID userId) {
		LeagueService.LeagueDetail detail = leagueService.detail(slug, userId);
		if (detail.myRole() == null || !detail.myRole().canManageLeague()) {
			throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Only the owner or an admin can manage invite links");
		}
		return detail.league();
	}

	private League activeInviteLeague(String code) {
		return invites.findById(code)
			.filter(LeagueInvite::isActive)
			.flatMap(invite -> leagues.findById(invite.getLeagueId()))
			.orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "This invite link isn't valid"));
	}

	record InvitePreview(League league, long memberCount, LeagueRole myRole) {
	}

	record Joined(League league, LeagueRole role) {
	}

}
