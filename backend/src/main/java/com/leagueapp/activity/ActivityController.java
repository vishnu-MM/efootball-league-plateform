package com.leagueapp.activity;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

import org.springframework.data.domain.Limit;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.leagueapp.identity.CurrentUser;
import com.leagueapp.league.LeagueService;

@RestController
class ActivityController {

	static final int PAGE_SIZE = 30;

	private final LeagueService leagueService;
	private final ActivityRepository activities;
	private final CurrentUser currentUser;

	ActivityController(LeagueService leagueService, ActivityRepository activities, CurrentUser currentUser) {
		this.leagueService = leagueService;
		this.activities = activities;
		this.currentUser = currentUser;
	}

	/** The league log, newest first, for anyone who can see the league. Pass the last entry's time to page on. */
	@GetMapping("/api/public/leagues/{slug}/log")
	@Transactional(readOnly = true)
	LogPage log(@PathVariable String slug, @RequestParam(required = false) OffsetDateTime before,
			@RequestParam(defaultValue = "false") boolean adminOnly) {
		LeagueService.LeagueAccess league = leagueService.access(slug, currentUser.id().orElse(null));
		List<Entry> entries = activities
			.page(league.leagueId(), before == null ? OffsetDateTime.now().plusMinutes(1) : before, adminOnly,
					Limit.of(PAGE_SIZE + 1))
			.stream()
			.map(Entry::of)
			.toList();
		boolean more = entries.size() > PAGE_SIZE;
		return new LogPage(more ? entries.subList(0, PAGE_SIZE) : entries, more);
	}

	record LogPage(List<Entry> entries, boolean hasMore) {
	}

	record Entry(UUID id, OffsetDateTime createdAt, LeagueActivity.Type type, boolean adminAction, String summary,
			String reason, UUID fixtureId, UUID subjectUserId) {

		static Entry of(LeagueActivity a) {
			return new Entry(a.getId(), a.getCreatedAt(), a.getType(), a.isAdminAction(), a.getSummary(), a.getReason(),
					a.getFixtureId(), a.getSubjectUserId());
		}

	}

}
