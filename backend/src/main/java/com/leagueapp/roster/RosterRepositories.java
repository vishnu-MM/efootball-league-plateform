package com.leagueapp.roster;

import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import jakarta.persistence.LockModeType;

import org.springframework.data.domain.Limit;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

interface CardRepository extends JpaRepository<Card, UUID> {

	@Query("""
			select c from Card c where c.leagueId = :leagueId and lower(c.playerName) = lower(:name)
			and c.cardType = :type and ((:rating is null and c.rating is null) or c.rating = :rating) and c.position = :position""")
	Optional<Card> findSame(@Param("leagueId") UUID leagueId, @Param("name") String name, @Param("type") CardType type,
			@Param("rating") Short rating, @Param("position") Position position);

	/** Verified cards whose player name contains the (already escaped) text, best rated first. */
	@Query("""
			select c from Card c where c.leagueId = :leagueId and c.verified = true
			and lower(c.playerName) like lower(concat('%', :text, '%')) escape '!'
			order by c.rating desc nulls last, c.playerName""")
	List<Card> search(@Param("leagueId") UUID leagueId, @Param("text") String text, Limit limit);

	Optional<Card> findByIdAndLeagueId(UUID id, UUID leagueId);

}

interface SquadCardRepository extends JpaRepository<SquadCard, UUID> {

	List<SquadCard> findByLeagueIdAndUserIdAndStatusInOrderByCreatedAtAsc(UUID leagueId, UUID userId,
			Collection<SquadCard.Status> statuses);

	List<SquadCard> findByLeagueIdAndStatusIn(UUID leagueId, Collection<SquadCard.Status> statuses);

	List<SquadCard> findByLeagueIdAndStatusOrderByCreatedAtAsc(UUID leagueId, SquadCard.Status status);

	long countByLeagueIdAndStatus(UUID leagueId, SquadCard.Status status);

	Optional<SquadCard> findByIdAndLeagueId(UUID id, UUID leagueId);

	boolean existsByLeagueIdAndUserIdAndCardIdAndStatusIn(UUID leagueId, UUID userId, UUID cardId,
			Collection<SquadCard.Status> statuses);

}

interface SquadRulesRepository extends JpaRepository<SquadRules, UUID> {

	/** Creates the league's default rules (no limits, window open) if it has none yet. */
	@Modifying
	@Query(value = "insert into squad_rules (league_id) values (:leagueId) on conflict (league_id) do nothing",
			nativeQuery = true)
	void createIfAbsent(@Param("leagueId") UUID leagueId);

	/** Row lock that serialises squad changes, approvals and rule changes in one league. */
	@Lock(LockModeType.PESSIMISTIC_WRITE)
	@Query("select r from SquadRules r where r.leagueId = :leagueId")
	Optional<SquadRules> lockByLeagueId(@Param("leagueId") UUID leagueId);

}
