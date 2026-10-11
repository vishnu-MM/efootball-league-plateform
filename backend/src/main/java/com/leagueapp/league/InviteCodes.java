package com.leagueapp.league;

import java.security.SecureRandom;

/** Random, unguessable invite codes: 10 base-62 characters, about 59 bits. */
final class InviteCodes {

	static final int LENGTH = 10;

	private static final String ALPHABET = "0123456789ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz";
	private static final SecureRandom RANDOM = new SecureRandom();

	private InviteCodes() {
	}

	static String next() {
		StringBuilder code = new StringBuilder(LENGTH);
		for (int i = 0; i < LENGTH; i++) {
			code.append(ALPHABET.charAt(RANDOM.nextInt(ALPHABET.length())));
		}
		return code.toString();
	}

}
