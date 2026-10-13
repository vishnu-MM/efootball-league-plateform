package com.leagueapp.notify;

import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableAsync;
import org.springframework.scheduling.annotation.EnableScheduling;

/** Event listeners run after commit on their own thread (@Async); the live-update heartbeat is scheduled. */
@Configuration
@EnableAsync
@EnableScheduling
class NotifyConfiguration {
}
