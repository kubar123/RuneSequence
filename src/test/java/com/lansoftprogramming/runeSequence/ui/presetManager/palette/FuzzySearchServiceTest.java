package com.lansoftprogramming.runeSequence.ui.presetManager.palette;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class FuzzySearchServiceTest {

	private final FuzzySearchService service = new FuzzySearchService();

	@Test
	void emptyQueryShouldMatchEverything() {
		assertEquals(100, service.calculateMatchScore("", "anything"));
	}

	@Test
	void queryLongerThanTargetShouldFail() {
		assertEquals(-1, service.calculateMatchScore("longer", "short"));
	}

	@Test
	void shouldScoreFuzzyMatchesAboveThreshold() {
		assertEquals(80, service.calculateMatchScore("abc", "axbc"),
				"Non-contiguous matches should clamp to the max fuzzy score");
		assertTrue(service.matches("abc", "axbc"));
	}

	@Test
	void fuzzyScoreBelowThresholdShouldReturnNoMatch() {
		assertEquals(-1, service.calculateMatchScore("abc", "a0123456789b0123456789c"),
				"Very sparse matches should fall below the minimum score threshold");
	}
}
