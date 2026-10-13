package com.leagueapp.roster;

import java.time.OffsetDateTime;
import java.util.EnumSet;
import java.util.Set;
import java.util.UUID;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;

/** A card in one manager's squad, and whether an admin has approved it. */
@Entity
@Table(name = "squad_card")
class SquadCard {

	static final int REASON_MAX = 200;

	@Id
	private UUID id;

	@Column(name = "league_id", nullable = false)
	private UUID leagueId;

	@Column(name = "user_id", nullable = false)
	private UUID userId;

	@ManyToOne(fetch = FetchType.EAGER, optional = false)
	@JoinColumn(name = "card_id", nullable = false)
	private Card card;

	@Enumerated(EnumType.STRING)
	@Column(nullable = false, length = 20)
	private Status status;

	@Column(name = "reject_reason", length = REASON_MAX)
	private String rejectReason;

	@Column(name = "proof_upload_id")
	private UUID proofUploadId;

	@Column(name = "decided_by")
	private UUID decidedBy;

	@Column(name = "decided_at")
	private OffsetDateTime decidedAt;

	@Column(name = "created_at", nullable = false, insertable = false, updatable = false)
	private OffsetDateTime createdAt;

	protected SquadCard() {
	}

	SquadCard(UUID leagueId, UUID userId, Card card) {
		this.id = UUID.randomUUID();
		this.leagueId = leagueId;
		this.userId = userId;
		this.card = card;
		this.status = Status.PENDING;
	}

	void approve(UUID adminId) {
		decide(Status.APPROVED, adminId, null);
		card.verify();
	}

	void reject(UUID adminId, String reason) {
		decide(Status.REJECTED, adminId, reason);
	}

	void remove() {
		this.status = Status.REMOVED;
	}

	private void decide(Status status, UUID adminId, String reason) {
		this.status = status;
		this.rejectReason = reason;
		this.decidedBy = adminId;
		this.decidedAt = OffsetDateTime.now();
	}

	UUID getId() {
		return id;
	}

	/** The screenshot proving the manager owns the card, if one was added. */
	void attachProof(UUID uploadId) {
		this.proofUploadId = uploadId;
	}

	UUID getProofUploadId() {
		return proofUploadId;
	}

	UUID getLeagueId() {
		return leagueId;
	}

	UUID getUserId() {
		return userId;
	}

	Card getCard() {
		return card;
	}

	Status getStatus() {
		return status;
	}

	String getRejectReason() {
		return rejectReason;
	}

	enum Status {

		PENDING, APPROVED, REJECTED, REMOVED;

		/** Pending and approved cards are in the squad and count towards its rules. */
		static final Set<Status> LIVE = EnumSet.of(PENDING, APPROVED);

	}

}
