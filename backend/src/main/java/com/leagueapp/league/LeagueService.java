package com.leagueapp.league;

import java.util.List;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional(readOnly = true)
public class LeagueService {

	private final LeagueRepository leagues;

	LeagueService(LeagueRepository leagues) {
		this.leagues = leagues;
	}

	public List<League> publicLeagues() {
		return leagues.findByVisibilityOrderByCreatedAtDesc(League.Visibility.PUBLIC);
	}

}
