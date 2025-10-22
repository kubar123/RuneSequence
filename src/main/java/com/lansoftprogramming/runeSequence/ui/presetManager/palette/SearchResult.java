package com.lansoftprogramming.runeSequence.ui.presetManager.palette;

import com.lansoftprogramming.runeSequence.ui.shared.model.AbilityItem;

/**
 * Represents a search result with match information.
 */
public class SearchResult {
	private final AbilityItem ability;
	private final int matchScore;
	private final boolean matches;

	public SearchResult(AbilityItem ability, int matchScore) {
		this.ability = ability;
		this.matchScore = matchScore;
		this.matches = matchScore >= 0;
	}

	public AbilityItem getAbility() {
		return ability;
	}

	public int getMatchScore() {
		return matchScore;
	}

	public boolean matches() {
		return matches;
	}
}