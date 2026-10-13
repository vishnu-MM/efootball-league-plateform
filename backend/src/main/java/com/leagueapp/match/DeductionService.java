package com.leagueapp.match;

import java.util.UUID;

import org.springframework.context.ApplicationEventPublisher;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import com.leagueapp.league.LeagueService;
import com.leagueapp.league.LeagueService.LeagueAccess;

/** Point deductions in the active season: owners and admins take points from a participant, or give them back. */
@Service
@Transactional(readOnly = true)
class DeductionService {

	private final LeagueService leagueService;
	private final SeasonRepository seasons;
	private final SeasonParticipantRepository participants;
	private final PointAdjustmentRepository adjustments;
	private final ApplicationEventPublisher events;

	DeductionService(LeagueService leagueService, SeasonRepository seasons, SeasonParticipantRepository participants,
			PointAdjustmentRepository adjustments, ApplicationEventPublisher events) {
		this.leagueService = leagueService;
		this.seasons = seasons;
		this.participants = participants;
		this.adjustments = adjustments;
		this.events = events;
	}

	@Transactional
	PointAdjustment deduct(String slug, UUID actorId, UUID userId, int points, String reason) {
		String why = FixtureService.requireReason(reason);
		if (points < 1 || points > PointAdjustment.MAX_POINTS) {
			throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
					"Deduct between 1 and " + PointAdjustment.MAX_POINTS + " points");
		}
		Season season = activeSeason(requireManager(slug, actorId));
		if (userId == null || !participants.existsById(new SeasonParticipant.Key(season.getId(), userId))) {
			throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "That player isn't in this season");
		}
		PointAdjustment deduction = adjustments.save(new PointAdjustment(season.getId(), userId, points, why, actorId));
		events.publishEvent(new MatchEvents.PointsDeducted(season.getLeagueId(), actorId, season.getId(),
				deduction.getId(), userId, points, why));
		return deduction;
	}

	@Transactional
	void revoke(String slug, UUID actorId, UUID deductionId, String reason) {
		String why = FixtureService.requireReason(reason);
		LeagueAccess league = requireManager(slug, actorId);
		PointAdjustment deduction = adjustments.lockById(deductionId)
			.filter(d -> seasons.findById(d.getSeasonId()).filter(s -> s.getLeagueId().equals(league.leagueId())).isPresent())
			.orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Deduction not found"));
		Season season = seasons.findById(deduction.getSeasonId()).orElseThrow();
		if (!season.isActive()) {
			throw new ResponseStatusException(HttpStatus.CONFLICT,
					"This season is finished, so its deductions can't be changed");
		}
		if (deduction.isRevoked()) {
			throw new ResponseStatusException(HttpStatus.CONFLICT, "This deduction has already been revoked");
		}
		deduction.revoke(actorId, why);
		events.publishEvent(new MatchEvents.DeductionRevoked(league.leagueId(), actorId, season.getId(),
				deduction.getId(), deduction.getUserId(), deduction.getPoints(), why));
	}

	private LeagueAccess requireManager(String slug, UUID actorId) {
		LeagueAccess league = leagueService.access(slug, actorId);
		if (!league.canManage()) {
			throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Only the owner or an admin can change points");
		}
		return league;
	}

	private Season activeSeason(LeagueAccess league) {
		return seasons.findFirstByLeagueIdOrderByNumberDesc(league.leagueId())
			.filter(Season::isActive)
			.orElseThrow(() -> new ResponseStatusException(HttpStatus.CONFLICT, "There's no season in progress"));
	}

}
