package com.leagueapp.match;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * Round-robin schedule by the circle method: the first player stays put and the rest rotate one place per round.
 * With an odd number of players a "bye" slot is added, so one player sits out each round. Home and away games are
 * balanced to within one, and in a home-and-away season the second half repeats the first with sides swapped.
 */
final class FixtureGenerator {

	private FixtureGenerator() {
	}

	/** Pairings in round order. The caller shuffles {@code players} first so the order is random. */
	static List<Pairing> roundRobin(List<UUID> players, boolean homeAndAway) {
		if (players.size() < 2) {
			throw new IllegalArgumentException("A season needs at least two players");
		}
		List<UUID> slots = new ArrayList<>(players);
		if (slots.size() % 2 == 1) {
			// The bye takes the fixed slot, so everyone else's home and away games come out exactly even.
			slots.add(0, null);
		}
		int n = slots.size();
		int roundsPerLeg = n - 1;
		List<Pairing> pairings = new ArrayList<>();

		for (int round = 0; round < roundsPerLeg; round++) {
			for (int i = 0; i < n / 2; i++) {
				UUID a = slots.get(i);
				UUID b = slots.get(n - 1 - i);
				if (a == null || b == null) {
					continue;
				}
				// Everyone circles through the top and bottom rows equally, so the top row is at home.
				// The fixed player never moves, so they alternate instead.
				boolean aAtHome = i != 0 || round % 2 == 0;
				pairings.add(aAtHome ? new Pairing(round + 1, a, b) : new Pairing(round + 1, b, a));
			}
			// Rotate everyone except the first slot one place clockwise.
			slots.add(1, slots.remove(n - 1));
		}

		if (homeAndAway) {
			int firstLeg = pairings.size();
			for (int i = 0; i < firstLeg; i++) {
				Pairing p = pairings.get(i);
				pairings.add(new Pairing(p.round() + roundsPerLeg, p.away(), p.home()));
			}
		}
		return pairings;
	}

	record Pairing(int round, UUID home, UUID away) {
	}

}
