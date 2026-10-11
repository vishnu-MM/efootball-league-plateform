package com.leagueapp.league;

import java.util.List;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;

interface LeagueRepository extends JpaRepository<League, UUID> {

	List<League> findByVisibilityOrderByCreatedAtDesc(League.Visibility visibility);

}
