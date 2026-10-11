package com.leagueapp.identity;

import java.time.OffsetDateTime;
import java.util.UUID;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

@Entity
@Table(name = "app_user")
class AppUser {

	static final int DISPLAY_NAME_MAX = 50;

	@Id
	private UUID id;

	@Column(name = "display_name", nullable = false, length = DISPLAY_NAME_MAX)
	private String displayName;

	@Column(unique = true)
	private String email;

	@Column(name = "created_at", nullable = false, insertable = false, updatable = false)
	private OffsetDateTime createdAt;

	protected AppUser() {
	}

	AppUser(String displayName, String email) {
		this.id = UUID.randomUUID();
		this.displayName = displayName;
		this.email = email;
	}

	UUID getId() {
		return id;
	}

	String getDisplayName() {
		return displayName;
	}

	String getEmail() {
		return email;
	}

}
