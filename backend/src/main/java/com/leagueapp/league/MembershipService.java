package com.leagueapp.league;

import java.util.List;
import java.util.UUID;

import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

/** Changing who is in a league and how: roles, removal, and each member's club name. */
@Service
@Transactional
class MembershipService {

	private final LeagueService leagueService;
	private final LeagueMembershipRepository memberships;
	private final ClubRepository clubs;
	private final List<MembershipRemovalGuard> removalGuards;

	MembershipService(LeagueService leagueService, LeagueMembershipRepository memberships, ClubRepository clubs,
			List<MembershipRemovalGuard> removalGuards) {
		this.leagueService = leagueService;
		this.memberships = memberships;
		this.clubs = clubs;
		this.removalGuards = removalGuards;
	}

	/** Owner only. Members can be made admins or managers; the owner's own role never changes. */
	void changeRole(String slug, UUID actorId, UUID targetId, LeagueRole newRole) {
		League league = requireOwner(slug, actorId);
		if (newRole == LeagueRole.OWNER) {
			throw badRequest("A league has one owner. Choose admin or manager.");
		}
		LeagueMembership target = requireMember(league, targetId);
		if (target.getRole() == LeagueRole.OWNER) {
			throw badRequest("The owner's role can't be changed");
		}
		target.changeRole(newRole);
	}

	/** Owner only. Not the owner themself, and not while another module (e.g. an active season) needs them. */
	void remove(String slug, UUID actorId, UUID targetId) {
		League league = requireOwner(slug, actorId);
		LeagueMembership target = requireMember(league, targetId);
		if (target.getRole() == LeagueRole.OWNER) {
			throw badRequest("The owner can't be removed");
		}
		for (MembershipRemovalGuard guard : removalGuards) {
			guard.blockReason(league.getId(), targetId).ifPresent(reason -> {
				throw new ResponseStatusException(HttpStatus.CONFLICT, reason);
			});
		}
		clubs.deleteById(new LeagueMembership.Key(league.getId(), targetId));
		memberships.delete(target);
	}

	/** Any member can name their own club. Names are unique in the league, ignoring case. */
	String setClubName(String slug, UUID userId, String rawName) {
		LeagueService.LeagueDetail detail = leagueService.detail(slug, userId);
		if (detail.myRole() == null) {
			throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Only members can have a club in this league");
		}
		String name = rawName.strip().replaceAll("\\s+", " ");
		if (name.length() < 2) {
			throw badRequest("Use at least 2 characters for the club name");
		}
		UUID leagueId = detail.league().getId();
		if (clubs.nameTakenByOther(leagueId, name, userId)) {
			throw new ResponseStatusException(HttpStatus.CONFLICT, "Another club in this league already has that name");
		}
		clubs.findById(new LeagueMembership.Key(leagueId, userId))
			.ifPresentOrElse(club -> club.rename(name), () -> clubs.save(new Club(leagueId, userId, name)));
		return name;
	}

	private League requireOwner(String slug, UUID actorId) {
		LeagueService.LeagueDetail detail = leagueService.detail(slug, actorId);
		if (detail.myRole() != LeagueRole.OWNER) {
			throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Only the owner can change members");
		}
		return detail.league();
	}

	private LeagueMembership requireMember(League league, UUID userId) {
		return memberships.findById(new LeagueMembership.Key(league.getId(), userId))
			.orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "That person isn't in this league"));
	}

	private static ResponseStatusException badRequest(String message) {
		return new ResponseStatusException(HttpStatus.BAD_REQUEST, message);
	}

}
