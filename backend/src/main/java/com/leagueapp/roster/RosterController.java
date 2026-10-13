package com.leagueapp.roster;

import java.time.Duration;
import java.util.EnumSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import org.springframework.core.io.InputStreamResource;
import org.springframework.http.CacheControl;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import com.leagueapp.files.Uploads;
import com.leagueapp.identity.CurrentUser;

@RestController
class RosterController {

	private final RosterService rosterService;
	private final CurrentUser currentUser;

	RosterController(RosterService rosterService, CurrentUser currentUser) {
		this.rosterService = rosterService;
		this.currentUser = currentUser;
	}

	/** Rules, window, your squad and flagged squads (plus the pending count for owners and admins). */
	@GetMapping("/api/leagues/{slug}/roster")
	RosterResponse roster(@PathVariable String slug) {
		return RosterResponse.from(rosterService.roster(slug, currentUser.requireId()));
	}

	@GetMapping("/api/leagues/{slug}/cards")
	List<CardResponse> search(@PathVariable String slug, @RequestParam(name = "q", required = false) String query) {
		return rosterService.search(slug, currentUser.requireId(), query).stream().map(CardResponse::from).toList();
	}

	@PostMapping("/api/leagues/{slug}/squad/cards")
	@ResponseStatus(HttpStatus.CREATED)
	SquadCardResponse add(@PathVariable String slug, @Valid @RequestBody AddCardRequest body) {
		return SquadCardResponse.from(rosterService.add(slug, currentUser.requireId(),
				new RosterService.NewCard(body.cardId(), body.playerName(), body.cardType(), body.rating(), body.position(),
						body.proofUploadId())));
	}

	@DeleteMapping("/api/leagues/{slug}/squad/cards/{id}")
	@ResponseStatus(HttpStatus.NO_CONTENT)
	void remove(@PathVariable String slug, @PathVariable UUID id) {
		rosterService.remove(slug, currentUser.requireId(), id);
	}

	@GetMapping("/api/leagues/{slug}/squad/approvals")
	List<PendingCardResponse> approvals(@PathVariable String slug) {
		return rosterService.approvals(slug, currentUser.requireId()).stream()
			.map(p -> new PendingCardResponse(p.squadCard().getId(), p.squadCard().getUserId(), p.teamName(),
					CardResponse.from(p.squadCard().getCard()), p.squadCard().getProofUploadId() != null))
			.toList();
	}

	/** The card's proof screenshot, for its manager and for owners/admins. */
	@GetMapping("/api/leagues/{slug}/squad/cards/{id}/proof")
	ResponseEntity<InputStreamResource> proof(@PathVariable String slug, @PathVariable UUID id) {
		Uploads.StoredImage image = rosterService.proof(slug, currentUser.requireId(), id);
		return ResponseEntity.ok()
			.contentType(MediaType.parseMediaType(image.contentType()))
			.contentLength(image.size())
			.cacheControl(CacheControl.maxAge(Duration.ofHours(1)).cachePrivate())
			.body(new InputStreamResource(image.stream()));
	}

	@PostMapping("/api/leagues/{slug}/squad/cards/{id}/approve")
	@ResponseStatus(HttpStatus.NO_CONTENT)
	void approve(@PathVariable String slug, @PathVariable UUID id) {
		rosterService.approve(slug, currentUser.requireId(), id);
	}

	@PostMapping("/api/leagues/{slug}/squad/cards/{id}/reject")
	@ResponseStatus(HttpStatus.NO_CONTENT)
	void reject(@PathVariable String slug, @PathVariable UUID id, @Valid @RequestBody(required = false) RejectRequest body) {
		rosterService.reject(slug, currentUser.requireId(), id, body == null ? null : body.reason());
	}

	@PostMapping("/api/leagues/{slug}/squad-rules/check")
	FlaggedResponse checkRules(@PathVariable String slug, @Valid @RequestBody RulesRequest body) {
		return new FlaggedResponse(rosterService.checkRules(slug, currentUser.requireId(), body.toRules()));
	}

	@PutMapping("/api/leagues/{slug}/squad-rules")
	FlaggedResponse saveRules(@PathVariable String slug, @Valid @RequestBody RulesRequest body) {
		return new FlaggedResponse(rosterService.saveRules(slug, currentUser.requireId(), body.toRules(),
				Boolean.TRUE.equals(body.requireCardProof())));
	}

	@PutMapping("/api/leagues/{slug}/transfer-window")
	@ResponseStatus(HttpStatus.NO_CONTENT)
	void setWindow(@PathVariable String slug, @Valid @RequestBody WindowRequest body) {
		rosterService.setWindow(slug, currentUser.requireId(), body.open());
	}

	@GetMapping("/api/public/leagues/{slug}/squads/{userId}")
	RosterService.MemberSquad squad(@PathVariable String slug, @PathVariable UUID userId) {
		return rosterService.squadOf(slug, currentUser.id().orElse(null), userId);
	}

	record AddCardRequest(UUID cardId, @Size(max = Card.NAME_MAX) String playerName, CardType cardType,
			@Min(Card.RATING_MIN) @Max(Card.RATING_MAX) Integer rating, Position position, UUID proofUploadId) {
	}

	record RejectRequest(@Size(max = SquadCard.REASON_MAX) String reason) {
	}

	record RulesRequest(@Min(1) @Max(100) Integer maxSquadSize, @NotNull CapType capType,
			@Min(1) @Max(100_000) Integer capValue, Set<CardType> bannedCardTypes, Boolean requireCardProof) {

		SquadRuleChecker.Rules toRules() {
			Set<CardType> banned = bannedCardTypes == null || bannedCardTypes.isEmpty() ? EnumSet.noneOf(CardType.class)
					: EnumSet.copyOf(bannedCardTypes);
			return new SquadRuleChecker.Rules(maxSquadSize, capType, capType == CapType.NONE ? null : capValue, banned);
		}

	}

	record WindowRequest(@NotNull Boolean open) {
	}

	record RulesResponse(Integer maxSquadSize, CapType capType, Integer capValue, Set<CardType> bannedCardTypes,
			boolean requireCardProof) {

		static RulesResponse from(SquadRuleChecker.Rules rules, boolean requireCardProof) {
			return new RulesResponse(rules.maxSize(), rules.capType(), rules.capValue(), rules.banned(),
					requireCardProof);
		}

	}

	record CardResponse(UUID id, String playerName, CardType cardType, int rating, Position position) {

		static CardResponse from(Card card) {
			return new CardResponse(card.getId(), card.getPlayerName(), card.getCardType(), card.getRating(),
					card.getPosition());
		}

	}

	record SquadCardResponse(UUID id, SquadCard.Status status, String rejectReason, CardResponse card,
			boolean hasProof) {

		static SquadCardResponse from(SquadCard squadCard) {
			return new SquadCardResponse(squadCard.getId(), squadCard.getStatus(), squadCard.getRejectReason(),
					CardResponse.from(squadCard.getCard()), squadCard.getProofUploadId() != null);
		}

	}

	record MySquadResponse(List<SquadCardResponse> cards, int size, Integer capUsage, List<String> problems) {
	}

	record RosterResponse(RulesResponse rules, boolean windowOpen, boolean canManage, boolean isOwner,
			MySquadResponse mySquad, List<RosterService.FlaggedSquad> flagged, Long pendingCount) {

		static RosterResponse from(RosterService.RosterView view) {
			return new RosterResponse(RulesResponse.from(view.rules(), view.requireCardProof()), view.windowOpen(), view.canManage(),
					view.isOwner(),
					new MySquadResponse(view.myCards().stream().map(SquadCardResponse::from).toList(), view.mySize(),
							view.myCapUsage(), view.myProblems()),
					view.flagged(), view.pendingCount());
		}

	}

	record PendingCardResponse(UUID id, UUID userId, String teamName, CardResponse card, boolean hasProof) {
	}

	record FlaggedResponse(List<RosterService.FlaggedSquad> flagged) {
	}

}
