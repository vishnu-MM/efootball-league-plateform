package com.leagueapp.identity;

import java.util.UUID;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

/** Links a sign-in provider account (e.g. a Google "sub") to an app user. */
@Entity
@Table(name = "user_identity")
class UserIdentity {

	@Id
	private UUID id;

	@Column(name = "user_id", nullable = false)
	private UUID userId;

	@Column(nullable = false, length = 20)
	private String provider;

	@Column(nullable = false)
	private String subject;

	protected UserIdentity() {
	}

	UserIdentity(UUID userId, String provider, String subject) {
		this.id = UUID.randomUUID();
		this.userId = userId;
		this.provider = provider;
		this.subject = subject;
	}

	UUID getUserId() {
		return userId;
	}

}
