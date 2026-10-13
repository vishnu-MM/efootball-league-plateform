package com.leagueapp.standings;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

import com.leagueapp.match.MatchResults.FinalResult;

/**
 * Builds a league table from final results. Points are after deductions. Order: points, goal difference, goals
 * scored, then points from the matches between the tied teams only (head-to-head, before deductions), then name.
 */
final class StandingsCalculator {

	private StandingsCalculator() {
	}

	static List<Row> table(Collection<UUID> players, Map<UUID, String> names, List<FinalResult> results, int pointsWin,
			int pointsDraw) {
		return table(players, names, results, pointsWin, pointsDraw, Map.of());
	}

	/** @param deducted points taken from each player (players without deductions can be left out) */
	static List<Row> table(Collection<UUID> players, Map<UUID, String> names, List<FinalResult> results, int pointsWin,
			int pointsDraw, Map<UUID, Integer> deducted) {
		Map<UUID, Row> rows = tally(players, names, results, pointsWin, pointsDraw, deducted);

		// Group by the main criteria; within a tied group, break ties with a mini-table of their own matches.
		Comparator<Row> main = Comparator.comparingInt(Row::points)
			.thenComparingInt(Row::goalDifference)
			.thenComparingInt(Row::goalsFor)
			.reversed();
		Map<String, List<Row>> groups = rows.values().stream()
			.sorted(main)
			.collect(Collectors.groupingBy(r -> r.points() + "/" + r.goalDifference() + "/" + r.goalsFor(),
					LinkedHashMap::new, Collectors.toList()));

		List<Row> ordered = new ArrayList<>();
		for (List<Row> group : groups.values()) {
			if (group.size() == 1) {
				ordered.add(group.get(0));
				continue;
			}
			Set<UUID> tied = group.stream().map(Row::userId).collect(Collectors.toSet());
			List<FinalResult> between = results.stream()
				.filter(r -> tied.contains(r.homeId()) && tied.contains(r.awayId()))
				.toList();
			Map<UUID, Row> mini = tally(tied, names, between, pointsWin, pointsDraw, Map.of());
			group.stream()
				.sorted(Comparator.comparingInt((Row r) -> mini.get(r.userId()).points())
					.reversed()
					.thenComparing(Row::name, String.CASE_INSENSITIVE_ORDER))
				.forEach(ordered::add);
		}
		return ordered;
	}

	private static Map<UUID, Row> tally(Collection<UUID> players, Map<UUID, String> names, List<FinalResult> results,
			int pointsWin, int pointsDraw, Map<UUID, Integer> deducted) {
		Map<UUID, int[]> stats = new HashMap<>(); // played, won, drawn, lost, for, against
		players.forEach(p -> stats.put(p, new int[6]));
		for (FinalResult r : results) {
			int[] home = stats.get(r.homeId());
			int[] away = stats.get(r.awayId());
			if (home == null || away == null) {
				continue; // not a player in this table
			}
			record(home, r.homeGoals(), r.awayGoals());
			record(away, r.awayGoals(), r.homeGoals());
		}
		Map<UUID, Row> rows = new LinkedHashMap<>();
		stats.forEach((id, s) -> {
			int minus = deducted.getOrDefault(id, 0);
			rows.put(id, new Row(id, names.getOrDefault(id, "Player"), s[0], s[1], s[2], s[3], s[4], s[5], s[4] - s[5],
					minus, s[1] * pointsWin + s[2] * pointsDraw - minus));
		});
		return rows;
	}

	private static void record(int[] s, int scored, int conceded) {
		s[0]++;
		if (scored > conceded) {
			s[1]++;
		}
		else if (scored == conceded) {
			s[2]++;
		}
		else {
			s[3]++;
		}
		s[4] += scored;
		s[5] += conceded;
	}

	record Row(UUID userId, String name, int played, int won, int drawn, int lost, int goalsFor, int goalsAgainst,
			int goalDifference, int deducted, int points) {
	}

}
