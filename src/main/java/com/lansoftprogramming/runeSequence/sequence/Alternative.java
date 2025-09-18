package com.lansoftprogramming.runeSequence.sequence;


/**
 * Represents a single alternative in a sequence expression.
 * Example: in "A / B", both A and B are Alternatives.
 */
public class Alternative {

	private final String token;   // Ability name (or macro string)
	private final Step group;     // Optional nested group (from parentheses)

	public Alternative(String token) {
		this.token = token;
		this.group = null;
	}

	public Alternative(Step group) {
		this.token = null;
		this.group = group;
	}

	public boolean isToken() {
		return token != null;
	}

	public boolean isGroup() {
		return group != null;
	}

	public String getToken() {
		return token;
	}

	public Step getGroup() {
		return group;
	}

	@Override
	public String toString() {
		if (isToken()) {
			return token;
		}
		if (isGroup()) {
			return "(" + group + ")";
		}
		return "?";
	}
}
