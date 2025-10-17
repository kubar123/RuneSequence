package com.lansoftprogramming.runeSequence.core.sequence.model;


/**
 * Represents a single alternative in a sequence expression.
 * Example: in "A / B", both A and B are Alternatives.
 */
public class Alternative {

	private final String token;                 // Ability name (or macro string)
	private final SequenceDefinition subgroup;  // Nested expression (from parentheses)

	public Alternative(String token) {
		this.token = token;
		this.subgroup = null;
	}

	public Alternative(SequenceDefinition subgroup) {
		this.token = null;
		this.subgroup = subgroup;
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