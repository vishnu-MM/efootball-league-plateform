package com.leagueapp.notify;

import java.util.Collection;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Stream;

import org.springframework.modulith.events.ApplicationModuleListener;
import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import com.leagueapp.identity.IdentityService;
import com.leagueapp.league.LeagueEvents;
import com.leagueapp.league.LeagueService;
import com.leagueapp.league.LeagueService.LeagueRef;
import com.leagueapp.league.LeagueService.MemberInfo;
import com.leagueapp.match.MatchEvents;
import com.leagueapp.roster.RosterEvents;

/**
 * Turns league events into notifications for the people they concern, and live updates for everyone watching the
 * league. Runs after the change has committed (through Modulith's event publication registry, so a failure is
 * retried rather than lost); never notifies people about their own actions.
 */
@Component
class Notifier {

	private final NotificationRepository notifications;
	private final LiveUpdates live;
	private final LeagueService leagueService;
	private final IdentityService identityService;

	Notifier(NotificationRepository notifications, LiveUpdates live, LeagueService leagueService,
			IdentityService identityService) {
		this.notifications = notifications;
		this.live = live;
		this.leagueService = leagueService;
		this.identityService = identityService;
	}

	@ApplicationModuleListener
	void on(MatchEvents.ResultReported e) {
		League l = league(e.leagueId());
		UUID opponent = e.match().opponentOf(e.actorId());
		l.send(e.actorId(), "RESULT_REPORTED", List.of(opponent),
				l.team(e.actorId()) + " reported " + l.score(e.match()) + ". Confirm it or dispute it.",
				l.fixture(e.match()));
	}

	@ApplicationModuleListener
	void on(MatchEvents.ResultConfirmed e) {
		League l = league(e.leagueId());
		l.send(e.actorId(), "RESULT_CONFIRMED", List.of(e.reporterId()), l.score(e.match()) + " is confirmed.",
				l.fixture(e.match()));
	}

	@ApplicationModuleListener
	void on(MatchEvents.DisputeOpened e) {
		League l = league(e.leagueId());
		l.send(e.actorId(), "DISPUTE_OPENED", union(List.of(e.reporterId()), l.managers()),
				l.team(e.actorId()) + " disputed the score of " + l.fixtureName(e.match()) + ".", l.fixture(e.match()));
	}

	@ApplicationModuleListener
	void on(MatchEvents.DisputeResolved e) {
		League l = league(e.leagueId());
		l.send(e.actorId(), "DISPUTE_RESOLVED", l.players(e.match()), "An admin settled the dispute: " + l.score(e.match()) + ".",
				l.fixture(e.match()));
	}

	@ApplicationModuleListener
	void on(MatchEvents.ResultOverridden e) {
		League l = league(e.leagueId());
		l.send(e.actorId(), "RESULT_OVERRIDDEN", l.players(e.match()),
				"An admin changed the result to " + l.score(e.match()) + ": " + e.reason(), l.fixture(e.match()));
	}

	@ApplicationModuleListener
	void on(MatchEvents.ForfeitAwarded e) {
		League l = league(e.leagueId());
		l.send(e.actorId(), "FORFEIT", l.players(e.match()),
				"An admin recorded a forfeit: " + l.score(e.match()) + ". " + e.reason(), l.fixture(e.match()));
	}

	@ApplicationModuleListener
	void on(MatchEvents.PointsDeducted e) {
		League l = league(e.leagueId());
		l.send(e.actorId(), "POINTS_DEDUCTED", List.of(e.userId()),
				points(e.points()) + " deducted from your team: " + e.reason(), l.home());
	}

	@ApplicationModuleListener
	void on(MatchEvents.DeductionRevoked e) {
		League l = league(e.leagueId());
		l.send(e.actorId(), "DEDUCTION_REVOKED", List.of(e.userId()),
				"You got " + points(e.points()) + " back: " + e.reason(), l.home());
	}

	@ApplicationModuleListener
	void on(MatchEvents.SeasonStarted e) {
		League l = league(e.leagueId());
		l.send(e.actorId(), "SEASON_STARTED", e.participants(), e.name() + " has started. Your fixtures are ready.", l.home());
	}

	@ApplicationModuleListener
	void on(MatchEvents.SeasonCompleted e) {
		League l = league(e.leagueId());
		l.send(null, "SEASON_COMPLETED", e.participants(), e.name() + " is complete. See the final table.", l.home());
	}

	@ApplicationModuleListener
	void on(RosterEvents.CardSubmitted e) {
		League l = league(e.leagueId());
		l.send(e.actorId(), "CARD_SUBMITTED", l.managers(),
				l.team(e.actorId()) + " added " + e.playerName() + ". It needs approval.", l.path("/squad/approvals"));
	}

	@ApplicationModuleListener
	void on(RosterEvents.CardDecided e) {
		League l = league(e.leagueId());
		String text = e.approved() ? e.playerName() + " was approved for your squad."
				: e.playerName() + " was rejected" + (e.reason() == null ? "." : ": " + e.reason());
		l.send(e.actorId(), e.approved() ? "CARD_APPROVED" : "CARD_REJECTED", List.of(e.userId()), text, l.home());
	}

	@ApplicationModuleListener
	void on(RosterEvents.SquadRulesChanged e) {
		league(e.leagueId()).refreshPages();
	}

	/** Only when an admin does it; the automatic changes come with the season started/completed notifications. */
	@ApplicationModuleListener
	void on(RosterEvents.TransferWindowChanged e) {
		League l = league(e.leagueId());
		if (e.actorId() == null) {
			l.refreshPages();
			return;
		}
		l.send(e.actorId(), e.open() ? "WINDOW_OPENED" : "WINDOW_CLOSED", l.members(),
				"The transfer window is " + (e.open() ? "open. Squads can change." : "closed. Squads are fixed."), l.home());
	}

	@ApplicationModuleListener
	void on(LeagueEvents.OwnershipTransferred e) {
		League l = league(e.leagueId());
		l.send(e.actorId(), "OWNERSHIP_TRANSFERRED", List.of(e.newOwnerId()), "You're now the owner of " + l.name() + ".",
				l.home());
	}

	private League league(UUID leagueId) {
		LeagueRef ref = leagueService.refs(List.of(leagueId)).get(leagueId);
		List<MemberInfo> members = leagueService.memberInfos(leagueId);
		return new League(ref, members);
	}

	private static String points(int points) {
		return points == 1 ? "1 point" : points + " points";
	}

	private static List<UUID> union(Collection<UUID> a, Collection<UUID> b) {
		return Stream.concat(a.stream(), b.stream()).distinct().toList();
	}

	/** One league's names and members, and sending to them. */
	private final class League {

		private final LeagueRef ref;

		private final List<MemberInfo> members;

		private final Map<UUID, String> teams = new HashMap<>();

		League(LeagueRef ref, List<MemberInfo> members) {
			this.ref = ref;
			this.members = members;
			members.forEach(m -> teams.put(m.userId(), m.teamName()));
		}

		/** Saves a notification for each recipient other than the actor; after commit, pushes it and a page refresh. */
		void send(UUID actorId, String type, Collection<UUID> to, String text, String link) {
			Set<UUID> recipients = new LinkedHashSet<>(to);
			recipients.remove(null);
			recipients.remove(actorId);
			if (ref == null) {
				return; // the league is gone
			}
			notifications.saveAll(recipients.stream().map(id -> new Notification(id, ref.id(), type, text, link)).toList());
			afterCommit(() -> {
				Map<UUID, Long> unread = new HashMap<>();
				recipients.stream()
					.filter(live::isConnected)
					.forEach(id -> unread.put(id, notifications.countByUserIdAndReadAtIsNull(id)));
				live.notificationsChanged(unread.keySet(), unread);
				live.leagueChanged(members(), ref.slug());
			});
		}

		/** Nobody to notify, but pages showing the league should refresh. */
		void refreshPages() {
			if (ref != null) {
				afterCommit(() -> live.leagueChanged(members(), ref.slug()));
			}
		}

		List<UUID> members() {
			return members.stream().map(MemberInfo::userId).toList();
		}

		List<UUID> managers() {
			return members.stream().filter(m -> m.role().canManageLeague()).map(MemberInfo::userId).toList();
		}

		List<UUID> players(MatchEvents.Match m) {
			return List.of(m.homeId(), m.awayId());
		}

		String team(UUID userId) {
			if (!teams.containsKey(userId) && userId != null) {
				teams.putAll(identityService.displayNames(List.of(userId)));
			}
			return teams.getOrDefault(userId, "A player");
		}

		String score(MatchEvents.Match m) {
			return team(m.homeId()) + " " + m.homeGoals() + "–" + m.awayGoals() + " " + team(m.awayId());
		}

		String fixtureName(MatchEvents.Match m) {
			return team(m.homeId()) + " v " + team(m.awayId());
		}

		String home() {
			return path("");
		}

		String name() {
			return ref == null ? "a league" : ref.name();
		}

		String fixture(MatchEvents.Match m) {
			return path("/fixtures/" + m.fixtureId());
		}

		String path(String rest) {
			return ref == null ? "/" : "/leagues/" + ref.slug() + rest;
		}

	}

	/** Runs once the listener's transaction commits, so a refetch sees the new rows; right away if there is none. */
	private static void afterCommit(Runnable action) {
		if (TransactionSynchronizationManager.isSynchronizationActive()) {
			TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
				@Override
				public void afterCommit() {
					action.run();
				}
			});
		}
		else {
			action.run();
		}
	}

}
