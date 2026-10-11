package com.leagueapp.match;

import java.util.EnumSet;
import java.util.Set;
import java.util.UUID;

/**
 * Who may do what to a fixture, by state.
 * <ul>
 * <li>Scheduled: either player reports the score.</li>
 * <li>Awaiting confirmation: the opponent confirms or disputes; an owner or admin (other than the reporter) can
 * confirm on the opponent's behalf. Nothing is ever confirmed automatically.</li>
 * <li>Disputed: an owner or admin resolves it.</li>
 * <li>Confirmed: final.</li>
 * </ul>
 */
final class ResultRules {

	private ResultRules() {
	}

	static Set<Action> allowed(Fixture.Status status, UUID viewerId, UUID homeId, UUID awayId, UUID reporterId,
			boolean viewerCanManage) {
		Set<Action> actions = EnumSet.noneOf(Action.class);
		if (viewerId == null) {
			return actions;
		}
		boolean isPlayer = viewerId.equals(homeId) || viewerId.equals(awayId);
		boolean isReporter = viewerId.equals(reporterId);

		switch (status) {
			case SCHEDULED -> {
				if (isPlayer) {
					actions.add(Action.REPORT);
				}
			}
			case AWAITING_CONFIRMATION -> {
				if (isPlayer && !isReporter) {
					actions.add(Action.CONFIRM);
					actions.add(Action.DISPUTE);
				}
				else if (viewerCanManage && !isReporter) {
					actions.add(Action.CONFIRM);
				}
			}
			case DISPUTED -> {
				if (viewerCanManage) {
					actions.add(Action.RESOLVE);
				}
			}
			case CONFIRMED -> {
			}
		}
		return actions;
	}

	enum Action {
		REPORT, CONFIRM, DISPUTE, RESOLVE
	}

}
