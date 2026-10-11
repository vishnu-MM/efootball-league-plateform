package com.leagueapp.league;

import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;

import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

@Service
@Transactional(readOnly = true)
public class LeagueService {

	private final LeagueRepository leagues;
	private final LeagueMembershipRepository memberships;

	LeagueService(LeagueRepository leagues, LeagueMembershipRepository memberships) {
		this.leagues = leagues;
		this.memberships = memberships;
	}

	public List<League> publicLeagues() {
		return leagues.findByVisibilityOrderByCreatedAtDesc(League.Visibility.PUBLIC);
	}

	@Transactional
	League create(UUID ownerId, String name, League.Visibility visibility) {
		String slug = SlugGenerator.uniqueSlug(name, leagues::existsBySlug);
		League league = leagues.save(new League(name.strip(), slug, ownerId, visibility));
		memberships.save(new LeagueMembership(league.getId(), ownerId, LeagueMembership.Role.OWNER));
		return league;
	}

	List<MyLeague> leaguesOf(UUID userId) {
		Map<UUID, LeagueMembership.Role> roles = memberships.findByKeyUserId(userId).stream()
			.collect(Collectors.toMap(LeagueMembership::getLeagueId, LeagueMembership::getRole));
		return leagues.findAllById(roles.keySet()).stream()
			.sorted(Comparator.comparing(League::getCreatedAt).reversed())
			.map(league -> new MyLeague(league, roles.get(league.getId())))
			.toList();
	}

	/** A league is visible if it is public or the viewer is a member; otherwise it looks like it doesn't exist. */
	LeagueDetail detail(String slug, UUID viewerId) {
		League league = leagues.findBySlug(slug).orElseThrow(LeagueService::notFound);
		LeagueMembership.Role role = viewerId == null ? null
				: memberships.findById(new LeagueMembership.Key(league.getId(), viewerId))
					.map(LeagueMembership::getRole)
					.orElse(null);
		if (league.getVisibility() == League.Visibility.PRIVATE && role == null) {
			throw notFound();
		}
		return new LeagueDetail(league, role, memberships.countByKeyLeagueId(league.getId()));
	}

	private static ResponseStatusException notFound() {
		return new ResponseStatusException(HttpStatus.NOT_FOUND, "League not found");
	}

	record MyLeague(League league, LeagueMembership.Role role) {
	}

	record LeagueDetail(League league, LeagueMembership.Role myRole, long memberCount) {
	}

}
