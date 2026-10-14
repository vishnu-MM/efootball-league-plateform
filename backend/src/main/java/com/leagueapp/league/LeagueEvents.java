package com.leagueapp.league;

import java.util.UUID;

/** Things that happen to a league as a whole. Published from the league module's services. */
public final class LeagueEvents {

	private LeagueEvents() {
	}

	public record OwnershipTransferred(UUID leagueId, UUID actorId, UUID newOwnerId) {
	}

}
