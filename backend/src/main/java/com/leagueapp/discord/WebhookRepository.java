package com.leagueapp.discord;

import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;

interface WebhookRepository extends JpaRepository<LeagueWebhook, UUID> {
}
