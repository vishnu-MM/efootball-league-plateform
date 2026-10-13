package com.leagueapp.files;

import java.time.OffsetDateTime;
import java.util.Collection;
import java.util.List;
import java.util.UUID;

import jakarta.persistence.LockModeType;

import org.springframework.data.domain.Limit;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

interface UploadRepository extends JpaRepository<Upload, UUID> {

	/** Locked, so one upload can't be attached in two places at once. */
	@Lock(LockModeType.PESSIMISTIC_WRITE)
	@Query("select u from Upload u where u.id in :ids")
	List<Upload> lockAll(@Param("ids") Collection<UUID> ids);

	List<Upload> findByAttachedAtIsNullAndCreatedAtBefore(OffsetDateTime before, Limit limit);

}
