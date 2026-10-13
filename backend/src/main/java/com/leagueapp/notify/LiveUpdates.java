package com.leagueapp.notify;

import java.io.IOException;
import java.time.Duration;
import java.util.Collection;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArraySet;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

/**
 * Open Server-Sent Events streams, per signed-in user (one per tab). In memory, so it only reaches users connected
 * to this backend instance. The browser reconnects by itself when a stream ends.
 */
@Component
class LiveUpdates {

	private static final Logger log = LoggerFactory.getLogger(LiveUpdates.class);

	/** Streams end after this long and the browser opens a fresh one, so dead connections can't pile up. */
	static final Duration STREAM_LIFETIME = Duration.ofMinutes(30);

	private final Map<UUID, Set<SseEmitter>> streams = new ConcurrentHashMap<>();

	SseEmitter open(UUID userId) {
		SseEmitter emitter = new SseEmitter(STREAM_LIFETIME.toMillis());
		Set<SseEmitter> mine = streams.computeIfAbsent(userId, id -> new CopyOnWriteArraySet<>());
		mine.add(emitter);
		Runnable remove = () -> {
			mine.remove(emitter);
			streams.computeIfPresent(userId, (id, set) -> set.isEmpty() ? null : set);
		};
		emitter.onCompletion(remove);
		emitter.onTimeout(remove);
		emitter.onError(e -> remove.run());
		send(emitter, SseEmitter.event().name("ready").data("{}"));
		return emitter;
	}

	/** Tells these users their unread count changed. */
	void notificationsChanged(Collection<UUID> userIds, Map<UUID, Long> unread) {
		for (UUID userId : userIds) {
			sendTo(userId, SseEmitter.event().name("notification").data(Map.of("unread", unread.getOrDefault(userId, 0L))));
		}
	}

	/** Tells these users (a league's members) that something in the league changed, so its pages can refresh. */
	void leagueChanged(Collection<UUID> userIds, String slug) {
		for (UUID userId : userIds) {
			sendTo(userId, SseEmitter.event().name("league").data(Map.of("slug", slug)));
		}
	}

	boolean isConnected(UUID userId) {
		return streams.containsKey(userId);
	}

	/** A comment line every 25 seconds keeps proxies from closing idle streams. */
	@Scheduled(fixedRate = 25_000)
	void heartbeat() {
		streams.values().forEach(set -> set.forEach(e -> send(e, SseEmitter.event().comment("ping"))));
	}

	private void sendTo(UUID userId, SseEmitter.SseEventBuilder event) {
		Set<SseEmitter> mine = streams.get(userId);
		if (mine != null) {
			mine.forEach(e -> send(e, event));
		}
	}

	private static void send(SseEmitter emitter, SseEmitter.SseEventBuilder event) {
		try {
			emitter.send(event);
		}
		catch (IOException | IllegalStateException e) {
			log.debug("Dropping a closed live-update stream: {}", e.getMessage());
			emitter.completeWithError(e);
		}
	}

}
