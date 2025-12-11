package com.lansoftprogramming.runeSequence.core.sequence.model;


/**
 * Represents a single alternative in a sequence expression.
 * Example: in "A / B", both A and B are Alternatives.
 */
public class Alternative {

	private final String token;                 // Ability name (or macro string)
	private final SequenceDefinition subgroup;  // Nested expression (from parentheses)
	private final AbilitySettingsOverrides abilitySettingsOverrides;

	public Alternative(String token) {
		this(token, null);
	}

	public Alternative(String token, AbilitySettingsOverrides abilitySettingsOverrides) {
		this.token = token;
		this.subgroup = null;
		this.abilitySettingsOverrides = abilitySettingsOverrides;
	}

	public Alternative(SequenceDefinition subgroup) {
		this.token = null;
		this.subgroup = subgroup;
		this.abilitySettingsOverrides = null;
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

	public SequenceDefinition getSubgroup() {
		return subgroup;
	}

	public AbilitySettingsOverrides getAbilitySettingsOverrides() {
		return abilitySettingsOverrides;
	}

	@Override
	public String toString() {
		if (isToken()) {
			return token;
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
