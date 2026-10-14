package com.leagueapp.league;

import java.time.Duration;
import java.time.OffsetDateTime;
import java.util.Collection;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;

import org.springframework.context.ApplicationEventPublisher;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import com.leagueapp.identity.IdentityService;

/**
 * Leagues and who can see them. The public methods (access, memberInfos) are the league module's API for
 * other modules; the rest serves this module's controllers.
 */
@Service
@Transactional(readOnly = true)
public class LeagueService {

	/** How long the owner can restore a deleted league before it is purged. */
	static final Duration RESTORE_WINDOW = Duration.ofDays(7);

	private final LeagueRepository leagues;
	private final LeagueMembershipRepository memberships;
	private final ClubRepository clubs;
	private final IdentityService identity;
	private final ApplicationEventPublisher events;

	LeagueService(LeagueRepository leagues, LeagueMembershipRepository memberships, ClubRepository clubs,
			IdentityService identity, ApplicationEventPublisher events) {
		this.leagues = leagues;
		this.memberships = memberships;
		this.clubs = clubs;
		this.identity = identity;
		this.events = events;
	}

	public List<League> publicLeagues() {
		return leagues.findByVisibilityAndDeletedAtIsNullAndHiddenAtIsNullOrderByCreatedAtDesc(League.Visibility.PUBLIC);
	}

	/** Hidden by moderators: nothing is announced about it (notifications, Discord). */
	public boolean isHidden(UUID leagueId) {
		return leagues.findById(leagueId).map(League::isHidden).orElse(false);
	}

	@Transactional
	public void hide(UUID leagueId) {
		leagues.findById(leagueId).orElseThrow(LeagueService::notFound).hide();
	}

	@Transactional
	public void unhide(UUID leagueId) {
		leagues.findById(leagueId).orElseThrow(LeagueService::notFound).unhide();
	}

	/** The league as this viewer may see it (404 for a private league they aren't in), with their role. */
	public LeagueAccess access(String slug, UUID viewerId) {
		LeagueDetail detail = detail(slug, viewerId);
		League league = detail.league();
		return new LeagueAccess(league.getId(), league.getSlug(), league.getName(), detail.myRole());
	}

	/** Slug and name of these leagues, for links (no visibility check: callers only pass leagues the user is in). */
	public Map<UUID, LeagueRef> refs(Collection<UUID> leagueIds) {
		return leagues.findAllById(leagueIds).stream()
			.collect(Collectors.toMap(League::getId, l -> new LeagueRef(l.getId(), l.getSlug(), l.getName())));
	}

	/** Every member with their name, club and role: owner first, then admins, then managers by join date. */
	public List<MemberInfo> memberInfos(UUID leagueId) {
		List<LeagueMembership> all = memberships.findByKeyLeagueId(leagueId);
		Map<UUID, String> names = identity.displayNames(all.stream().map(LeagueMembership::getUserId).toList());
		Map<UUID, String> clubNames = clubs.findByKeyLeagueId(leagueId).stream()
			.collect(Collectors.toMap(Club::getUserId, Club::getName));
		return all.stream()
			.sorted(Comparator.comparing(LeagueMembership::getRole).thenComparing(LeagueMembership::getJoinedAt))
			.map(m -> new MemberInfo(m.getUserId(), names.getOrDefault(m.getUserId(), "Player"),
					clubNames.get(m.getUserId()), m.getRole(), m.getJoinedAt()))
			.toList();
	}

	@Transactional
	League create(UUID ownerId, String name, League.Visibility visibility) {
		String slug = SlugGenerator.uniqueSlug(name, leagues::existsBySlug);
		League league = leagues.save(new League(name.strip(), slug, ownerId, visibility));
		memberships.save(new LeagueMembership(league.getId(), ownerId, LeagueRole.OWNER));
		return league;
	}

	/** Owner only. The slug stays the same, so existing links keep working. */
	@Transactional
	League update(String slug, UUID actorId, String name, League.Visibility visibility) {
		LeagueDetail detail = detail(slug, actorId);
		if (detail.myRole() != LeagueRole.OWNER) {
			throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Only the owner can change league settings");
		}
		League league = detail.league();
		league.rename(name.strip());
		league.changeVisibility(visibility);
		return league;
	}

	/** Owner only, confirmed by typing the name. Members are told; the owner can restore it for 7 days. */
	@Transactional
	void delete(String slug, UUID actorId, String confirmName) {
		League league = requireOwner(slug, actorId);
		if (league.getDeletedAt() != null) {
			throw new ResponseStatusException(HttpStatus.CONFLICT, "This league is already deleted");
		}
		if (!league.getName().equals(confirmName)) {
			throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Type the league name to confirm");
		}
		league.markDeleted();
		events.publishEvent(new LeagueEvents.LeagueDeleted(league.getId(), actorId));
	}

	@Transactional
	void restore(String slug, UUID actorId) {
		League league = requireOwner(slug, actorId);
		if (league.getDeletedAt() == null) {
			throw new ResponseStatusException(HttpStatus.CONFLICT, "This league isn't deleted");
		}
		if (OffsetDateTime.now().isAfter(league.getRestoreUntil())) {
			throw new ResponseStatusException(HttpStatus.CONFLICT, "This league can no longer be restored");
		}
		league.restore();
	}

	private League requireOwner(String slug, UUID actorId) {
		LeagueDetail detail = detail(slug, actorId);
		if (detail.myRole() != LeagueRole.OWNER) {
			throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Only the owner can do that");
		}
		return detail.league();
	}

	List<MyLeague> leaguesOf(UUID userId) {
		Map<UUID, LeagueRole> roles = memberships.findByKeyUserId(userId).stream()
			.collect(Collectors.toMap(LeagueMembership::getLeagueId, LeagueMembership::getRole));
		return leagues.findAllById(roles.keySet()).stream()
			.sorted(Comparator.comparing(League::getCreatedAt).reversed())
			.map(league -> new MyLeague(league, roles.get(league.getId())))
			.toList();
	}

	/**
	 * A league is visible if it is public or the viewer is a member; otherwise it looks like it doesn't exist.
	 * A deleted or hidden league is visible only to its owner.
	 */
	LeagueDetail detail(String slug, UUID viewerId) {
		League league = leagues.findBySlug(slug).orElseThrow(LeagueService::notFound);
		LeagueRole role = viewerId == null ? null
				: memberships.findById(new LeagueMembership.Key(league.getId(), viewerId))
					.map(LeagueMembership::getRole)
					.orElse(null);
		if (league.getVisibility() == League.Visibility.PRIVATE && role == null) {
			throw notFound();
		}
		if (league.isRestricted() && role != LeagueRole.OWNER) {
			throw notFound();
		}
		return new LeagueDetail(league, role, memberships.countByKeyLeagueId(league.getId()));
	}

	/** The member list, for members only. */
	List<MemberInfo> members(String slug, UUID viewerId) {
		LeagueDetail detail = detail(slug, viewerId);
		if (detail.myRole() == null) {
			throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Only members can see who is in this league");
		}
		return memberInfos(detail.league().getId());
	}

	static ResponseStatusException notFound() {
		return new ResponseStatusException(HttpStatus.NOT_FOUND, "League not found");
	}

	public record LeagueAccess(UUID leagueId, String slug, String name, LeagueRole viewerRole) {

		public boolean isMember() {
			return viewerRole != null;
		}

		public boolean canManage() {
			return viewerRole != null && viewerRole.canManageLeague();
		}

	}

	public record LeagueRef(UUID id, String slug, String name) {
	}

	public record MemberInfo(UUID userId, String displayName, String clubName, LeagueRole role,
			OffsetDateTime joinedAt) {

		/** What fixtures and tables show: the club name, or the member's name if they haven't set one. */
		public String teamName() {
			return clubName != null ? clubName : displayName;
		}

	}

	record MyLeague(League league, LeagueRole role) {
	}

	record LeagueDetail(League league, LeagueRole myRole, long memberCount) {
	}

}
