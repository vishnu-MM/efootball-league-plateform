package com.leagueapp.match;

import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import jakarta.persistence.LockModeType;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

interface SeasonRepository extends JpaRepository<Season, UUID> {

	boolean existsByLeagueIdAndStatus(UUID leagueId, Season.Status status);

	/** The active season, or else the most recent one. */
	Optional<Season> findFirstByLeagueIdOrderByNumberDesc(UUID leagueId);

	@Query("select coalesce(max(s.number), 0) from Season s where s.leagueId = :leagueId")
	int maxNumber(@Param("leagueId") UUID leagueId);

	@Query("""
			select count(p) > 0 from SeasonParticipant p, Season s
			where p.key.seasonId = s.id and s.leagueId = :leagueId and s.status = 'ACTIVE' and p.key.userId = :userId""")
	boolean isInActiveSeason(@Param("leagueId") UUID leagueId, @Param("userId") UUID userId);

}

interface SeasonParticipantRepository extends JpaRepository<SeasonParticipant, SeasonParticipant.Key> {

	List<SeasonParticipant> findByKeySeasonId(UUID seasonId);

}

interface FixtureRepository extends JpaRepository<Fixture, UUID> {

	List<Fixture> findBySeasonIdOrderByRoundAscIdAsc(UUID seasonId);

	boolean existsBySeasonIdAndStatusNot(UUID seasonId, Fixture.Status status);

	/** Row lock so two people can't move the same fixture at the same moment. */
	@Lock(LockModeType.PESSIMISTIC_WRITE)
	@Query("select f from Fixture f where f.id = :id")
	Optional<Fixture> lockById(@Param("id") UUID id);

}

interface ScoreReportRepository extends JpaRepository<ScoreReport, UUID> {

	List<ScoreReport> findByFixtureIdOrderByCreatedAtAsc(UUID fixtureId);

	List<ScoreReport> findByFixtureIdIn(Collection<UUID> fixtureIds);

}
