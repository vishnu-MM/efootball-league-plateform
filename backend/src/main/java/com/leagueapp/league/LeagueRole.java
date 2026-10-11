package com.leagueapp.league;

/** A member's role in one league. Declaration order is rank order (owner first). */
public enum LeagueRole {

	OWNER, ADMIN, MANAGER;

	/** Owners and admins run the league: invites, seasons, confirming and resolving results. */
	public boolean canManageLeague() {
		return this == OWNER || this == ADMIN;
	}

}
