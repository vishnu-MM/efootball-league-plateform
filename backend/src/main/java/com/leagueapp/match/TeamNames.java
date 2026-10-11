package com.leagueapp.match;

import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import org.springframework.stereotype.Component;

import com.leagueapp.identity.IdentityService;
import com.leagueapp.league.LeagueService;

/** Names shown for players in fixtures and tables: club name, else display name (also for ex-members). */
@Component
class TeamNames {

	private final LeagueService leagueService;
	private final IdentityService identityService;

	TeamNames(LeagueService leagueService, IdentityService identityService) {
		this.leagueService = leagueService;
		this.identityService = identityService;
	}

	Map<UUID, String> of(UUID leagueId, Collection<UUID> userIds) {
		Map<UUID, String> names = new HashMap<>();
		leagueService.memberInfos(leagueId).forEach(m -> names.put(m.userId(), m.teamName()));
		Set<UUID> missing = new HashSet<>(userIds);
		missing.removeAll(names.keySet());
		if (!missing.isEmpty()) {
			names.putAll(identityService.displayNames(missing));
		}
		return names;
	}

}
