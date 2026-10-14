package com.leagueapp.roster;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;

import org.springframework.context.ApplicationEventPublisher;
import org.springframework.data.domain.Limit;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import com.leagueapp.files.Uploads;
import com.leagueapp.league.LeagueRole;
import com.leagueapp.league.LeagueService;
import com.leagueapp.league.LeagueService.LeagueAccess;
import com.leagueapp.league.LeagueService.MemberInfo;

/** Squads: adding and removing cards, admin approval, the league's squad rules and its transfer window. */
@Service
@Transactional(readOnly = true)
class RosterService {

	static final int SEARCH_LIMIT = 20;

	private final LeagueService leagueService;
	private final Squads squads;
	private final CardRepository cards;
	private final SquadCardRepository squadCards;
	private final Uploads uploads;
	private final ApplicationEventPublisher events;

	RosterService(LeagueService leagueService, Squads squads, CardRepository cards, SquadCardRepository squadCards,
			Uploads uploads, ApplicationEventPublisher events) {
		this.leagueService = leagueService;
		this.squads = squads;
		this.cards = cards;
		this.squadCards = squadCards;
		this.uploads = uploads;
		this.events = events;
	}

	/** Everything the league page shows about squads, for one member. */
	RosterView roster(String slug, UUID viewerId) {
		LeagueAccess league = requireMember(slug, viewerId);
		SquadRules rules = squads.rulesOf(league.leagueId());
		List<SquadCard> mine = squadCards.findByLeagueIdAndUserIdAndStatusInOrderByCreatedAtAsc(league.leagueId(),
				viewerId, List.of(SquadCard.Status.PENDING, SquadCard.Status.APPROVED, SquadCard.Status.REJECTED));
		List<SquadRuleChecker.Entry> live = mine.stream()
			.filter(c -> c.getStatus() != SquadCard.Status.REJECTED)
			.map(c -> c.getCard().entry())
			.toList();
		Long pending = league.canManage()
				? squadCards.countByLeagueIdAndStatus(league.leagueId(), SquadCard.Status.PENDING)
				: null;
		return new RosterView(rules.rules(), rules.isRequireCardProof(), rules.isWindowOpen(), league.canManage(),
				league.viewerRole() == LeagueRole.OWNER, mine, live.size(), SquadRuleChecker.capUsage(rules.rules(), live),
				SquadRuleChecker.problems(rules.rules(), live), flagged(league.leagueId(), rules.rules()), pending);
	}

	/** Verified catalogue cards whose player name contains the text (at least 2 characters). */
	List<Card> search(String slug, UUID viewerId, String text) {
		LeagueAccess league = requireMember(slug, viewerId);
		String query = text == null ? "" : text.strip();
		if (query.length() < 2) {
			return List.of();
		}
		String escaped = query.replace("!", "!!").replace("%", "!%").replace("_", "!_");
		return cards.search(league.leagueId(), escaped, Limit.of(SEARCH_LIMIT));
	}

	/**
	 * Adds a card to the viewer's squad as pending: a catalogue card by id, or a new one (reusing the catalogue entry
	 * if someone already entered the same card). Refused while the window is closed or if the squad would break a rule.
	 */
	@Transactional
	SquadCard add(String slug, UUID viewerId, NewCard request) {
		LeagueAccess league = requireMember(slug, viewerId);
		SquadRules rules = squads.lock(league.leagueId());
		requireWindowOpen(rules);
		Card card = request.cardId() != null
				? cards.findByIdAndLeagueId(request.cardId(), league.leagueId())
					.orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Card not found"))
				: catalogueCard(league.leagueId(), viewerId, request);
		if (squadCards.existsByLeagueIdAndUserIdAndCardIdAndStatusIn(league.leagueId(), viewerId, card.getId(),
				SquadCard.Status.LIVE)) {
			throw conflict("That card is already in your squad");
		}
		List<SquadRuleChecker.Entry> after = new ArrayList<>(
				squads.liveEntries(league.leagueId()).getOrDefault(viewerId, List.of()));
		after.add(card.entry());
		List<String> problems = SquadRuleChecker.problems(rules.rules(), after);
		if (!problems.isEmpty()) {
			throw conflict("Adding this card would break the squad rules: " + String.join("; ", problems));
		}
		if (rules.isRequireCardProof() && request.proofUploadId() == null) {
			throw conflict("This league needs a screenshot of each new card. Add one to show you own it.");
		}
		SquadCard added = new SquadCard(league.leagueId(), viewerId, card);
		if (request.proofUploadId() != null) {
			uploads.attach(league.leagueId(), viewerId, Uploads.Purpose.CARD_PROOF, List.of(request.proofUploadId()));
			added.attachProof(request.proofUploadId());
		}
		squadCards.save(added);
		events.publishEvent(
				new RosterEvents.CardSubmitted(league.leagueId(), viewerId, added.getId(), card.getPlayerName()));
		return added;
	}

	/** The owner takes a card out of their squad: while the window is open, or any time for a rejected card. */
	@Transactional
	void remove(String slug, UUID viewerId, UUID squadCardId) {
		LeagueAccess league = requireMember(slug, viewerId);
		SquadRules rules = squads.lock(league.leagueId());
		SquadCard card = squadCards.findByIdAndLeagueId(squadCardId, league.leagueId())
			.filter(c -> c.getUserId().equals(viewerId) && c.getStatus() != SquadCard.Status.REMOVED)
			.orElseThrow(RosterService::cardNotFound);
		if (card.getStatus() != SquadCard.Status.REJECTED) {
			requireWindowOpen(rules);
		}
		card.remove();
	}

	/** Cards waiting for approval, oldest first, with whose squad they're for. Owners and admins only. */
	List<PendingCard> approvals(String slug, UUID viewerId) {
		LeagueAccess league = requireManager(slug, viewerId);
		Map<UUID, String> names = teamNames(league.leagueId());
		return squadCards.findByLeagueIdAndStatusOrderByCreatedAtAsc(league.leagueId(), SquadCard.Status.PENDING)
			.stream()
			.map(c -> new PendingCard(c, names.getOrDefault(c.getUserId(), "Player")))
			.toList();
	}

	@Transactional
	void approve(String slug, UUID viewerId, UUID squadCardId) {
		SquadCard card = pendingForDecision(slug, viewerId, squadCardId);
		card.approve(viewerId);
		events.publishEvent(new RosterEvents.CardDecided(card.getLeagueId(), viewerId, card.getId(), card.getUserId(),
				card.getCard().getPlayerName(), true, null));
	}

	@Transactional
	void reject(String slug, UUID viewerId, UUID squadCardId, String reason) {
		String clean = reason == null || reason.isBlank() ? null : reason.strip();
		SquadCard card = pendingForDecision(slug, viewerId, squadCardId);
		card.reject(viewerId, clean);
		events.publishEvent(new RosterEvents.CardDecided(card.getLeagueId(), viewerId, card.getId(), card.getUserId(),
				card.getCard().getPlayerName(), false, clean));
	}

	/** Which squads these rules would flag, without saving them. Owner only. */
	List<FlaggedSquad> checkRules(String slug, UUID viewerId, SquadRuleChecker.Rules proposed) {
		LeagueAccess league = requireOwner(slug, viewerId);
		validate(proposed);
		return flagged(league.leagueId(), proposed);
	}

	/** Saves the rules even if some squads now break them; returns the squads that do. Owner only. */
	@Transactional
	List<FlaggedSquad> saveRules(String slug, UUID viewerId, SquadRuleChecker.Rules rules, boolean requireCardProof) {
		LeagueAccess league = requireOwner(slug, viewerId);
		validate(rules);
		SquadRules current = squads.lock(league.leagueId());
		current.change(rules);
		current.setRequireCardProof(requireCardProof);
		events.publishEvent(new RosterEvents.SquadRulesChanged(league.leagueId(), viewerId));
		return flagged(league.leagueId(), rules);
	}

	/** Owners and admins can open or close the transfer window at any time. */
	@Transactional
	void setWindow(String slug, UUID viewerId, boolean open) {
		LeagueAccess league = requireManager(slug, viewerId);
		squads.setWindow(league.leagueId(), viewerId, open);
	}

	/** A card's proof screenshot: for its manager, and for owners and admins (who approve cards). */
	Uploads.StoredImage proof(String slug, UUID viewerId, UUID squadCardId) {
		LeagueAccess league = requireMember(slug, viewerId);
		SquadCard card = squadCards.findByIdAndLeagueId(squadCardId, league.leagueId())
			.filter(c -> c.getUserId().equals(viewerId) || league.canManage())
			.filter(c -> c.getProofUploadId() != null)
			.orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "No screenshot for this card"));
		return uploads.open(card.getProofUploadId());
	}

	/** A member's approved squad, for anyone who can see the league. */
	MemberSquad squadOf(String slug, UUID viewerId, UUID userId) {
		LeagueAccess league = leagueService.access(slug, viewerId);
		MemberInfo member = leagueService.memberInfos(league.leagueId()).stream()
			.filter(m -> m.userId().equals(userId))
			.findFirst()
			.orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "That person isn't in this league"));
		return new MemberSquad(userId, member.teamName(), squads.approvedCards(league.leagueId(), userId));
	}

	private Card catalogueCard(UUID leagueId, UUID viewerId, NewCard request) {
		String name = request.playerName() == null ? "" : request.playerName().strip().replaceAll("\\s+", " ");
		if (name.length() < 2 || name.length() > Card.NAME_MAX) {
			throw badRequest("Use 2 to " + Card.NAME_MAX + " characters for the player's name");
		}
		if (request.cardType() == null || request.position() == null) {
			throw badRequest("Pick the card type and position");
		}
		if (request.rating() != null && (request.rating() < Card.RATING_MIN || request.rating() > Card.RATING_MAX)) {
			throw badRequest("Ratings go from " + Card.RATING_MIN + " to " + Card.RATING_MAX);
		}
		Short ratingShort = request.rating() == null ? null : request.rating().shortValue();
		// The league's rules row is locked, so nobody else can insert the same card in between.
		return cards.findSame(leagueId, name, request.cardType(), ratingShort, request.position())
			.orElseGet(() -> cards.save(new Card(leagueId, name, request.cardType(), request.rating(), request.position(),
					viewerId)));
	}

	private SquadCard pendingForDecision(String slug, UUID viewerId, UUID squadCardId) {
		LeagueAccess league = requireManager(slug, viewerId);
		squads.lock(league.leagueId());
		SquadCard card = squadCards.findByIdAndLeagueId(squadCardId, league.leagueId())
			.filter(c -> c.getStatus() != SquadCard.Status.REMOVED)
			.orElseThrow(RosterService::cardNotFound);
		if (card.getStatus() != SquadCard.Status.PENDING) {
			throw conflict("This card has already been decided");
		}
		return card;
	}

	/** Current members whose squads break these rules. */
	private List<FlaggedSquad> flagged(UUID leagueId, SquadRuleChecker.Rules rules) {
		Map<UUID, List<SquadRuleChecker.Entry>> live = squads.liveEntries(leagueId);
		List<FlaggedSquad> flagged = new ArrayList<>();
		for (MemberInfo member : leagueService.memberInfos(leagueId)) {
			List<String> problems = SquadRuleChecker.problems(rules, live.getOrDefault(member.userId(), List.of()));
			if (!problems.isEmpty()) {
				flagged.add(new FlaggedSquad(member.userId(), member.teamName(), problems));
			}
		}
		return flagged;
	}

	private Map<UUID, String> teamNames(UUID leagueId) {
		return leagueService.memberInfos(leagueId).stream()
			.collect(Collectors.toMap(MemberInfo::userId, MemberInfo::teamName));
	}

	private static void validate(SquadRuleChecker.Rules rules) {
		if (rules.capType() != CapType.NONE && rules.capValue() == null) {
			throw badRequest("Set a value for the rating cap");
		}
	}

	private static void requireWindowOpen(SquadRules rules) {
		if (!rules.isWindowOpen()) {
			throw conflict("The transfer window is closed. Squads can change when it opens again.");
		}
	}

	private LeagueAccess requireMember(String slug, UUID viewerId) {
		LeagueAccess league = leagueService.access(slug, viewerId);
		if (!league.isMember()) {
			throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Only members have squads in this league");
		}
		return league;
	}

	private LeagueAccess requireManager(String slug, UUID viewerId) {
		LeagueAccess league = leagueService.access(slug, viewerId);
		if (!league.canManage()) {
			throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Only the owner or an admin can do that");
		}
		return league;
	}

	private LeagueAccess requireOwner(String slug, UUID viewerId) {
		LeagueAccess league = leagueService.access(slug, viewerId);
		if (league.viewerRole() != LeagueRole.OWNER) {
			throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Only the owner can change the squad rules");
		}
		return league;
	}

	private static ResponseStatusException cardNotFound() {
		return new ResponseStatusException(HttpStatus.NOT_FOUND, "Card not found");
	}

	private static ResponseStatusException conflict(String message) {
		return new ResponseStatusException(HttpStatus.CONFLICT, message);
	}

	private static ResponseStatusException badRequest(String message) {
		return new ResponseStatusException(HttpStatus.BAD_REQUEST, message);
	}

	record NewCard(UUID cardId, String playerName, CardType cardType, Integer rating, Position position,
			UUID proofUploadId) {
	}

	record RosterView(SquadRuleChecker.Rules rules, boolean requireCardProof, boolean windowOpen, boolean canManage, boolean isOwner,
			List<SquadCard> myCards, int mySize, Integer myCapUsage, List<String> myProblems, List<FlaggedSquad> flagged,
			Long pendingCount) {
	}

	record FlaggedSquad(UUID userId, String name, List<String> problems) {
	}

	record PendingCard(SquadCard squadCard, String teamName) {
	}

	record MemberSquad(UUID userId, String name, List<Squads.SquadPlayer> cards) {
	}

}
