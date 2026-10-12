package com.leagueapp.roster;

/** eFootball card types. A new type in the game is a new constant here (and its label in the frontend). */
public enum CardType {

	STANDARD("Standard"), FEATURED("Featured"), HIGHLIGHT("Highlight"), POTW("POTW"), EPIC("Epic"),
	BIG_TIME("Big Time"), SHOW_TIME("Show Time"), LEGEND("Legend"), TREND("Trend");

	private final String label;

	CardType(String label) {
		this.label = label;
	}

	String label() {
		return label;
	}

}
