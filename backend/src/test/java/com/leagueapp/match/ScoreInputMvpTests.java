package com.leagueapp.match;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.List;
import java.util.Map;
import java.util.UUID;

import org.junit.jupiter.api.Test;
import org.springframework.web.server.ResponseStatusException;

import com.leagueapp.match.ScoreReport.Side;

class ScoreInputMvpTests {

	final UUID card = UUID.randomUUID();
	final Map<Side, Map<UUID, String>> homeHasSquad = Map.of(Side.HOME, Map.of(card, "Mbappé"), Side.AWAY, Map.of());

	@Test
	void aSideWithASquadPicksAnApprovedCardAndTheNameIsASnapshot() {
		ScoreReport.Mvp mvp = input(new ScoreInput.MvpInput(Side.HOME, card, "ignored")).validMvp(homeHasSquad);
		assertThat(mvp).isEqualTo(new ScoreReport.Mvp(Side.HOME, "Mbappé", card));
	}

	@Test
	void aSideWithoutASquadTypesAName() {
		assertThat(input(new ScoreInput.MvpInput(Side.AWAY, null, "  Kane ")).validMvp(homeHasSquad))
			.isEqualTo(new ScoreReport.Mvp(Side.AWAY, "Kane", null));
	}

	@Test
	void refusesCardsFromTheWrongPlaceAndTypedNamesWhereASquadExists() {
		assertThatThrownBy(() -> input(new ScoreInput.MvpInput(Side.HOME, UUID.randomUUID(), null)).validMvp(homeHasSquad))
			.isInstanceOf(ResponseStatusException.class);
		assertThatThrownBy(() -> input(new ScoreInput.MvpInput(Side.HOME, null, "Typed")).validMvp(homeHasSquad))
			.isInstanceOf(ResponseStatusException.class);
		assertThatThrownBy(() -> input(new ScoreInput.MvpInput(Side.AWAY, card, null)).validMvp(homeHasSquad))
			.isInstanceOf(ResponseStatusException.class);
	}

	@Test
	void noMvpIsFine() {
		assertThat(input(null).validMvp(homeHasSquad)).isNull();
		assertThat(input(new ScoreInput.MvpInput(Side.AWAY, null, " ")).validMvp(homeHasSquad)).isNull();
	}

	private static ScoreInput input(ScoreInput.MvpInput mvp) {
		return new ScoreInput(1, 0, List.of(), null, mvp, List.of());
	}

}
