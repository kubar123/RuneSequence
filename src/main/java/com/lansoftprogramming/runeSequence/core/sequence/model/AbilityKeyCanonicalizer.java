package com.lansoftprogramming.runeSequence.core.sequence.model;

import java.util.regex.Pattern;

public final class AbilityKeyCanonicalizer {
	private static final Pattern ABILITY_STACK_SUFFIX = Pattern.compile("\\[\\*\\d+\\]");

	private AbilityKeyCanonicalizer() {
	}

	/**
	 * Canonicalizes ability keys for consistent lookup across config, caches, and runtime flows.
	 * <p>
	 * Currently strips stack suffixes like {@code "[*2]"} and trims whitespace.
	 */
	public static String canonicalizeForLookup(String abilityKey) {
		if (abilityKey == null) {
			return null;
		}
		String normalized = ABILITY_STACK_SUFFIX.matcher(abilityKey).replaceAll("").trim();
		return normalized.isEmpty() ? abilityKey : normalized;
	}
}

