package com.leagueapp.league;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;

interface LeagueRepository extends JpaRepository<League, UUID> {

	List<League> findByVisibilityOrderByCreatedAtDesc(League.Visibility visibility);

	Optional<League> findBySlug(String slug);

	boolean existsBySlug(String slug);

}

interface LeagueMembershipRepository extends JpaRepository<LeagueMembership, LeagueMembership.Key> {

	List<LeagueMembership> findByKeyUserId(UUID userId);

	long countByKeyLeagueId(UUID leagueId);

}
