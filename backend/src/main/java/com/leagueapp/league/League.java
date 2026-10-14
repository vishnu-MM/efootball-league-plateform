package com.leagueapp.league;

import java.time.OffsetDateTime;
import java.util.UUID;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

@Entity
@Table(name = "league")
public class League {

	@Id
	private UUID id;

	@Column(nullable = false, length = 80)
	private String name;

	@Column(nullable = false, unique = true, length = 80)
	private String slug;

	@Column(name = "owner_id", nullable = false)
	private UUID ownerId;

	@Enumerated(EnumType.STRING)
	@Column(nullable = false, length = 20)
	private Visibility visibility;

	@Column(name = "created_at", nullable = false, insertable = false, updatable = false)
	private OffsetDateTime createdAt;

	protected League() {
	}

	public League(String name, String slug, UUID ownerId, Visibility visibility) {
		this.id = UUID.randomUUID();
		this.name = name;
		this.slug = slug;
		this.ownerId = ownerId;
		this.visibility = visibility;
	}

	public UUID getId() {
		return id;
	}

	public String getName() {
		return name;
	}

	public String getSlug() {
		return slug;
	}

	public UUID getOwnerId() {
		return ownerId;
	}

	public Visibility getVisibility() {
		return visibility;
	}

	public OffsetDateTime getCreatedAt() {
		return createdAt;
	}

	void rename(String name) {
		this.name = name;
	}

	void changeVisibility(Visibility visibility) {
		this.visibility = visibility;
	}

	void transferTo(UUID newOwnerId) {
		this.ownerId = newOwnerId;
	}

	public enum Visibility {
		PUBLIC, PRIVATE
	}

}
