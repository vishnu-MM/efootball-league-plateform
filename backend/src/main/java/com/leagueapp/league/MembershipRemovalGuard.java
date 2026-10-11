package com.leagueapp.league;

import java.util.Optional;
import java.util.UUID;

/**
 * Lets other modules veto removing a member (e.g. the match module, while they play in the active season)
 * without the league module depending on them.
 */
public interface MembershipRemovalGuard {

	/** A reason the member can't be removed right now, or empty if removal is fine. */
	Optional<String> blockReason(UUID leagueId, UUID userId);

}
