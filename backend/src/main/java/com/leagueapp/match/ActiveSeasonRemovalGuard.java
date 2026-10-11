package com.leagueapp.match;

import java.util.Optional;
import java.util.UUID;

import org.springframework.stereotype.Component;

import com.leagueapp.league.MembershipRemovalGuard;

/** Members playing in the active season stay until it ends, so its fixtures and table stay whole. */
@Component
class ActiveSeasonRemovalGuard implements MembershipRemovalGuard {

	private final SeasonRepository seasons;

	ActiveSeasonRemovalGuard(SeasonRepository seasons) {
		this.seasons = seasons;
	}

	@Override
	public Optional<String> blockReason(UUID leagueId, UUID userId) {
		return seasons.isInActiveSeason(leagueId, userId)
				? Optional.of("They're playing in the current season. You can remove them once it ends.")
				: Optional.empty();
	}

}
