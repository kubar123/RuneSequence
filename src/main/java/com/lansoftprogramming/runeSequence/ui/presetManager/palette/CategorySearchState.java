package com.lansoftprogramming.runeSequence.ui.presetManager.palette;

import java.util.List;

/**
 * Holds search state for a category tab.
 */
public class CategorySearchState {
	private final String categoryName;
	private final List<SearchResult> results;
	private final int matchCount;

	public CategorySearchState(String categoryName, List<SearchResult> results) {
		this.categoryName = categoryName;
		this.results = results;
		this.matchCount = (int) results.stream().filter(SearchResult::matches).count();
	}

	public String getCategoryName() {
		return categoryName;
	}

	public List<SearchResult> getResults() {
		return results;
	}

	public int getMatchCount() {
		return matchCount;
	}

	public boolean hasMatches() {
		return matchCount > 0;
	}

	public String getFormattedTabTitle() {
		if (matchCount > 0) {
			return matchCount + " - " + categoryName;
		}
		return categoryName;
	}
}