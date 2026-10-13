package com.leagueapp.roster;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * The roster module's API for other modules: a member's approved cards, which squads break the rules, and the
 * transfer window. Also holds the squad reads the roster's own service shares.
 */
@Service
@Transactional(readOnly = true)
public class Squads {

	private final SquadCardRepository squadCards;
	private final SquadRulesRepository rules;
	private final ApplicationEventPublisher events;

	Squads(SquadCardRepository squadCards, SquadRulesRepository rules, ApplicationEventPublisher events) {
		this.squadCards = squadCards;
		this.rules = rules;
		this.events = events;
	}

	/** The member's approved cards, best rated first: what match reports pick scorers and assists from. */
	public List<SquadPlayer> approvedCards(UUID leagueId, UUID userId) {
		return squadCards.findByLeagueIdAndUserIdAndStatusInOrderByCreatedAtAsc(leagueId, userId,
				List.of(SquadCard.Status.APPROVED))
			.stream()
			.map(SquadPlayer::of)
			.sorted((a, b) -> Integer.compare(b.rating(), a.rating()))
			.toList();
	}

	/** Card details for these squad cards, removed ones included (goals keep pointing at them): id → card. */
	public Map<UUID, SquadPlayer> cardsById(Collection<UUID> squadCardIds) {
		Map<UUID, SquadPlayer> found = new HashMap<>();
		squadCards.findAllById(squadCardIds).forEach(c -> found.put(c.getId(), SquadPlayer.of(c)));
		return found;
	}

	/** For each of these members whose squad breaks the league's rules, what is wrong. */
	public Map<UUID, List<String>> problems(UUID leagueId, Collection<UUID> userIds) {
		SquadRuleChecker.Rules current = rulesOf(leagueId).rules();
		Map<UUID, List<SquadRuleChecker.Entry>> live = liveEntries(leagueId);
		Map<UUID, List<String>> problems = new LinkedHashMap<>();
		for (UUID userId : userIds) {
			List<String> found = SquadRuleChecker.problems(current, live.getOrDefault(userId, List.of()));
			if (!found.isEmpty()) {
				problems.put(userId, found);
			}
		}
		return problems;
	}

	/** A season ended: squads can change. */
	@Transactional
	public void openWindow(UUID leagueId) {
		setWindow(leagueId, null, true);
	}

	/** A season started: squads are fixed. */
	@Transactional
	public void closeWindow(UUID leagueId) {
		setWindow(leagueId, null, false);
	}

	/** Opens or closes the window; announces it only if that changed anything. {@code actorId} null: automatic. */
	@Transactional
	void setWindow(UUID leagueId, UUID actorId, boolean open) {
		SquadRules current = lock(leagueId);
		if (current.isWindowOpen() != open) {
			current.setWindowOpen(open);
			events.publishEvent(new RosterEvents.TransferWindowChanged(leagueId, actorId, open));
		}
	}

	/** The league's rules, or the defaults if it has never saved any. */
	SquadRules rulesOf(UUID leagueId) {
		return rules.findById(leagueId).orElseGet(() -> SquadRules.defaults(leagueId));
	}

	/** The league's rules row, locked so squad changes in the league happen one at a time. */
	SquadRules lock(UUID leagueId) {
		rules.createIfAbsent(leagueId);
		return rules.lockByLeagueId(leagueId).orElseThrow();
	}

	/** Each member's live (pending or approved) cards, as the rule checker sees them. */
	Map<UUID, List<SquadRuleChecker.Entry>> liveEntries(UUID leagueId) {
		Map<UUID, List<SquadRuleChecker.Entry>> byUser = new HashMap<>();
		for (SquadCard card : squadCards.findByLeagueIdAndStatusIn(leagueId, SquadCard.Status.LIVE)) {
			byUser.computeIfAbsent(card.getUserId(), id -> new ArrayList<>()).add(card.getCard().entry());
		}
		return byUser;
	}

	/** An approved card as other modules see it. */
	public record SquadPlayer(UUID squadCardId, String playerName, CardType cardType, int rating, Position position) {

		static SquadPlayer of(SquadCard squadCard) {
			Card card = squadCard.getCard();
			return new SquadPlayer(squadCard.getId(), card.getPlayerName(), card.getCardType(), card.getRating(),
					card.getPosition());
		}

	}

}
