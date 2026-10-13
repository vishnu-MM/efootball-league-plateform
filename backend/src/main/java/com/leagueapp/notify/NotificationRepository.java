package com.leagueapp.notify;

import java.time.OffsetDateTime;
import java.util.Collection;
import java.util.List;
import java.util.UUID;

import org.springframework.data.domain.Limit;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

interface NotificationRepository extends JpaRepository<Notification, UUID> {

	List<Notification> findByUserIdAndCreatedAtBeforeOrderByCreatedAtDesc(UUID userId, OffsetDateTime before, Limit limit);

	long countByUserIdAndReadAtIsNull(UUID userId);

	@Modifying
	@Query("update Notification n set n.readAt = :now where n.userId = :userId and n.readAt is null")
	int markAllRead(@Param("userId") UUID userId, @Param("now") OffsetDateTime now);

	@Modifying
	@Query("update Notification n set n.readAt = :now where n.userId = :userId and n.readAt is null and n.id in :ids")
	int markRead(@Param("userId") UUID userId, @Param("ids") Collection<UUID> ids, @Param("now") OffsetDateTime now);

}
