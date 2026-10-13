package com.leagueapp.stats;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;

import com.leagueapp.match.MatchResults.Credit;

/**
 * Turns goal, assist and MVP credits into a ranked board. A squad card is one player however its name is spelt; a
 * typed name is one player per manager, ignoring case. Ties share a rank (1, 1, 3) and are listed by name.
 */
final class LeaderboardCalculator {

	private LeaderboardCalculator() {
	}

	static List<Entry> board(List<Credit> credits, Credit.Kind kind, int limit) {
		Map<String, Entry> byPlayer = new LinkedHashMap<>();
		for (Credit c : credits) {
			if (c.kind() != kind) {
				continue;
			}
			String key = c.squadCardId() != null ? "card:" + c.squadCardId()
					: "name:" + c.userId() + ":" + c.name().toLowerCase(Locale.ROOT);
			byPlayer.merge(key, new Entry(0, c.userId(), c.name(), c.squadCardId(), 1),
					(a, b) -> new Entry(0, a.userId(), a.name(), a.squadCardId(), a.count() + 1));
		}
		List<Entry> sorted = byPlayer.values().stream()
			.sorted(Comparator.comparingInt(Entry::count).reversed()
				.thenComparing(Entry::name, String.CASE_INSENSITIVE_ORDER))
			.toList();
		List<Entry> ranked = new ArrayList<>();
		for (int i = 0; i < sorted.size() && i < limit; i++) {
			Entry e = sorted.get(i);
			int rank = i > 0 && sorted.get(i - 1).count() == e.count() ? ranked.get(i - 1).rank() : i + 1;
			ranked.add(new Entry(rank, e.userId(), e.name(), e.squadCardId(), e.count()));
		}
		return ranked;
	}

	record Entry(int rank, UUID userId, String name, UUID squadCardId, int count) {
	}

}
