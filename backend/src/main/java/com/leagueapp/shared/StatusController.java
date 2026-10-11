package com.leagueapp.shared;

import java.time.OffsetDateTime;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
class StatusController {

	private final JdbcTemplate jdbc;
	private final String version;

	StatusController(JdbcTemplate jdbc, @Value("${app.version}") String version) {
		this.jdbc = jdbc;
		this.version = version;
	}

	@GetMapping("/api/public/status")
	StatusResponse status() {
		OffsetDateTime databaseTime = jdbc.queryForObject("SELECT now()", OffsetDateTime.class);
		return new StatusResponse("league-platform", version, databaseTime);
	}

	record StatusResponse(String app, String version, OffsetDateTime databaseTime) {
	}

}
