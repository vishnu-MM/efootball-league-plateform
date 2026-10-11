package com.leagueapp.match;

import java.time.LocalDate;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * A fixture as lists show it. The score is the final one when confirmed, the reported one while awaiting
 * confirmation, and empty otherwise; {@code actions} are what this viewer may do now.
 */
record FixtureSummary(UUID id, int round, LocalDate playBy, Fixture.Status status, Side home, Side away,
		Integer homeGoals, Integer awayGoals, UUID reportedBy, boolean decidedByAdmin, Set<ResultRules.Action> actions) {

	static FixtureSummary of(Fixture f, Map<UUID, String> names, ScoreReport pendingReport, UUID viewerId,
			boolean viewerCanManage) {
		Integer home = f.getHomeGoals();
		Integer away = f.getAwayGoals();
		UUID reporter = null;
		if (pendingReport != null && f.getStatus() == Fixture.Status.AWAITING_CONFIRMATION) {
			home = pendingReport.getHomeGoals();
			away = pendingReport.getAwayGoals();
			reporter = pendingReport.getReportedBy();
		}
		return new FixtureSummary(f.getId(), f.getRound(), f.getPlayBy(), f.getStatus(),
				new Side(f.getHomeUserId(), names.getOrDefault(f.getHomeUserId(), "Player")),
				new Side(f.getAwayUserId(), names.getOrDefault(f.getAwayUserId(), "Player")), home, away, reporter,
				f.getResolvedBy() != null, ResultRules.allowed(f.getStatus(), viewerId, f.getHomeUserId(),
						f.getAwayUserId(), reporter, viewerCanManage));
	}

	record Side(UUID userId, String name) {
	}

}
