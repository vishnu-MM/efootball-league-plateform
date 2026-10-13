package com.leagueapp.discord;

/** The kinds of league events an owner can choose to post to Discord. */
enum DiscordTopic {

	/** A result confirmed by the opponent (or an admin on their behalf). */
	RESULTS,
	/** A dispute opened or settled. */
	DISPUTES,
	/** An admin overriding a result or recording a forfeit. */
	CORRECTIONS,
	/** Points deducted or given back. */
	DEDUCTIONS,
	/** A season starting or finishing. */
	SEASONS,
	/** The transfer window opening or closing. */
	TRANSFER_WINDOW

}
