package com.leagueapp.league;

import java.util.UUID;

/** Things that happen to a league as a whole. Published from the league module's services. */
public final class LeagueEvents {

	private LeagueEvents() {
	}

	public record OwnershipTransferred(UUID leagueId, UUID actorId, UUID newOwnerId) {
	}

	/** The owner deleted the league; it can still be restored for 7 days. */
	public record LeagueDeleted(UUID leagueId, UUID actorId) {
	}

	/**
	 * The league is about to be removed for good. Published synchronously inside the purge transaction, so
	 * listeners can clean up what the database cascade can't (e.g. files in storage).
	 */
	public record LeaguePurging(UUID leagueId) {
	}

}
