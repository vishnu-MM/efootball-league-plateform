package com.leagueapp.match;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.stream.IntStream;

import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.junit.jupiter.api.Test;

class FixtureGeneratorTests {

	@ParameterizedTest
	@ValueSource(ints = { 2, 3, 4, 5, 6, 7, 8, 11, 20 })
	void everyPairMeetsExactlyOnceInASingleRoundRobin(int n) {
		List<UUID> players = players(n);
		List<FixtureGenerator.Pairing> pairings = FixtureGenerator.roundRobin(players, false);

		assertThat(pairings).hasSize(n * (n - 1) / 2);
		Set<Set<UUID>> pairs = new HashSet<>();
		for (FixtureGenerator.Pairing p : pairings) {
			assertThat(p.home()).isNotEqualTo(p.away());
			assertThat(pairs.add(Set.of(p.home(), p.away()))).as("pair repeated").isTrue();
		}
		int rounds = n % 2 == 0 ? n - 1 : n;
		assertThat(pairings).allSatisfy(p -> assertThat(p.round()).isBetween(1, rounds));
	}

	@ParameterizedTest
	@ValueSource(ints = { 3, 4, 5, 6, 9, 10 })
	void nobodyPlaysTwiceInOneRoundAndByesRotate(int n) {
		List<FixtureGenerator.Pairing> pairings = FixtureGenerator.roundRobin(players(n), false);
		Map<Integer, Set<UUID>> busy = new HashMap<>();
		for (FixtureGenerator.Pairing p : pairings) {
			Set<UUID> inRound = busy.computeIfAbsent(p.round(), r -> new HashSet<>());
			assertThat(inRound.add(p.home())).isTrue();
			assertThat(inRound.add(p.away())).isTrue();
		}
		// Odd counts: exactly one player sits out each round.
		int expectedPerRound = n % 2 == 0 ? n : n - 1;
		assertThat(busy.values()).allSatisfy(set -> assertThat(set).hasSize(expectedPerRound));
	}

	@ParameterizedTest
	@ValueSource(ints = { 4, 6, 8, 10, 5, 7 })
	void homeAndAwayGamesAreBalanced(int n) {
		Map<UUID, Integer> home = new HashMap<>();
		Map<UUID, Integer> away = new HashMap<>();
		for (FixtureGenerator.Pairing p : FixtureGenerator.roundRobin(players(n), false)) {
			home.merge(p.home(), 1, Integer::sum);
			away.merge(p.away(), 1, Integer::sum);
		}
		home.keySet().forEach(id -> assertThat(Math.abs(home.getOrDefault(id, 0) - away.getOrDefault(id, 0)))
			.as("home/away imbalance").isLessThanOrEqualTo(1));
	}

	@Test
	void homeAndAwaySeasonMirrorsTheFirstHalf() {
		int n = 6;
		List<FixtureGenerator.Pairing> pairings = FixtureGenerator.roundRobin(players(n), true);
		int firstLeg = n * (n - 1) / 2;

		assertThat(pairings).hasSize(2 * firstLeg);
		for (int i = 0; i < firstLeg; i++) {
			FixtureGenerator.Pairing first = pairings.get(i);
			FixtureGenerator.Pairing second = pairings.get(firstLeg + i);
			assertThat(second.round()).isEqualTo(first.round() + n - 1);
			assertThat(second.home()).isEqualTo(first.away());
			assertThat(second.away()).isEqualTo(first.home());
		}
	}

	@Test
	void needsTwoPlayers() {
		assertThatThrownBy(() -> FixtureGenerator.roundRobin(players(1), false))
			.isInstanceOf(IllegalArgumentException.class);
	}

	private static List<UUID> players(int n) {
		return IntStream.range(0, n).mapToObj(i -> UUID.randomUUID()).toList();
	}

}
