package com.lansoftprogramming.runeSequence.sequence;


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
			return "(" + subgroup + ")";
		}
		return "?";
	}
}