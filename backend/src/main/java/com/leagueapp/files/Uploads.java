package com.leagueapp.files;

import java.io.InputStream;
import java.time.Duration;
import java.time.OffsetDateTime;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.Limit;
import org.springframework.http.HttpStatus;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import software.amazon.awssdk.core.exception.SdkException;

/**
 * The files module's API: store a screenshot, attach it to whatever it is proof for, and read it back. Who may see a
 * screenshot is decided by the module it is attached to (a card's owner and admins, a match's players and admins).
 */
@Service
@Transactional(readOnly = true)
public class Uploads {

	static final int MAX_BYTES = 5 * 1024 * 1024;

	/** Uploads nobody attached within a day are deleted. */
	static final Duration UNATTACHED_LIFETIME = Duration.ofDays(1);

	public enum Purpose {
		CARD_PROOF, DISPUTE_EVIDENCE
	}

	private static final Logger log = LoggerFactory.getLogger(Uploads.class);

	private final UploadRepository uploads;
	private final ObjectStore store;

	Uploads(UploadRepository uploads, ObjectStore store) {
		this.uploads = uploads;
		this.store = store;
	}

	/** Checks the bytes are a PNG, JPEG or WebP image of at most 5 MB, stores them, and returns the new upload's id. */
	@Transactional
	UUID store(UUID leagueId, UUID userId, Purpose purpose, byte[] bytes) {
		if (bytes.length == 0) {
			throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "That file is empty");
		}
		if (bytes.length > MAX_BYTES) {
			throw new ResponseStatusException(HttpStatus.PAYLOAD_TOO_LARGE, "Screenshots can be up to 5 MB");
		}
		String type = ImageTypes.detect(bytes)
			.orElseThrow(() -> new ResponseStatusException(HttpStatus.BAD_REQUEST, "Upload a PNG, JPEG or WebP screenshot"));
		Upload upload = uploads.save(new Upload(leagueId, userId, purpose, type, bytes.length));
		try {
			store.put(upload.getStorageKey(), bytes, type);
		}
		catch (SdkException e) {
			log.warn("Couldn't store upload {}: {}", upload.getId(), e.getMessage());
			throw new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE, "Screenshots can't be saved right now. Try again later.");
		}
		return upload.getId();
	}

	/**
	 * Attaches the user's own, unused uploads in this league, made for this purpose. Runs in the caller's
	 * transaction, so if saving the card or report fails the uploads stay free.
	 */
	@Transactional(propagation = Propagation.MANDATORY)
	public void attach(UUID leagueId, UUID userId, Purpose purpose, Collection<UUID> uploadIds) {
		if (uploadIds.isEmpty()) {
			return;
		}
		Map<UUID, Upload> found = uploads.lockAll(uploadIds).stream()
			.collect(Collectors.toMap(Upload::getId, Function.identity()));
		for (UUID id : uploadIds) {
			Upload u = found.get(id);
			if (u == null || !u.getLeagueId().equals(leagueId) || !u.getUploadedBy().equals(userId)
					|| u.getPurpose() != purpose || u.isAttached()) {
				throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "That screenshot can't be used here. Upload it again.");
			}
			u.attach();
		}
	}

	/** The stored image, for a module that has already checked the viewer may see it. */
	public StoredImage open(UUID uploadId) {
		Upload u = uploads.findById(uploadId)
			.orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Screenshot not found"));
		try {
			return new StoredImage(u.getContentType(), u.getSizeBytes(), store.get(u.getStorageKey()));
		}
		catch (SdkException e) {
			log.warn("Couldn't read upload {}: {}", uploadId, e.getMessage());
			throw new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE, "Screenshots can't be loaded right now");
		}
	}

	/** Hourly: removes uploads that were never attached (abandoned forms) from storage and the database. */
	@Scheduled(fixedDelay = 60 * 60 * 1000, initialDelay = 5 * 60 * 1000)
	@Transactional
	public void deleteAbandoned() {
		List<Upload> stale = uploads.findByAttachedAtIsNullAndCreatedAtBefore(
				OffsetDateTime.now().minus(UNATTACHED_LIFETIME), Limit.of(500));
		for (Upload u : stale) {
			store.delete(u.getStorageKey());
		}
		uploads.deleteAll(stale);
	}

	public record StoredImage(String contentType, long size, InputStream stream) {
	}

}
