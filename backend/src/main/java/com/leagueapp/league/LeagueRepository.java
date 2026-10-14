package com.leagueapp.league;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import jakarta.persistence.LockModeType;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

interface LeagueRepository extends JpaRepository<League, UUID> {

	List<League> findByVisibilityAndDeletedAtIsNullAndHiddenAtIsNullOrderByCreatedAtDesc(League.Visibility visibility);

	List<League> findByDeletedAtBefore(OffsetDateTime before);

	Optional<League> findBySlug(String slug);

	boolean existsBySlug(String slug);

	/** Row lock that serialises invite changes for one league. */
	@Lock(LockModeType.PESSIMISTIC_WRITE)
	@Query("select l from League l where l.id = :id")
	Optional<League> lockById(@Param("id") UUID id);

}

interface LeagueMembershipRepository extends JpaRepository<LeagueMembership, LeagueMembership.Key> {

	List<LeagueMembership> findByKeyUserId(UUID userId);

	List<LeagueMembership> findByKeyLeagueId(UUID leagueId);

	long countByKeyLeagueId(UUID leagueId);

	/** Adds a manager; does nothing if they are already a member (so a double tap can't fail). */
	@Modifying
	@Query(value = """
			insert into league_membership (league_id, user_id, role) values (:leagueId, :userId, 'MANAGER')
			on conflict (league_id, user_id) do nothing""", nativeQuery = true)
	int addManagerIfAbsent(@Param("leagueId") UUID leagueId, @Param("userId") UUID userId);

}

interface LeagueInviteRepository extends JpaRepository<LeagueInvite, String> {

	Optional<LeagueInvite> findByLeagueIdAndRevokedAtIsNull(UUID leagueId);

}

interface ClubRepository extends JpaRepository<Club, LeagueMembership.Key> {

	List<Club> findByKeyLeagueId(UUID leagueId);

	@Query("select count(c) > 0 from Club c where c.key.leagueId = :leagueId and lower(c.name) = lower(:name) and c.key.userId <> :userId")
	boolean nameTakenByOther(@Param("leagueId") UUID leagueId, @Param("name") String name, @Param("userId") UUID userId);

}
