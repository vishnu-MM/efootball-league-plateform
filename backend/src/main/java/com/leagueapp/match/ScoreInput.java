package com.leagueapp.match;

import java.util.List;

import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

/** A score as entered: goals per side, optional typed scorers (with optional assists), optional note. */
record ScoreInput(int homeGoals, int awayGoals, List<GoalInput> goals, String note) {

	static final int MAX_GOALS = 99;

	void validate() {
		if (homeGoals < 0 || awayGoals < 0 || homeGoals > MAX_GOALS || awayGoals > MAX_GOALS) {
			throw badRequest("Goals must be between 0 and " + MAX_GOALS);
		}
		List<ScoreReport.Goal> clean = cleanGoals();
		long home = clean.stream().filter(g -> g.side() == ScoreReport.Side.HOME).count();
		long away = clean.size() - home;
		if (home > homeGoals || away > awayGoals) {
			throw badRequest("A side can't have more scorers than goals");
		}
		if (note != null && note.strip().length() > ScoreReport.NOTE_MAX) {
			throw badRequest("Keep the note under " + ScoreReport.NOTE_MAX + " characters");
		}
	}

	/** Scorers with blank names dropped and names trimmed to the column size. */
	List<ScoreReport.Goal> cleanGoals() {
		if (goals == null) {
			return List.of();
		}
		return goals.stream()
			.filter(g -> g != null && g.side() != null && g.scorer() != null && !g.scorer().isBlank())
			.map(g -> new ScoreReport.Goal(g.side(), trim(g.scorer()), g.assist() == null || g.assist().isBlank() ? null : trim(g.assist())))
			.toList();
	}

	String cleanNote() {
		return note == null || note.isBlank() ? null : note.strip();
	}

	private static String trim(String name) {
		String s = name.strip();
		return s.length() > ScoreReport.NAME_MAX ? s.substring(0, ScoreReport.NAME_MAX) : s;
	}

	private static ResponseStatusException badRequest(String message) {
		return new ResponseStatusException(HttpStatus.BAD_REQUEST, message);
	}

	record GoalInput(ScoreReport.Side side, String scorer, String assist) {
	}

}
