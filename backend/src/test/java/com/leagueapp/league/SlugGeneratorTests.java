package com.leagueapp.league;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Set;

import org.junit.jupiter.api.Test;

class SlugGeneratorTests {

	@Test
	void slugifiesNames() {
		assertThat(SlugGenerator.slugify("Sunday Cup!")).isEqualTo("sunday-cup");
		assertThat(SlugGenerator.slugify("  Liga Española  2026 ")).isEqualTo("liga-espanola-2026");
		assertThat(SlugGenerator.slugify("!!!")).isEqualTo("league");
	}

	@Test
	void truncatesLongNamesWithoutTrailingDash() {
		String slug = SlugGenerator.slugify("a".repeat(59) + " bbbbbb");
		assertThat(slug).hasSizeLessThanOrEqualTo(SlugGenerator.MAX_LENGTH).doesNotEndWith("-");
	}

	@Test
	void avoidsReservedRouteNames() {
		assertThat(SlugGenerator.uniqueSlug("New", slug -> false)).isEqualTo("new-2");
	}

	@Test
	void appendsNumberWhenTaken() {
		Set<String> taken = Set.of("sunday-cup", "sunday-cup-2");
		assertThat(SlugGenerator.uniqueSlug("Sunday Cup", taken::contains)).isEqualTo("sunday-cup-3");
	}

}
