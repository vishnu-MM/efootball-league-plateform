package com.leagueapp.discord;

import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.modulith.events.ApplicationModuleListener;
import org.springframework.stereotype.Component;

import com.leagueapp.league.LeagueService;
import com.leagueapp.league.LeagueService.LeagueRef;
import com.leagueapp.league.LeagueService.MemberInfo;
import com.leagueapp.match.MatchEvents;
import com.leagueapp.roster.RosterEvents;

/**
 * Posts league events to the league's Discord channel, if it has a webhook and the owner ticked that kind of event.
 * Runs after the change has committed. A failed post is recorded on the webhook (and shown to the owner), never
 * retried, so a channel doesn't get a burst of stale messages later.
 */
@Component
class DiscordPoster {

	private final WebhookRepository webhooks;
	private final DiscordClient client;
	private final LeagueService leagueService;
	private final String publicUrl;

	DiscordPoster(WebhookRepository webhooks, DiscordClient client, LeagueService leagueService,
			@Value("${app.public-url:http://localhost:3000}") String publicUrl) {
		this.webhooks = webhooks;
		this.client = client;
		this.leagueService = leagueService;
		this.publicUrl = publicUrl.replaceAll("/+$", "");
	}

	@ApplicationModuleListener
	void on(MatchEvents.ResultConfirmed e) {
		post(e.leagueId(), DiscordTopic.RESULTS, c -> DiscordMessages.resultConfirmed(c, e));
	}

	@ApplicationModuleListener
	void on(MatchEvents.DisputeOpened e) {
		post(e.leagueId(), DiscordTopic.DISPUTES, c -> DiscordMessages.disputeOpened(c, e));
	}

	@ApplicationModuleListener
	void on(MatchEvents.DisputeResolved e) {
		post(e.leagueId(), DiscordTopic.DISPUTES, c -> DiscordMessages.disputeResolved(c, e));
	}

	@ApplicationModuleListener
	void on(MatchEvents.ResultOverridden e) {
		post(e.leagueId(), DiscordTopic.CORRECTIONS, c -> DiscordMessages.resultOverridden(c, e));
	}

	@ApplicationModuleListener
	void on(MatchEvents.ForfeitAwarded e) {
		post(e.leagueId(), DiscordTopic.CORRECTIONS, c -> DiscordMessages.forfeitAwarded(c, e));
	}

	@ApplicationModuleListener
	void on(MatchEvents.PointsDeducted e) {
		post(e.leagueId(), DiscordTopic.DEDUCTIONS, c -> DiscordMessages.pointsDeducted(c, e));
	}

	@ApplicationModuleListener
	void on(MatchEvents.DeductionRevoked e) {
		post(e.leagueId(), DiscordTopic.DEDUCTIONS, c -> DiscordMessages.deductionRevoked(c, e));
	}

	@ApplicationModuleListener
	void on(MatchEvents.SeasonStarted e) {
		post(e.leagueId(), DiscordTopic.SEASONS, c -> DiscordMessages.seasonStarted(c, e));
	}

	@ApplicationModuleListener
	void on(MatchEvents.SeasonCompleted e) {
		post(e.leagueId(), DiscordTopic.SEASONS, c -> DiscordMessages.seasonCompleted(c, e));
	}

	@ApplicationModuleListener
	void on(RosterEvents.TransferWindowChanged e) {
		post(e.leagueId(), DiscordTopic.TRANSFER_WINDOW, c -> DiscordMessages.windowChanged(c, e));
	}

	/** Sends a test message now and returns whether it worked (recording the error if not). */
	DiscordClient.Result sendTest(LeagueWebhook webhook) {
		return deliver(webhook, DiscordMessages.test(context(webhook.getLeagueId())));
	}

	private void post(UUID leagueId, DiscordTopic topic, Function<DiscordMessages.Context, DiscordMessages.Message> build) {
		webhooks.findById(leagueId).filter(w -> w.posts(topic)).ifPresent(w -> deliver(w, build.apply(context(leagueId))));
	}

	private DiscordClient.Result deliver(LeagueWebhook webhook, DiscordMessages.Message message) {
		DiscordClient.Result result = client.post(webhook.getUrl(), message);
		if (!result.ok()) {
			webhook.failed(result.error(), result.gone());
			webhooks.save(webhook);
		}
		return result;
	}

	private DiscordMessages.Context context(UUID leagueId) {
		LeagueRef ref = leagueService.refs(List.of(leagueId)).get(leagueId);
		Map<UUID, String> teams = leagueService.memberInfos(leagueId).stream()
			.collect(Collectors.toMap(MemberInfo::userId, MemberInfo::teamName));
		return new DiscordMessages.Context(ref == null ? "League" : ref.name(),
				ref == null ? publicUrl : publicUrl + "/leagues/" + ref.slug(), teams);
	}

}
