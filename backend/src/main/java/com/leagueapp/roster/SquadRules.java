package com.leagueapp.roster;

import java.util.Arrays;
import java.util.EnumSet;
import java.util.Set;
import java.util.UUID;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

/** A league's squad rules and whether its transfer window is open. */
@Entity
@Table(name = "squad_rules")
class SquadRules {

	@Id
	@Column(name = "league_id")
	private UUID leagueId;

	@Column(name = "max_squad_size")
	private Short maxSquadSize;

	@Enumerated(EnumType.STRING)
	@Column(name = "cap_type", nullable = false, length = 20)
	private CapType capType;

	@Column(name = "cap_value")
	private Integer capValue;

	@Column(name = "banned_card_types", nullable = false)
	private String[] bannedCardTypes;

	@Column(name = "window_open", nullable = false)
	private boolean windowOpen;

	@Column(name = "require_card_proof", nullable = false)
	private boolean requireCardProof;

	protected SquadRules() {
	}

	/** The defaults a league has before anyone saves rules: no limits and an open window. Not saved. */
	static SquadRules defaults(UUID leagueId) {
		SquadRules rules = new SquadRules();
		rules.leagueId = leagueId;
		rules.capType = CapType.NONE;
		rules.bannedCardTypes = new String[0];
		rules.windowOpen = true;
		return rules;
	}

	void change(SquadRuleChecker.Rules rules) {
		this.maxSquadSize = rules.maxSize() == null ? null : rules.maxSize().shortValue();
		this.capType = rules.capType();
		this.capValue = rules.capType() == CapType.NONE ? null : rules.capValue();
		this.bannedCardTypes = rules.banned().stream().map(Enum::name).sorted().toArray(String[]::new);
	}

	void setWindowOpen(boolean open) {
		this.windowOpen = open;
	}

	boolean isWindowOpen() {
		return windowOpen;
	}

	/** New cards need a screenshot of the card in the game. */
	boolean isRequireCardProof() {
		return requireCardProof;
	}

	void setRequireCardProof(boolean require) {
		this.requireCardProof = require;
	}

	SquadRuleChecker.Rules rules() {
		Set<CardType> banned = EnumSet.noneOf(CardType.class);
		Arrays.stream(bannedCardTypes).map(CardType::valueOf).forEach(banned::add);
		return new SquadRuleChecker.Rules(maxSquadSize == null ? null : maxSquadSize.intValue(), capType, capValue,
				banned);
	}

}
