package com.leagueapp.roster;

import java.util.UUID;

/**
 * What the roster module announces to other modules. Published inside the transaction that made the change;
 * {@code actorId} is who did it, null when it happened on its own (the window opening as a season ends).
 */
public final class RosterEvents {

	private RosterEvents() {
	}

	/** A manager added a card to their squad; it waits for approval. */
	public record CardSubmitted(UUID leagueId, UUID actorId, UUID squadCardId, String playerName) {
	}

	/** An owner or admin approved or rejected {@code userId}'s card; {@code reason} only for rejections. */
	public record CardDecided(UUID leagueId, UUID actorId, UUID squadCardId, UUID userId, String playerName,
			boolean approved, String reason) {
	}

	public record SquadRulesChanged(UUID leagueId, UUID actorId) {
	}

	public record TransferWindowChanged(UUID leagueId, UUID actorId, boolean open) {
	}

}
