package com.lansoftprogramming.runeSequence.core.sequence.parser;

/**
 * Shared helpers for cleaning rotation expression strings copied from rich text sources.
 * These sources may introduce invisible characters that break parsing or validation.
 */
public final class ExpressionSanitizer {

	private ExpressionSanitizer() {
	}

	/**
	 * Removes invisible / exotic whitespace characters commonly introduced by rich text copy/paste.
	 * Does not remove newlines; callers that need line trimming should call {@link String#trim()} after.
	 */
	public static String removeInvisibles(String input) {
		if (input == null || input.isEmpty()) {
			return input;
		}

		StringBuilder out = new StringBuilder(input.length());
		for (int i = 0; i < input.length(); i++) {
			char c = input.charAt(i);
			if (isInvisibleNoise(c)) {
				continue;
			}
			out.append(c);
		}
		return out.toString();
	}

	/**
	 * Strips a single pair of surrounding double quotes when the trimmed input is entirely quoted.
	 */
	public static String stripSurroundingQuotes(String input) {
		if (input == null || input.isEmpty()) {
			return input;
		}

		String trimmed = input.trim();
		if (trimmed.length() < 2) {
			return input;
		}
		if (trimmed.charAt(0) != '"' || trimmed.charAt(trimmed.length() - 1) != '"') {
			return input;
		}
		String inner = trimmed.substring(1, trimmed.length() - 1);
		return inner.trim();
	}

	private static boolean isInvisibleNoise(char c) {
		return c == '\u200B' // zero-width space
				|| c == '\u200C' // zero-width non-joiner
				|| c == '\u200D' // zero-width joiner
				|| c == '\u2060' // word joiner
				|| c == '\uFEFF' // BOM / zero-width no-break space
				|| c == '\u00A0' // no-break space
				|| c == '\u202F' // narrow no-break space
				|| c == '\u200E' // left-to-right mark
				|| c == '\u200F' // right-to-left mark
				|| c == '\u180E'; // Mongolian vowel separator (deprecated)
	}
}

