package com.leagueapp.match;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;

import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

/**
 * A score as entered: goals per side, optional scorers (with optional assists), optional note. A side whose manager
 * has approved squad cards picks scorers from them by id; a side without one types names.
 */
record ScoreInput(int homeGoals, int awayGoals, List<GoalInput> goals, String note, MvpInput mvp,
		List<UUID> evidence) {

	static final int MAX_GOALS = 99;

	static final int MAX_EVIDENCE = 3;

	/** Screenshot upload ids, without duplicates; at most three. */
	List<UUID> cleanEvidence() {
		if (evidence == null) {
			return List.of();
		}
		List<UUID> ids = evidence.stream().filter(Objects::nonNull).distinct().toList();
		if (ids.size() > MAX_EVIDENCE) {
			throw badRequest("Add up to " + MAX_EVIDENCE + " screenshots");
		}
		return ids;
	}

	/**
	 * Checks the score and turns the scorers into goals to store.
	 * @param squads each side's approved cards (squad card id → player name); empty for a side that types names
	 */
	List<ScoreReport.Goal> validGoals(Map<ScoreReport.Side, Map<UUID, String>> squads) {
		if (homeGoals < 0 || awayGoals < 0 || homeGoals > MAX_GOALS || awayGoals > MAX_GOALS) {
			throw badRequest("Goals must be between 0 and " + MAX_GOALS);
		}
		List<ScoreReport.Goal> clean = new ArrayList<>();
		if (goals != null) {
			for (GoalInput goal : goals) {
				if (goal != null && goal.side() != null) {
					Map<UUID, String> squad = squads.getOrDefault(goal.side(), Map.of());
					ScoreReport.Goal resolved = squad.isEmpty() ? typed(goal) : fromSquad(goal, squad);
					if (resolved != null) {
						clean.add(resolved);
					}
				}
			}
		}
		long home = clean.stream().filter(g -> g.side() == ScoreReport.Side.HOME).count();
		long away = clean.size() - home;
		if (home > homeGoals || away > awayGoals) {
			throw badRequest("A side can't have more scorers than goals");
		}
		if (note != null && note.strip().length() > ScoreReport.NOTE_MAX) {
			throw badRequest("Keep the note under " + ScoreReport.NOTE_MAX + " characters");
		}
		return clean;
	}

	/**
	 * The MVP, if one was picked: from that side's squad when it has one, else a typed name. Blank means none.
	 * @param squads each side's approved cards (squad card id → player name); empty for a side that types names
	 */
	ScoreReport.Mvp validMvp(Map<ScoreReport.Side, Map<UUID, String>> squads) {
		if (mvp == null || mvp.side() == null || (mvp.cardId() == null && blank(mvp.name()))) {
			return null;
		}
		Map<UUID, String> squad = squads.getOrDefault(mvp.side(), Map.of());
		if (squad.isEmpty()) {
			if (mvp.cardId() != null) {
				throw badRequest("That side has no approved squad yet, so type the MVP's name");
			}
			return new ScoreReport.Mvp(mvp.side(), trim(mvp.name()), null);
		}
		String name = mvp.cardId() == null ? null : squad.get(mvp.cardId());
		if (name == null) {
			throw badRequest("The MVP must be an approved card in that side's squad");
		}
		return new ScoreReport.Mvp(mvp.side(), trim(name), mvp.cardId());
	}

	String cleanNote() {
		return note == null || note.isBlank() ? null : note.strip();
	}

	/** A side without a squad: typed names, blank scorers dropped. */
	private static ScoreReport.Goal typed(GoalInput goal) {
		if (goal.scorerCardId() != null || goal.assistCardId() != null) {
			throw badRequest("That side has no approved squad yet, so type the scorer's name");
		}
		if (blank(goal.scorer())) {
			return null;
		}
		return new ScoreReport.Goal(goal.side(), trim(goal.scorer()), blank(goal.assist()) ? null : trim(goal.assist()),
				null, null);
	}

	/** A side with a squad: scorer and assist are approved cards of that side's manager. */
	private static ScoreReport.Goal fromSquad(GoalInput goal, Map<UUID, String> squad) {
		if (goal.scorerCardId() == null) {
			if (!blank(goal.scorer()) || !blank(goal.assist()) || goal.assistCardId() != null) {
				throw badRequest("Pick scorers and assists from the squad");
			}
			return null;
		}
		if (goal.assistCardId() == null && !blank(goal.assist())) {
			throw badRequest("Pick scorers and assists from the squad");
		}
		String scorer = squad.get(goal.scorerCardId());
		String assist = goal.assistCardId() == null ? null : squad.get(goal.assistCardId());
		if (scorer == null || (goal.assistCardId() != null && assist == null)) {
			throw badRequest("Scorers and assists must be approved cards in that side's squad");
		}
		return new ScoreReport.Goal(goal.side(), trim(scorer), assist == null ? null : trim(assist), goal.scorerCardId(),
				goal.assistCardId());
	}

	private static boolean blank(String text) {
		return text == null || text.isBlank();
	}

	private static String trim(String name) {
		String s = name.strip();
		return s.length() > ScoreReport.NAME_MAX ? s.substring(0, ScoreReport.NAME_MAX) : s;
	}

	private static ResponseStatusException badRequest(String message) {
		return new ResponseStatusException(HttpStatus.BAD_REQUEST, message);
	}

	/** The MVP: a squad card id, or a typed name for a side without a squad. */
	record MvpInput(ScoreReport.Side side, UUID cardId, String name) {
	}

	/** A scorer: a squad card id (with an optional assisting card), or a typed name for a side without a squad. */
	record GoalInput(ScoreReport.Side side, String scorer, String assist, UUID scorerCardId, UUID assistCardId) {
	}

}
