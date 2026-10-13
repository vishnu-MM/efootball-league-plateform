package com.leagueapp.activity;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

import org.springframework.data.domain.Limit;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

interface ActivityRepository extends JpaRepository<LeagueActivity, UUID> {

	/** Newest first, older than {@code before}; admin actions only if asked. */
	@Query("""
			select a from LeagueActivity a
			where a.leagueId = :leagueId and a.createdAt < :before and (:adminOnly = false or a.adminAction = true)
			order by a.createdAt desc""")
	List<LeagueActivity> page(@Param("leagueId") UUID leagueId, @Param("before") OffsetDateTime before,
			@Param("adminOnly") boolean adminOnly, Limit limit);

}
