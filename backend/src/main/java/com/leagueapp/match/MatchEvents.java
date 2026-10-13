package com.leagueapp.match;

import java.util.List;
import java.util.UUID;

/**
 * What the match module announces to other modules (the league log, notifications). Published inside the
 * transaction that made the change; {@code actorId} is who did it, null when it happened on its own.
 */
public final class MatchEvents {

	private MatchEvents() {
	}

	/** The two sides of a fixture and its score at the time of the event. */
	public record Match(UUID fixtureId, UUID homeId, UUID awayId, int homeGoals, int awayGoals) {

		public boolean involves(UUID userId) {
			return homeId.equals(userId) || awayId.equals(userId);
		}

		public UUID opponentOf(UUID userId) {
			return homeId.equals(userId) ? awayId : homeId;
		}

	}

	public record ResultReported(UUID leagueId, UUID actorId, Match match) {
	}

	/** {@code reporterId} entered the score; {@code actorId} accepted it (the opponent, or an admin). */
	public record ResultConfirmed(UUID leagueId, UUID actorId, UUID reporterId, Match match) {
	}

	public record DisputeOpened(UUID leagueId, UUID actorId, UUID reporterId, Match match) {
	}

	public record DisputeResolved(UUID leagueId, UUID actorId, Match match) {
	}

	public record ResultOverridden(UUID leagueId, UUID actorId, int oldHomeGoals, int oldAwayGoals, String reason,
			Match match) {
	}

	/** {@code forfeiting} is HOME, AWAY or BOTH. */
	public record ForfeitAwarded(UUID leagueId, UUID actorId, String forfeiting, String reason, Match match) {
	}

	public record PointsDeducted(UUID leagueId, UUID actorId, UUID seasonId, UUID deductionId, UUID userId, int points,
			String reason) {
	}

	public record DeductionRevoked(UUID leagueId, UUID actorId, UUID seasonId, UUID deductionId, UUID userId,
			int points, String reason) {
	}

	public record SeasonStarted(UUID leagueId, UUID actorId, UUID seasonId, String name, List<UUID> participants) {
	}

	public record SeasonCompleted(UUID leagueId, UUID seasonId, String name, List<UUID> participants) {
	}

}
