package com.leagueapp.league;

import java.io.Serializable;
import java.util.UUID;

import jakarta.persistence.Column;
import jakarta.persistence.Embeddable;
import jakarta.persistence.EmbeddedId;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Table;

@Entity
@Table(name = "league_membership")
class LeagueMembership {

	@EmbeddedId
	private Key key;

	@Enumerated(EnumType.STRING)
	@Column(nullable = false, length = 20)
	private Role role;

	protected LeagueMembership() {
	}

	LeagueMembership(UUID leagueId, UUID userId, Role role) {
		this.key = new Key(leagueId, userId);
		this.role = role;
	}

	UUID getLeagueId() {
		return key.leagueId;
	}

	Role getRole() {
		return role;
	}

	enum Role {
		OWNER, ADMIN, MANAGER
	}

	@Embeddable
	record Key(@Column(name = "league_id") UUID leagueId, @Column(name = "user_id") UUID userId)
			implements Serializable {
	}

}
