package com.leagueapp.activity;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Stream;

import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

import com.leagueapp.identity.IdentityService;
import com.leagueapp.league.LeagueService;
import com.leagueapp.league.LeagueService.MemberInfo;
import com.leagueapp.match.MatchEvents;
import com.leagueapp.roster.RosterEvents;

/**
 * Writes the league log. Listens synchronously, inside the transaction that made the change, so an entry exists
 * exactly when its action does.
 */
@Component
class ActivityRecorder {

	private final ActivityRepository activities;
	private final LeagueService leagueService;
	private final IdentityService identityService;

	ActivityRecorder(ActivityRepository activities, LeagueService leagueService, IdentityService identityService) {
		this.activities = activities;
		this.leagueService = leagueService;
		this.identityService = identityService;
	}

	@EventListener
	void on(MatchEvents.DisputeResolved e) {
		Names n = names(e.leagueId(), e.actorId(), e.match().homeId(), e.match().awayId());
		record(e.leagueId(), e.actorId(), LeagueActivity.Type.DISPUTE_RESOLVED,
				n.person(e.actorId()) + " settled the dispute: " + score(n, e.match()), null, e.match().fixtureId(), null);
	}

	@EventListener
	void on(MatchEvents.ResultOverridden e) {
		Names n = names(e.leagueId(), e.actorId(), e.match().homeId(), e.match().awayId());
		record(e.leagueId(), e.actorId(), LeagueActivity.Type.RESULT_OVERRIDDEN,
				n.person(e.actorId()) + " changed " + n.team(e.match().homeId()) + " " + e.oldHomeGoals() + "–"
						+ e.oldAwayGoals() + " " + n.team(e.match().awayId()) + " to " + e.match().homeGoals() + "–"
						+ e.match().awayGoals(),
				e.reason(), e.match().fixtureId(), null);
	}

	@EventListener
	void on(MatchEvents.ForfeitAwarded e) {
		Names n = names(e.leagueId(), e.actorId(), e.match().homeId(), e.match().awayId());
		String what = switch (e.forfeiting()) {
			case "BOTH" -> "a double forfeit";
			case "HOME" -> "a forfeit by " + n.team(e.match().homeId());
			default -> "a forfeit by " + n.team(e.match().awayId());
		};
		record(e.leagueId(), e.actorId(), LeagueActivity.Type.FORFEIT,
				n.person(e.actorId()) + " recorded " + what + ": " + score(n, e.match()), e.reason(),
				e.match().fixtureId(), null);
	}

	@EventListener
	void on(MatchEvents.PointsDeducted e) {
		Names n = names(e.leagueId(), e.actorId(), e.userId());
		record(e.leagueId(), e.actorId(), LeagueActivity.Type.POINTS_DEDUCTED,
				n.person(e.actorId()) + " deducted " + points(e.points()) + " from " + n.team(e.userId()), e.reason(),
				null, e.userId());
	}

	@EventListener
	void on(MatchEvents.DeductionRevoked e) {
		Names n = names(e.leagueId(), e.actorId(), e.userId());
		record(e.leagueId(), e.actorId(), LeagueActivity.Type.DEDUCTION_REVOKED,
				n.person(e.actorId()) + " gave " + n.team(e.userId()) + " back " + points(e.points()), e.reason(), null,
				e.userId());
	}

	@EventListener
	void on(MatchEvents.SeasonStarted e) {
		Names n = names(e.leagueId(), e.actorId());
		record(e.leagueId(), e.actorId(), LeagueActivity.Type.SEASON_STARTED,
				n.person(e.actorId()) + " started " + e.name() + " with " + e.participants().size() + " players", null,
				null, null);
	}

	@EventListener
	void on(MatchEvents.SeasonCompleted e) {
		record(e.leagueId(), null, LeagueActivity.Type.SEASON_COMPLETED, e.name() + " is complete", null, null, null);
	}

	@EventListener
	void on(RosterEvents.CardDecided e) {
		Names n = names(e.leagueId(), e.actorId(), e.userId());
		record(e.leagueId(), e.actorId(), e.approved() ? LeagueActivity.Type.CARD_APPROVED : LeagueActivity.Type.CARD_REJECTED,
				n.person(e.actorId()) + (e.approved() ? " approved " : " rejected ") + e.playerName() + " for "
						+ n.team(e.userId()),
				e.reason(), null, e.userId());
	}

	@EventListener
	void on(RosterEvents.SquadRulesChanged e) {
		Names n = names(e.leagueId(), e.actorId());
		record(e.leagueId(), e.actorId(), LeagueActivity.Type.SQUAD_RULES_CHANGED,
				n.person(e.actorId()) + " changed the squad rules", null, null, null);
	}

	/** Only an admin opening or closing it; the automatic changes come with a season starting or ending. */
	@EventListener
	void on(RosterEvents.TransferWindowChanged e) {
		if (e.actorId() == null) {
			return;
		}
		Names n = names(e.leagueId(), e.actorId());
		record(e.leagueId(), e.actorId(), e.open() ? LeagueActivity.Type.WINDOW_OPENED : LeagueActivity.Type.WINDOW_CLOSED,
				n.person(e.actorId()) + (e.open() ? " opened" : " closed") + " the transfer window", null, null, null);
	}

	private void record(UUID leagueId, UUID actorId, LeagueActivity.Type type, String summary, String reason,
			UUID fixtureId, UUID subjectUserId) {
		activities.save(new LeagueActivity(leagueId, actorId, type, summary, reason, fixtureId, subjectUserId));
	}

	private static String score(Names n, MatchEvents.Match m) {
		return n.team(m.homeId()) + " " + m.homeGoals() + "–" + m.awayGoals() + " " + n.team(m.awayId());
	}

	private static String points(int points) {
		return points == 1 ? "1 point" : points + " points";
	}

	/** People by display name, teams by club name; ex-members by their display name. */
	private Names names(UUID leagueId, UUID... userIds) {
		Map<UUID, String> people = new HashMap<>();
		Map<UUID, String> teams = new HashMap<>();
		for (MemberInfo m : leagueService.memberInfos(leagueId)) {
			people.put(m.userId(), m.displayName());
			teams.put(m.userId(), m.teamName());
		}
		List<UUID> missing = Stream.of(userIds).filter(id -> id != null && !people.containsKey(id)).toList();
		if (!missing.isEmpty()) {
			identityService.displayNames(missing).forEach((id, name) -> {
				people.put(id, name);
				teams.put(id, name);
			});
		}
		return new Names(people, teams);
	}

	private record Names(Map<UUID, String> people, Map<UUID, String> teams) {

		String person(UUID id) {
			return people.getOrDefault(id, "Someone");
		}

		String team(UUID id) {
			return teams.getOrDefault(id, "a player");
		}

	}

}
