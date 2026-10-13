package com.leagueapp.files;

import java.util.Optional;

/** Recognises PNG, JPEG and WebP by their first bytes, whatever the file name or the browser claim. */
final class ImageTypes {

	private ImageTypes() {
	}

	static Optional<String> detect(byte[] bytes) {
		if (startsWith(bytes, 0, 0x89, 'P', 'N', 'G', 0x0D, 0x0A, 0x1A, 0x0A)) {
			return Optional.of("image/png");
		}
		if (startsWith(bytes, 0, 0xFF, 0xD8, 0xFF)) {
			return Optional.of("image/jpeg");
		}
		if (startsWith(bytes, 0, 'R', 'I', 'F', 'F') && startsWith(bytes, 8, 'W', 'E', 'B', 'P')) {
			return Optional.of("image/webp");
		}
		return Optional.empty();
	}

	private static boolean startsWith(byte[] bytes, int offset, int... expected) {
		if (bytes.length < offset + expected.length) {
			return false;
		}
		for (int i = 0; i < expected.length; i++) {
			if ((bytes[offset + i] & 0xFF) != expected[i]) {
				return false;
			}
		}
		return true;
	}

}
