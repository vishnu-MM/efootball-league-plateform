package com.leagueapp.match;

import java.io.Serializable;
import java.util.UUID;

import jakarta.persistence.Column;
import jakarta.persistence.Embeddable;
import jakarta.persistence.EmbeddedId;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;

@Entity
@Table(name = "season_participant")
class SeasonParticipant {

	@EmbeddedId
	private Key key;

	protected SeasonParticipant() {
	}

	SeasonParticipant(UUID seasonId, UUID userId) {
		this.key = new Key(seasonId, userId);
	}

	UUID getUserId() {
		return key.userId();
	}

	@Embeddable
	record Key(@Column(name = "season_id") UUID seasonId, @Column(name = "user_id") UUID userId)
			implements Serializable {
	}

}
