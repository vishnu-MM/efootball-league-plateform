package com.leagueapp.match;

import static com.leagueapp.match.ResultRules.Action.CONFIRM;
import static com.leagueapp.match.ResultRules.Action.DISPUTE;
import static com.leagueapp.match.ResultRules.Action.REPORT;
import static com.leagueapp.match.ResultRules.Action.RESOLVE;
import static org.assertj.core.api.Assertions.assertThat;

import java.util.UUID;

import org.junit.jupiter.api.Test;

class ResultRulesTests {

	final UUID home = UUID.randomUUID();
	final UUID away = UUID.randomUUID();
	final UUID admin = UUID.randomUUID();
	final UUID stranger = UUID.randomUUID();

	@Test
	void eitherPlayerReportsAScheduledMatch() {
		assertThat(allowed(Fixture.Status.SCHEDULED, home, null, false)).containsExactly(REPORT);
		assertThat(allowed(Fixture.Status.SCHEDULED, away, null, false)).containsExactly(REPORT);
		assertThat(allowed(Fixture.Status.SCHEDULED, admin, null, true)).isEmpty();
		assertThat(allowed(Fixture.Status.SCHEDULED, null, null, false)).isEmpty();
	}

	@Test
	void opponentConfirmsOrDisputesAndReporterWaits() {
		assertThat(allowed(Fixture.Status.AWAITING_CONFIRMATION, away, home, false)).containsExactlyInAnyOrder(CONFIRM, DISPUTE);
		assertThat(allowed(Fixture.Status.AWAITING_CONFIRMATION, home, home, false)).isEmpty();
		assertThat(allowed(Fixture.Status.AWAITING_CONFIRMATION, stranger, home, false)).isEmpty();
	}

	@Test
	void adminCanConfirmForTheOpponentButNotTheirOwnReport() {
		assertThat(allowed(Fixture.Status.AWAITING_CONFIRMATION, admin, home, true)).containsExactly(CONFIRM);
		assertThat(allowed(Fixture.Status.AWAITING_CONFIRMATION, home, home, true)).isEmpty();
	}

	@Test
	void onlyAdminsResolveDisputes() {
		assertThat(allowed(Fixture.Status.DISPUTED, admin, home, true)).containsExactly(RESOLVE);
		assertThat(allowed(Fixture.Status.DISPUTED, away, home, false)).isEmpty();
	}

	@Test
	void confirmedIsFinal() {
		assertThat(allowed(Fixture.Status.CONFIRMED, home, null, true)).isEmpty();
	}

	private java.util.Set<ResultRules.Action> allowed(Fixture.Status status, UUID viewer, UUID reporter,
			boolean canManage) {
		return ResultRules.allowed(status, viewer, home, away, reporter, canManage);
	}

}
