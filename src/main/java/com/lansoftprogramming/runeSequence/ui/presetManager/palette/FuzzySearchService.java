package com.lansoftprogramming.runeSequence.ui.presetManager.palette;

/**
 * Service for performing fuzzy string matching on ability names.
 * Uses character-by-character matching with position weighting.
 */
public class FuzzySearchService {

	private static final int MIN_SCORE_THRESHOLD = 30;

	/**
	 * Calculates fuzzy match score between query and target string.
	 * Higher scores indicate better matches.
	 *
	 * @param query  Search query (case-insensitive)
	 * @param target Target string to match against
	 * @return Match score (0-100), or -1 if no match
	 */
	public int calculateMatchScore(String query, String target) {
		if (query == null || query.isEmpty()) {
			return 100; // Empty query matches everything
		}
		if (target == null || target.isEmpty()) {
			return -1;
		}

		String queryLower = query.toLowerCase();
		String targetLower = target.toLowerCase();

		// Exact match gets highest score
		if (targetLower.equals(queryLower)) {
			return 100;
		}

		// Check if target starts with query (high priority)
		if (targetLower.startsWith(queryLower)) {
			return 95;
		}

		// Check if target contains query as substring (medium priority)
		if (targetLower.contains(queryLower)) {
			return 85;
		}

		// Fuzzy character-by-character matching
		return fuzzyMatch(queryLower, targetLower);
	}

	/**
	 * Performs fuzzy character matching with position weighting.
	 */
	private int fuzzyMatch(String query, String target) {
		int queryLen = query.length();
		int targetLen = target.length();

		if (queryLen > targetLen) {
			return -1;
		}

		int matchCount = 0;
		int consecutiveMatches = 0;
		int maxConsecutive = 0;
		int lastMatchIndex = -1;
		int queryIndex = 0;

		for (int targetIndex = 0; targetIndex < targetLen && queryIndex < queryLen; targetIndex++) {
			if (target.charAt(targetIndex) == query.charAt(queryIndex)) {
				matchCount++;
				queryIndex++;

				// Track consecutive matches for bonus scoring
				if (targetIndex == lastMatchIndex + 1) {
					consecutiveMatches++;
					maxConsecutive = Math.max(maxConsecutive, consecutiveMatches);
				} else {
					consecutiveMatches = 1;
				}
				lastMatchIndex = targetIndex;
			}
		}

		// Must match all query characters
		if (matchCount != queryLen) {
			return -1;
		}

		// Calculate score based on:
		// - Percentage of characters matched
		// - Position of first match (earlier is better)
		// - Consecutive character runs (bonus)
		int baseScore = (matchCount * 100) / targetLen;
		int positionBonus = (targetLen - lastMatchIndex) * 2;
		int consecutiveBonus = maxConsecutive * 5;

		int finalScore = baseScore + positionBonus + consecutiveBonus;

		return finalScore >= MIN_SCORE_THRESHOLD ? Math.min(finalScore, 80) : -1;
	}

	/**
	 * Determines if a string matches the query.
	 */
	public boolean matches(String query, String target) {
		return calculateMatchScore(query, target) >= 0;
	}
}