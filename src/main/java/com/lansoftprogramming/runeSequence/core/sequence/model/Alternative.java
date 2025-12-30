package com.lansoftprogramming.runeSequence.core.sequence.model;

import java.util.List;

/**
 * Represents a single alternative in a sequence expression.
 * Example: in "A / B", both A and B are Alternatives.
 */
public class Alternative {

	private final String token;                 // Base ability name (or macro string)
	private final String instanceLabel;         // Optional per-instance label (e.g. "1" in "cane[*1]")
	private final SequenceDefinition subgroup;  // Nested expression (from parentheses)
	private final AbilitySettingsOverrides abilitySettingsOverrides;
	private final List<String> abilityModifiers; // Prefix modifiers (e.g. "gmaul" for "gmaul+eofspec")

	public Alternative(String token) {
		this(token, null, null, List.of());
	}

	public Alternative(String token, AbilitySettingsOverrides abilitySettingsOverrides) {
		this(token, null, abilitySettingsOverrides, List.of());
	}

	public Alternative(String token, String instanceLabel, AbilitySettingsOverrides abilitySettingsOverrides) {
		this(token, instanceLabel, abilitySettingsOverrides, List.of());
	}

	public Alternative(String token,
	                   String instanceLabel,
	                   AbilitySettingsOverrides abilitySettingsOverrides,
	                   List<String> abilityModifiers) {
		this.token = token;
		this.instanceLabel = instanceLabel;
		this.subgroup = null;
		this.abilitySettingsOverrides = abilitySettingsOverrides;
		this.abilityModifiers = abilityModifiers != null ? List.copyOf(abilityModifiers) : List.of();
	}

	public Alternative(SequenceDefinition subgroup) {
		this.token = null;
		this.instanceLabel = null;
		this.subgroup = subgroup;
		this.abilitySettingsOverrides = null;
		this.abilityModifiers = List.of();
	}

	public boolean isToken() {
		return token != null;
	}

	public boolean isGroup() {
		return subgroup != null;
	}

	public String getToken() {
		return token;
	}

	public String getInstanceLabel() {
		return instanceLabel;
	}

	public SequenceDefinition getSubgroup() {
		return subgroup;
	}

	public AbilitySettingsOverrides getAbilitySettingsOverrides() {
		return abilitySettingsOverrides;
	}

	public List<String> getAbilityModifiers() {
		return abilityModifiers;
	}

	public boolean hasAbilityModifiers() {
		return abilityModifiers != null && !abilityModifiers.isEmpty();
	}

	public Alternative withAbilityModifiers(List<String> modifiers) {
		if (!isToken()) {
			return this;
		}
		return new Alternative(token, instanceLabel, abilitySettingsOverrides, modifiers);
	}

	@Override
	public String toString() {
		if (isToken()) {
			String baseToken = AbilityToken.format(token, instanceLabel);
			if (abilityModifiers == null || abilityModifiers.isEmpty()) {
				return baseToken;
			}
			StringBuilder sb = new StringBuilder();
			for (int i = 0; i < abilityModifiers.size(); i++) {
				if (i > 0) {
					sb.append(" + ");
				}
				sb.append(abilityModifiers.get(i));
			}
			sb.append(" + ").append(baseToken);
			return sb.toString();
		}
		if (isGroup()) {
			String subgroupStr = subgroup.toString();
			// This heuristic avoids double-parentheses when a group contains just a single OR-expression.
			boolean isSimpleOrGroup = subgroup.getSteps().size() == 1 &&
					subgroup.getSteps().get(0).getTerms().size() == 1 &&
					subgroup.getSteps().get(0).getTerms().get(0).getAlternatives().size() > 1;

			if (isSimpleOrGroup) {
				return subgroupStr;
			}
			return "(" + subgroupStr + ")";
		}
		return "?";
	}
}
