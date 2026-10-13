package com.leagueapp.files;

import java.io.IOException;
import java.util.UUID;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.server.ResponseStatusException;

import com.leagueapp.identity.CurrentUser;
import com.leagueapp.league.LeagueService;

@RestController
class UploadController {

	private final Uploads uploads;
	private final LeagueService leagueService;
	private final CurrentUser currentUser;

	UploadController(Uploads uploads, LeagueService leagueService, CurrentUser currentUser) {
		this.uploads = uploads;
		this.leagueService = leagueService;
		this.currentUser = currentUser;
	}

	/** A member uploads a screenshot; it stays unused until a card or score report attaches it by id. */
	@PostMapping("/api/leagues/{slug}/uploads")
	@ResponseStatus(HttpStatus.CREATED)
	Created upload(@PathVariable String slug, @RequestParam("file") MultipartFile file,
			@RequestParam("purpose") Uploads.Purpose purpose) throws IOException {
		UUID me = currentUser.requireId();
		LeagueService.LeagueAccess league = leagueService.access(slug, me);
		if (!league.isMember()) {
			throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Only members can upload screenshots");
		}
		if (file.getSize() > Uploads.MAX_BYTES) {
			throw new ResponseStatusException(HttpStatus.PAYLOAD_TOO_LARGE, "Screenshots can be up to 5 MB");
		}
		return new Created(uploads.store(league.leagueId(), me, purpose, file.getBytes()));
	}

	record Created(UUID id) {
	}

}
