package com.leagueapp.league;

import java.time.OffsetDateTime;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.context.event.EventListener;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * Permanently removes leagues whose 7-day restore window has run out: daily, and once at startup for any the app
 * missed while it was down. Each league goes in its own transaction, so one failure doesn't block the rest.
 */
@Component
class LeaguePurger {

	private static final Logger log = LoggerFactory.getLogger(LeaguePurger.class);

	private final LeagueRepository leagues;
	private final ApplicationEventPublisher events;
	private final TransactionTemplate transactions;

	LeaguePurger(LeagueRepository leagues, ApplicationEventPublisher events, TransactionTemplate transactions) {
		this.leagues = leagues;
		this.events = events;
		this.transactions = transactions;
	}

	@EventListener(ApplicationReadyEvent.class)
	@Scheduled(cron = "0 0 4 * * *")
	void purgeExpired() {
		for (League expired : leagues.findByDeletedAtBefore(OffsetDateTime.now().minus(LeagueService.RESTORE_WINDOW))) {
			try {
				transactions.executeWithoutResult(status -> {
					events.publishEvent(new LeagueEvents.LeaguePurging(expired.getId()));
					// Every league table cascades from league.
					leagues.deleteById(expired.getId());
				});
				log.info("Purged deleted league {}", expired.getSlug());
			}
			catch (RuntimeException e) {
				log.warn("Couldn't purge league {}: {}", expired.getSlug(), e.getMessage());
			}
		}
	}

}
