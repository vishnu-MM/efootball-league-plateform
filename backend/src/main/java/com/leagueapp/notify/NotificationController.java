package com.leagueapp.notify;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import org.springframework.data.domain.Limit;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import com.leagueapp.identity.CurrentUser;
import com.leagueapp.league.LeagueService;

@RestController
class NotificationController {

	static final int PAGE_SIZE = 30;

	private final NotificationRepository notifications;
	private final LiveUpdates live;
	private final LeagueService leagueService;
	private final CurrentUser currentUser;

	NotificationController(NotificationRepository notifications, LiveUpdates live, LeagueService leagueService,
			CurrentUser currentUser) {
		this.notifications = notifications;
		this.live = live;
		this.leagueService = leagueService;
		this.currentUser = currentUser;
	}

	/** Your notifications, newest first. Pass the last one's time to page on. */
	@GetMapping("/api/notifications")
	@Transactional(readOnly = true)
	NotificationPage list(@RequestParam(required = false) OffsetDateTime before) {
		UUID me = currentUser.requireId();
		List<Notification> page = notifications.findByUserIdAndCreatedAtBeforeOrderByCreatedAtDesc(me,
				before == null ? OffsetDateTime.now().plusMinutes(1) : before, Limit.of(PAGE_SIZE + 1));
		Map<UUID, LeagueService.LeagueRef> leagues = leagueService
			.refs(page.stream().map(Notification::getLeagueId).distinct().toList());
		List<Item> items = page.stream()
			.limit(PAGE_SIZE)
			.map(n -> new Item(n.getId(), n.getType(), n.getText(), n.getLink(), n.getCreatedAt(), n.getReadAt() != null,
					leagues.containsKey(n.getLeagueId()) ? leagues.get(n.getLeagueId()).name() : null))
			.toList();
		return new NotificationPage(items, page.size() > PAGE_SIZE);
	}

	@GetMapping("/api/notifications/unread-count")
	@Transactional(readOnly = true)
	UnreadCount unreadCount() {
		return new UnreadCount(notifications.countByUserIdAndReadAtIsNull(currentUser.requireId()));
	}

	/** Marks these notifications read; no ids (or an empty list) marks them all. */
	@PostMapping("/api/notifications/read")
	@ResponseStatus(HttpStatus.NO_CONTENT)
	@Transactional
	void markRead(@RequestBody(required = false) ReadRequest body) {
		UUID me = currentUser.requireId();
		if (body == null || body.ids() == null || body.ids().isEmpty()) {
			notifications.markAllRead(me, OffsetDateTime.now());
		}
		else {
			notifications.markRead(me, body.ids(), OffsetDateTime.now());
		}
	}

	/** Live updates for the signed-in user: "notification" (unread count) and "league" (a league changed). */
	@GetMapping(path = "/api/stream", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
	SseEmitter stream() {
		return live.open(currentUser.requireId());
	}

	record NotificationPage(List<Item> items, boolean hasMore) {
	}

	record Item(UUID id, String type, String text, String link, OffsetDateTime createdAt, boolean read, String leagueName) {
	}

	record UnreadCount(long unread) {
	}

	record ReadRequest(List<UUID> ids) {
	}

}
