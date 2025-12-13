package com.lansoftprogramming.runeSequence.core.sequence.model;

import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Helper for formatting and parsing ability tokens that may contain instance labels,
 * e.g. "abilityKey[*2]".
 */
public final class AbilityToken {

	private static final Pattern TOKEN_WITH_LABEL = Pattern.compile("(.+?)\\[\\*(\\d+)]$");

	private final String abilityKey;
	private final String instanceLabel;

	private AbilityToken(String abilityKey, String instanceLabel) {
		this.abilityKey = abilityKey;
		this.instanceLabel = normalizeLabel(instanceLabel);
	}

	public static AbilityToken of(String abilityKey) {
		return new AbilityToken(abilityKey, null);
	}

	public static AbilityToken of(String abilityKey, String instanceLabel) {
		return new AbilityToken(abilityKey, instanceLabel);
	}

	/**
	 * Parses a serialized token and extracts the ability key and optional instance label.
	 */
	public static AbilityToken parse(String token) {
		if (token == null) {
			return new AbilityToken(null, null);
		}
		Matcher matcher = TOKEN_WITH_LABEL.matcher(token);
		if (matcher.matches()) {
			return new AbilityToken(matcher.group(1), matcher.group(2));
		}
		return new AbilityToken(token, null);
	}

	/**
	 * Formats a token string from the provided ability key and optional instance label.
	 */
	public static String format(String abilityKey, String instanceLabel) {
		return of(abilityKey, instanceLabel).asToken();
	}

	/**
	 * Returns the base ability key from a token, stripping any instance label.
	 */
	public static String baseAbilityKey(String token) {
		return parse(token).getAbilityKey();
	}

	public String getAbilityKey() {
		return abilityKey;
	}

	public Optional<String> getInstanceLabel() {
		return Optional.ofNullable(instanceLabel);
	}

	public boolean hasInstanceLabel() {
		return instanceLabel != null;
	}

	public String asToken() {
		if (abilityKey == null) {
			return null;
		}
		if (instanceLabel == null) {
			return abilityKey;
		}
		return abilityKey + "[*" + instanceLabel + "]";
	}

	private String normalizeLabel(String label) {
		if (label == null) {
			return null;
		}
		String trimmed = label.trim();
		return trimmed.isEmpty() ? null : trimmed;
	}
}
