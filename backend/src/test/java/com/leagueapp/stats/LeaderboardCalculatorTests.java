package com.leagueapp.stats;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import java.util.UUID;

import org.junit.jupiter.api.Test;

import com.leagueapp.match.MatchResults.Credit;
import com.leagueapp.match.MatchResults.Credit.Kind;

class LeaderboardCalculatorTests {

	final UUID ann = UUID.randomUUID();
	final UUID ben = UUID.randomUUID();
	final UUID messiCard = UUID.randomUUID();

	@Test
	void countsCardsByCardAndTypedNamesByManagerIgnoringCase() {
		List<Credit> credits = List.of(goal(ann, "Messi", messiCard), goal(ann, "L. Messi", messiCard),
				goal(ben, "kane", null), goal(ben, "Kane", null), goal(ann, "Kane", null), assist(ben, "Kane", null));

		List<LeaderboardCalculator.Entry> board = LeaderboardCalculator.board(credits, Kind.GOAL, 20);

		assertThat(board).extracting(LeaderboardCalculator.Entry::count).containsExactly(2, 2, 1);
		assertThat(board.get(0).squadCardId()).isNull(); // "kane" and "Messi" tie on 2; listed by name
		assertThat(board.get(0).userId()).isEqualTo(ben);
		assertThat(board.get(1).squadCardId()).isEqualTo(messiCard);
		assertThat(board.get(2).userId()).isEqualTo(ann); // Ann's Kane is a different player from Ben's
	}

	@Test
	void tiesShareARankAndTheBoardIsCut() {
		List<Credit> credits = List.of(goal(ann, "A", null), goal(ann, "A", null), goal(ann, "B", null),
				goal(ann, "C", null), goal(ann, "D", null));

		List<LeaderboardCalculator.Entry> board = LeaderboardCalculator.board(credits, Kind.GOAL, 3);

		assertThat(board).extracting(LeaderboardCalculator.Entry::rank).containsExactly(1, 2, 2);
		assertThat(board).extracting(LeaderboardCalculator.Entry::name).containsExactly("A", "B", "C");
	}

	@Test
	void onlyCountsTheAskedKind() {
		assertThat(LeaderboardCalculator.board(List.of(assist(ann, "A", null)), Kind.MVP, 20)).isEmpty();
	}

	private static Credit goal(UUID userId, String name, UUID cardId) {
		return new Credit(Kind.GOAL, userId, name, cardId);
	}

	private static Credit assist(UUID userId, String name, UUID cardId) {
		return new Credit(Kind.ASSIST, userId, name, cardId);
	}

}
