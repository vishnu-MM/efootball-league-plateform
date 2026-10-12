package com.leagueapp.roster;

import java.time.OffsetDateTime;
import java.util.UUID;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

/** A card in the league's catalogue. Managers own cards through {@link SquadCard}. */
@Entity
@Table(name = "card")
class Card {

	static final int NAME_MAX = 60;

	static final int RATING_MIN = 40;

	static final int RATING_MAX = 110;

	@Id
	private UUID id;

	@Column(name = "league_id", nullable = false)
	private UUID leagueId;

	@Column(name = "player_name", nullable = false, length = NAME_MAX)
	private String playerName;

	@Enumerated(EnumType.STRING)
	@Column(name = "card_type", nullable = false, length = 20)
	private CardType cardType;

	@Column(nullable = false)
	private short rating;

	@Enumerated(EnumType.STRING)
	@Column(nullable = false, length = 4)
	private Position position;

	/** Approved for someone at least once, so it shows up in search. */
	@Column(nullable = false)
	private boolean verified;

	@Column(name = "created_by", nullable = false)
	private UUID createdBy;

	@Column(name = "created_at", nullable = false, insertable = false, updatable = false)
	private OffsetDateTime createdAt;

	protected Card() {
	}

	Card(UUID leagueId, String playerName, CardType cardType, int rating, Position position, UUID createdBy) {
		this.id = UUID.randomUUID();
		this.leagueId = leagueId;
		this.playerName = playerName;
		this.cardType = cardType;
		this.rating = (short) rating;
		this.position = position;
		this.createdBy = createdBy;
	}

	void verify() {
		this.verified = true;
	}

	UUID getId() {
		return id;
	}

	String getPlayerName() {
		return playerName;
	}

	CardType getCardType() {
		return cardType;
	}

	int getRating() {
		return rating;
	}

	Position getPosition() {
		return position;
	}

	SquadRuleChecker.Entry entry() {
		return new SquadRuleChecker.Entry(playerName, cardType, rating);
	}

}
