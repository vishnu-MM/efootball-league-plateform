package com.leagueapp.discord;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Map;
import java.util.UUID;

import org.junit.jupiter.api.Test;

import com.leagueapp.match.MatchEvents;

class DiscordMessagesTests {

	final UUID ann = UUID.randomUUID();
	final UUID ben = UUID.randomUUID();
	final UUID fixture = UUID.randomUUID();
	final DiscordMessages.Context context = new DiscordMessages.Context("Sunday League", "https://app.test/leagues/sunday",
			Map.of(ann, "Ann FC", ben, "Ben United"));

	@Test
	void overridesShowTheOldScoreAndTheReason() {
		DiscordMessages.Message m = DiscordMessages.resultOverridden(context, new MatchEvents.ResultOverridden(UUID.randomUUID(),
				UUID.randomUUID(), 2, 1, "Wrong score entered", new MatchEvents.Match(fixture, ann, ben, 1, 1)));
		assertThat(m.title()).isEqualTo("Result changed: Ann FC 1–1 Ben United (was 2–1)");
		assertThat(m.description()).isEqualTo("Reason: Wrong score entered");
		assertThat(m.url()).isEqualTo("https://app.test/leagues/sunday/fixtures/" + fixture);
		assertThat(m.footer()).isEqualTo("Sunday League");
	}

	@Test
	void forfeitsSayWhoForfeited() {
		DiscordMessages.Message m = DiscordMessages.forfeitAwarded(context, new MatchEvents.ForfeitAwarded(UUID.randomUUID(),
				UUID.randomUUID(), "AWAY", "No show", new MatchEvents.Match(fixture, ann, ben, 3, 0)));
		assertThat(m.title()).isEqualTo("Forfeit: Ann FC 3–0 Ben United");
		assertThat(m.description()).isEqualTo("Ben United forfeited. Reason: No show");
	}

	@Test
	void onlyDiscordWebhookUrlsAreAccepted() {
		assertThat(DiscordClient.pathOf("https://discord.com/api/webhooks/123456789012/abcdefghijklmnopqrstuvwxyz"))
			.contains("/api/webhooks/123456789012/abcdefghijklmnopqrstuvwxyz");
		assertThat(DiscordClient.pathOf("https://canary.discordapp.com/api/webhooks/123456789012/abcdefghijklmnopqrstuvwxyz/"))
			.isPresent();
		assertThat(DiscordClient.pathOf("https://discord.com.evil.test/api/webhooks/123456789012/abcdefghijklmnopqrstuvwxyz"))
			.isEmpty();
		assertThat(DiscordClient.pathOf("http://discord.com/api/webhooks/123456789012/abcdefghijklmnopqrstuvwxyz")).isEmpty();
		assertThat(DiscordClient.pathOf("https://discord.com/api/webhooks/123/../../users/@me")).isEmpty();
	}

}
