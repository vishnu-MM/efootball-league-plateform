package com.leagueapp.roster;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.EnumMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

/**
 * A league's squad rules applied to one squad. "Can this card be added?" is the same check run on the squad plus
 * the new card, so a squad that already breaks the rules can't grow.
 */
final class SquadRuleChecker {

	static final int TOP_N = 11;

	private SquadRuleChecker() {
	}

	/** What is wrong with the squad, in plain words; empty if it follows every rule. */
	static List<String> problems(Rules rules, List<Entry> squad) {
		List<String> problems = new ArrayList<>();
		if (rules.maxSize() != null && squad.size() > rules.maxSize()) {
			problems.add(squad.size() + " cards in the squad; the limit is " + rules.maxSize());
		}
		Map<CardType, Integer> banned = new EnumMap<>(CardType.class);
		for (Entry card : squad) {
			if (rules.banned().contains(card.type())) {
				banned.merge(card.type(), 1, Integer::sum);
			}
		}
		banned.forEach((type, count) -> problems.add(type.label() + " cards are banned (" + count + " in the squad)"));

		Integer usage = capUsage(rules, squad);
		if (usage != null && rules.capValue() != null && usage > rules.capValue()) {
			switch (rules.capType()) {
				case PER_CARD -> squad.stream()
					.filter(card -> card.rating() > rules.capValue())
					.forEach(card -> problems.add(card.name() + " is rated " + card.rating()
							+ "; the limit per card is " + rules.capValue()));
				case SQUAD_TOTAL -> problems.add("Squad total is " + format(usage) + "; the cap is " + format(rules.capValue()));
				case TOP_11_TOTAL -> problems.add("Top-11 total is " + format(usage) + "; the cap is " + format(rules.capValue()));
				case NONE -> {
				}
			}
		}
		return problems;
	}

	/** The number the cap is measured against (highest rating, total, or top-11 total); null without a cap. */
	static Integer capUsage(Rules rules, List<Entry> squad) {
		return switch (rules.capType()) {
			case NONE -> null;
			case PER_CARD -> squad.stream().mapToInt(Entry::rating).max().orElse(0);
			case SQUAD_TOTAL -> squad.stream().mapToInt(Entry::rating).sum();
			case TOP_11_TOTAL -> squad.stream()
				.map(Entry::rating)
				.sorted(Comparator.reverseOrder())
				.limit(TOP_N)
				.mapToInt(Integer::intValue)
				.sum();
		};
	}

	private static String format(int number) {
		return String.format(Locale.ROOT, "%,d", number);
	}

	/** The rules that matter for checking; {@code null} limits are off. */
	record Rules(Integer maxSize, CapType capType, Integer capValue, Set<CardType> banned) {
	}

	/** A live card in a squad (pending or approved). */
	record Entry(String name, CardType type, int rating) {
	}

}
