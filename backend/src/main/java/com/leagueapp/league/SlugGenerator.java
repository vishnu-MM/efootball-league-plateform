package com.leagueapp.league;

import java.text.Normalizer;
import java.util.Locale;
import java.util.Set;
import java.util.function.Predicate;

/** Turns a league name into a unique URL slug, e.g. "Sunday Cup!" -> "sunday-cup", then "sunday-cup-2". */
final class SlugGenerator {

	static final int MAX_LENGTH = 60;

	/** Slugs that would clash with app routes such as /leagues/new. */
	static final Set<String> RESERVED = Set.of("new", "edit", "settings");

	private SlugGenerator() {
	}

	static String slugify(String name) {
		String ascii = Normalizer.normalize(name, Normalizer.Form.NFKD).replaceAll("\\p{M}", "");
		String slug = ascii.toLowerCase(Locale.ROOT).replaceAll("[^a-z0-9]+", "-").replaceAll("(^-+|-+$)", "");
		if (slug.length() > MAX_LENGTH) {
			slug = slug.substring(0, MAX_LENGTH).replaceAll("-+$", "");
		}
		return slug.isEmpty() ? "league" : slug;
	}

	static String uniqueSlug(String name, Predicate<String> taken) {
		String base = slugify(name);
		String candidate = base;
		for (int n = 2; RESERVED.contains(candidate) || taken.test(candidate); n++) {
			candidate = base + "-" + n;
		}
		return candidate;
	}

}
