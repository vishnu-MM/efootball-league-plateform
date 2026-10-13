package com.leagueapp.discord;

import java.util.Map;
import java.util.UUID;

import com.leagueapp.match.MatchEvents;
import com.leagueapp.roster.RosterEvents;

/** What each league event looks like in Discord: plain Java so it can be tested without a server. */
final class DiscordMessages {

	static final int GREEN = 0x5FB98A;
	static final int CORAL = 0xF2765F;
	static final int VIOLET = 0x7468F2;
	static final int MUSTARD = 0xE9B949;

	private DiscordMessages() {
	}

	/** An embed: title, optional description and link, a colour, and the league name underneath. */
	record Message(String title, String description, String url, int color, String footer) {
	}

	/** Names and links for one league. */
	record Context(String leagueName, String leagueUrl, Map<UUID, String> teams) {

		String team(UUID id) {
			return teams.getOrDefault(id, "A player");
		}

		String score(MatchEvents.Match m) {
			return team(m.homeId()) + " " + m.homeGoals() + "–" + m.awayGoals() + " " + team(m.awayId());
		}

		String fixtureUrl(MatchEvents.Match m) {
			return leagueUrl + "/fixtures/" + m.fixtureId();
		}

	}

	static Message resultConfirmed(Context c, MatchEvents.ResultConfirmed e) {
		return new Message("Result: " + c.score(e.match()), null, c.fixtureUrl(e.match()), GREEN, c.leagueName());
	}

	static Message disputeOpened(Context c, MatchEvents.DisputeOpened e) {
		MatchEvents.Match m = e.match();
		return new Message("Dispute: " + c.team(m.homeId()) + " v " + c.team(m.awayId()),
				c.team(e.actorId()) + " disputed the reported score. An admin will decide.", c.fixtureUrl(m), CORAL,
				c.leagueName());
	}

	static Message disputeResolved(Context c, MatchEvents.DisputeResolved e) {
		return new Message("Dispute settled: " + c.score(e.match()), null, c.fixtureUrl(e.match()), GREEN, c.leagueName());
	}

	static Message resultOverridden(Context c, MatchEvents.ResultOverridden e) {
		return new Message("Result changed: " + c.score(e.match()) + " (was " + e.oldHomeGoals() + "–" + e.oldAwayGoals() + ")",
				"Reason: " + e.reason(), c.fixtureUrl(e.match()), VIOLET, c.leagueName());
	}

	static Message forfeitAwarded(Context c, MatchEvents.ForfeitAwarded e) {
		MatchEvents.Match m = e.match();
		String who = switch (e.forfeiting()) {
			case "BOTH" -> "Both sides forfeited.";
			case "HOME" -> c.team(m.homeId()) + " forfeited.";
			default -> c.team(m.awayId()) + " forfeited.";
		};
		return new Message("Forfeit: " + c.score(m), who + " Reason: " + e.reason(), c.fixtureUrl(m), VIOLET, c.leagueName());
	}

	static Message pointsDeducted(Context c, MatchEvents.PointsDeducted e) {
		return new Message("−" + points(e.points()) + ": " + c.team(e.userId()), "Reason: " + e.reason(), c.leagueUrl(),
				CORAL, c.leagueName());
	}

	static Message deductionRevoked(Context c, MatchEvents.DeductionRevoked e) {
		return new Message(points(e.points()) + " given back: " + c.team(e.userId()), "Reason: " + e.reason(),
				c.leagueUrl(), GREEN, c.leagueName());
	}

	static Message seasonStarted(Context c, MatchEvents.SeasonStarted e) {
		return new Message(e.name() + " has started", e.participants().size() + " teams. Good luck!", c.leagueUrl(),
				MUSTARD, c.leagueName());
	}

	static Message seasonCompleted(Context c, MatchEvents.SeasonCompleted e) {
		return new Message(e.name() + " is complete", "Every match is final. See the table.", c.leagueUrl(), MUSTARD,
				c.leagueName());
	}

	static Message windowChanged(Context c, RosterEvents.TransferWindowChanged e) {
		return new Message(e.open() ? "Transfer window open" : "Transfer window closed",
				e.open() ? "Squads can change." : "Squads are fixed.", c.leagueUrl(), MUSTARD, c.leagueName());
	}

	static Message test(Context c) {
		return new Message("Discord is connected", "League updates will be posted to this channel.", c.leagueUrl(),
				GREEN, c.leagueName());
	}

	private static String points(int points) {
		return points == 1 ? "1 point" : points + " points";
	}

}
