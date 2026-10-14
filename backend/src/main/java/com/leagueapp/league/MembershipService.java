package com.leagueapp.league;

import java.util.List;
import java.util.UUID;

import org.springframework.context.ApplicationEventPublisher;
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
	private final ApplicationEventPublisher events;

	MembershipService(LeagueService leagueService, LeagueMembershipRepository memberships, ClubRepository clubs,
			List<MembershipRemovalGuard> removalGuards, ApplicationEventPublisher events) {
		this.leagueService = leagueService;
		this.memberships = memberships;
		this.clubs = clubs;
		this.removalGuards = removalGuards;
		this.events = events;
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
		checkRemovalGuards(league, targetId);
		clubs.deleteById(new LeagueMembership.Key(league.getId(), targetId));
		memberships.delete(target);
	}

	/** Any member but the owner, who must hand the league over (or delete it) first. */
	void leave(String slug, UUID userId) {
		LeagueService.LeagueDetail detail = leagueService.detail(slug, userId);
		if (detail.myRole() == null) {
			throw LeagueService.notFound();
		}
		if (detail.myRole() == LeagueRole.OWNER) {
			throw new ResponseStatusException(HttpStatus.CONFLICT,
					"Transfer ownership or delete the league before leaving");
		}
		League league = detail.league();
		checkRemovalGuards(league, userId);
		clubs.deleteById(new LeagueMembership.Key(league.getId(), userId));
		memberships.delete(requireMember(league, userId));
	}

	/** Owner only. The new owner takes over at once; the old owner stays on as an admin. */
	void transferOwnership(String slug, UUID actorId, UUID newOwnerId) {
		League league = requireOwner(slug, actorId);
		if (actorId.equals(newOwnerId)) {
			throw badRequest("You already own this league");
		}
		LeagueMembership target = requireMember(league, newOwnerId);
		requireMember(league, actorId).changeRole(LeagueRole.ADMIN);
		target.changeRole(LeagueRole.OWNER);
		league.transferTo(newOwnerId);
		events.publishEvent(new LeagueEvents.OwnershipTransferred(league.getId(), actorId, newOwnerId));
	}

	private void checkRemovalGuards(League league, UUID userId) {
		for (MembershipRemovalGuard guard : removalGuards) {
			guard.blockReason(league.getId(), userId).ifPresent(reason -> {
				throw new ResponseStatusException(HttpStatus.CONFLICT, reason);
			});
		}
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
