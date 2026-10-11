package com.leagueapp.standings;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import java.util.Map;
import java.util.UUID;

import org.junit.jupiter.api.Test;

import com.leagueapp.match.MatchResults.FinalResult;

class StandingsCalculatorTests {

	final UUID ann = UUID.randomUUID();
	final UUID ben = UUID.randomUUID();
	final UUID cat = UUID.randomUUID();
	final UUID dan = UUID.randomUUID();
	final Map<UUID, String> names = Map.of(ann, "Ann FC", ben, "Ben United", cat, "Cat City", dan, "Dan Town");

	@Test
	void countsPointsGoalsAndRecord() {
		List<StandingsCalculator.Row> table = table(List.of(ann, ben, cat),
				List.of(result(ann, ben, 3, 1), result(ben, cat, 2, 2), result(cat, ann, 0, 1)));

		StandingsCalculator.Row first = table.get(0);
		assertThat(first.name()).isEqualTo("Ann FC");
		assertThat(first).extracting("played", "won", "drawn", "lost", "goalsFor", "goalsAgainst", "goalDifference", "points")
			.containsExactly(2, 2, 0, 0, 4, 1, 3, 6);
		assertThat(table).extracting(StandingsCalculator.Row::name).containsExactly("Ann FC", "Cat City", "Ben United");
	}

	@Test
	void playersWithoutResultsStillAppear() {
		assertThat(table(List.of(ann, ben), List.of()))
			.extracting(StandingsCalculator.Row::played).containsExactly(0, 0);
	}

	@Test
	void goalDifferenceThenGoalsScoredBreakTies() {
		// Ann and Ben both win once and lose once. Ann's goal difference is better.
		List<StandingsCalculator.Row> byDifference = table(List.of(ann, ben, cat, dan),
				List.of(result(ann, cat, 5, 0), result(ann, dan, 0, 1), result(ben, cat, 1, 0), result(ben, dan, 0, 1)));
		assertThat(byDifference).extracting(StandingsCalculator.Row::name).startsWith("Dan Town", "Ann FC", "Ben United");

		// Same difference (+1), Ben scored more.
		List<StandingsCalculator.Row> byGoals = table(List.of(ann, ben, cat, dan),
				List.of(result(ann, cat, 1, 0), result(ben, dan, 4, 3)));
		assertThat(byGoals).extracting(StandingsCalculator.Row::name).startsWith("Ben United", "Ann FC");
	}

	@Test
	void headToHeadBreaksAFullTie() {
		// Identical records overall; Cat beat Ann when they met.
		List<StandingsCalculator.Row> table = table(List.of(ann, cat, ben, dan),
				List.of(result(cat, ann, 2, 1), result(ann, ben, 2, 1), result(dan, cat, 2, 1), result(ben, dan, 0, 0)));
		StandingsCalculator.Row annRow = table.stream().filter(r -> r.userId().equals(ann)).findFirst().orElseThrow();
		StandingsCalculator.Row catRow = table.stream().filter(r -> r.userId().equals(cat)).findFirst().orElseThrow();
		assertThat(annRow.points()).isEqualTo(catRow.points());
		assertThat(annRow.goalDifference()).isEqualTo(catRow.goalDifference());
		assertThat(annRow.goalsFor()).isEqualTo(catRow.goalsFor());
		assertThat(table.indexOf(catRow)).isLessThan(table.indexOf(annRow));
	}

	@Test
	void usesTheSeasonsPointsSettings() {
		List<StandingsCalculator.Row> table = StandingsCalculator.table(List.of(ann, ben), names,
				List.of(result(ann, ben, 1, 0), result(ben, ann, 1, 1)), 2, 1);
		assertThat(table.get(0).points()).isEqualTo(3);
		assertThat(table.get(1).points()).isEqualTo(1);
	}

	private List<StandingsCalculator.Row> table(List<UUID> players, List<FinalResult> results) {
		return StandingsCalculator.table(players, names, results, 3, 1);
	}

	private static FinalResult result(UUID home, UUID away, int homeGoals, int awayGoals) {
		return new FinalResult(home, away, homeGoals, awayGoals);
	}

}
