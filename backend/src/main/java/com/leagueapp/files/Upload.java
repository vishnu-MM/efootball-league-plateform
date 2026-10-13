package com.leagueapp.files;

import java.time.OffsetDateTime;
import java.util.UUID;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

/** A screenshot in storage. Attached once, to one card or one score report, by the person who uploaded it. */
@Entity
@Table(name = "upload")
class Upload {

	@Id
	private UUID id;

	@Column(name = "league_id", nullable = false)
	private UUID leagueId;

	@Column(name = "uploaded_by", nullable = false)
	private UUID uploadedBy;

	@Enumerated(EnumType.STRING)
	@Column(nullable = false, length = 20)
	private Uploads.Purpose purpose;

	@Column(name = "content_type", nullable = false, length = 20)
	private String contentType;

	@Column(name = "size_bytes", nullable = false)
	private int sizeBytes;

	@Column(name = "storage_key", nullable = false, length = 200)
	private String storageKey;

	@Column(name = "attached_at")
	private OffsetDateTime attachedAt;

	@Column(name = "created_at", nullable = false, insertable = false, updatable = false)
	private OffsetDateTime createdAt;

	protected Upload() {
	}

	Upload(UUID leagueId, UUID uploadedBy, Uploads.Purpose purpose, String contentType, int sizeBytes) {
		this.id = UUID.randomUUID();
		this.leagueId = leagueId;
		this.uploadedBy = uploadedBy;
		this.purpose = purpose;
		this.contentType = contentType;
		this.sizeBytes = sizeBytes;
		this.storageKey = "leagues/" + leagueId + "/" + purpose.name().toLowerCase() + "/" + id;
	}

	void attach() {
		this.attachedAt = OffsetDateTime.now();
	}

	boolean isAttached() {
		return attachedAt != null;
	}

	UUID getId() {
		return id;
	}

	UUID getLeagueId() {
		return leagueId;
	}

	UUID getUploadedBy() {
		return uploadedBy;
	}

	Uploads.Purpose getPurpose() {
		return purpose;
	}

	String getContentType() {
		return contentType;
	}

	int getSizeBytes() {
		return sizeBytes;
	}

	String getStorageKey() {
		return storageKey;
	}

}
