package com.leagueapp.roster;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;

import org.junit.jupiter.api.Test;

import com.leagueapp.roster.SquadRuleChecker.Entry;
import com.leagueapp.roster.SquadRuleChecker.Rules;

class SquadRuleCheckerTests {

	static final Rules NO_RULES = new Rules(null, CapType.NONE, null, Set.of());

	@Test
	void anySquadIsFineWithoutRules() {
		assertThat(SquadRuleChecker.problems(NO_RULES, cards(30, 105))).isEmpty();
		assertThat(SquadRuleChecker.capUsage(NO_RULES, cards(3, 90))).isNull();
	}

	@Test
	void squadSizeIsAMaximum() {
		Rules rules = new Rules(3, CapType.NONE, null, Set.of());
		assertThat(SquadRuleChecker.problems(rules, cards(3, 80))).isEmpty();
		assertThat(SquadRuleChecker.problems(rules, cards(4, 80))).containsExactly("4 cards in the squad; the limit is 3");
	}

	@Test
	void bannedTypesAreNamedOnce() {
		Rules rules = new Rules(null, CapType.NONE, null, Set.of(CardType.EPIC, CardType.BIG_TIME));
		List<Entry> squad = List.of(new Entry("A", CardType.EPIC, 98), new Entry("B", CardType.EPIC, 97),
				new Entry("C", CardType.STANDARD, 80));
		assertThat(SquadRuleChecker.problems(rules, squad)).containsExactly("Epic cards are banned (2 in the squad)");
	}

	@Test
	void perCardCapNamesEachCardOverIt() {
		Rules rules = new Rules(null, CapType.PER_CARD, 95, Set.of());
		List<Entry> squad = List.of(new Entry("Messi", CardType.EPIC, 98), new Entry("Pedri", CardType.STANDARD, 95));
		assertThat(SquadRuleChecker.problems(rules, squad)).containsExactly("Messi is rated 98; the limit per card is 95");
		assertThat(SquadRuleChecker.capUsage(rules, squad)).isEqualTo(98);
	}

	@Test
	void squadTotalAddsEveryCard() {
		Rules rules = new Rules(null, CapType.SQUAD_TOTAL, 250, Set.of());
		assertThat(SquadRuleChecker.capUsage(rules, cards(3, 80))).isEqualTo(240);
		assertThat(SquadRuleChecker.problems(rules, cards(3, 80))).isEmpty();
		assertThat(SquadRuleChecker.problems(rules, cards(4, 80))).containsExactly("Squad total is 320; the cap is 250");
	}

	@Test
	void topElevenCountsOnlyTheBestEleven() {
		Rules rules = new Rules(null, CapType.TOP_11_TOTAL, 1000, Set.of());
		List<Entry> squad = new ArrayList<>(cards(11, 90)); // 990
		squad.add(new Entry("Bench", CardType.STANDARD, 70)); // not in the top 11
		assertThat(SquadRuleChecker.capUsage(rules, squad)).isEqualTo(990);
		assertThat(SquadRuleChecker.problems(rules, squad)).isEmpty();

		squad.add(new Entry("Star", CardType.EPIC, 101)); // replaces a 90 in the top 11
		assertThat(SquadRuleChecker.capUsage(rules, squad)).isEqualTo(1001);
		assertThat(SquadRuleChecker.problems(rules, squad)).containsExactly("Top-11 total is 1,001; the cap is 1,000");
	}

	@Test
	void topElevenWithFewerCardsCountsWhatThereIs() {
		Rules rules = new Rules(null, CapType.TOP_11_TOTAL, 1000, Set.of());
		assertThat(SquadRuleChecker.capUsage(rules, cards(2, 90))).isEqualTo(180);
	}

	@Test
	void everyBrokenRuleIsListed() {
		Rules rules = new Rules(1, CapType.SQUAD_TOTAL, 100, Set.of(CardType.EPIC));
		List<Entry> squad = List.of(new Entry("A", CardType.EPIC, 98), new Entry("B", CardType.STANDARD, 80));
		assertThat(SquadRuleChecker.problems(rules, squad)).hasSize(3);
	}

	@Test
	void unratedCardsAreToleratedInCapCalculations() {
		Rules perCard = new Rules(null, CapType.PER_CARD, 90, Set.of());
		List<Entry> squadWithUnrated = List.of(new Entry("A", CardType.STANDARD, null), new Entry("B", CardType.STANDARD, 85));
		assertThat(SquadRuleChecker.problems(perCard, squadWithUnrated)).isEmpty();
		assertThat(SquadRuleChecker.capUsage(perCard, squadWithUnrated)).isEqualTo(85);

		Rules total = new Rules(null, CapType.SQUAD_TOTAL, 200, Set.of());
		assertThat(SquadRuleChecker.capUsage(total, squadWithUnrated)).isEqualTo(85);

		Rules top11 = new Rules(null, CapType.TOP_11_TOTAL, 1000, Set.of());
		assertThat(SquadRuleChecker.capUsage(top11, squadWithUnrated)).isEqualTo(85);
	}

	private static List<Entry> cards(int count, int rating) {
		List<Entry> squad = new ArrayList<>();
		for (int i = 0; i < count; i++) {
			squad.add(new Entry("Player " + i, CardType.STANDARD, rating));
		}
		return squad;
	}

}
