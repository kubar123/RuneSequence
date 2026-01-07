package com.lansoftprogramming.runeSequence.core.detection;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class IconDetectionGraderTest {

	@Test
	void gradesGreenWhenAtOrAboveThreshold() {
		assertEquals(IconDetectionGrader.Grade.GREEN, IconDetectionGrader.grade(0.9d, 0.9d).grade());
		assertEquals(IconDetectionGrader.Grade.GREEN, IconDetectionGrader.grade(0.9d, 0.91d).grade());
	}

	@Test
	void gradesYellowWhenWithinTenPercentBelowThreshold() {
		// delta = (0.9 - 0.81) / 0.9 = 0.10
		assertEquals(IconDetectionGrader.Grade.YELLOW, IconDetectionGrader.grade(0.9d, 0.81d).grade());
	}

	@Test
	void gradesRedWhenWithinTwentyPercentBelowThreshold() {
		// delta = (0.9 - 0.721) / 0.9 < 0.20
		assertEquals(IconDetectionGrader.Grade.RED, IconDetectionGrader.grade(0.9d, 0.721d).grade());
	}

	@Test
	void gradesNotFoundWhenMoreThanTwentyPercentBelowThreshold() {
		// delta = (0.9 - 0.72) / 0.9 = 0.20
		assertEquals(IconDetectionGrader.Grade.NOT_FOUND, IconDetectionGrader.grade(0.9d, 0.72d).grade());
	}

	@Test
	void fallsBackToDefaultThresholdWhenInvalid() {
		assertEquals(IconDetectionGrader.Grade.GREEN, IconDetectionGrader.grade(0.0d, 0.99d).grade());
		assertEquals(IconDetectionGrader.Grade.NOT_FOUND, IconDetectionGrader.grade(Double.NaN, 0.0d).grade());
	}

	@Test
	void supportsCustomTolerancePercentages() {
		// required=0.9, best=0.70 -> delta=0.222...
		assertEquals(
				IconDetectionGrader.Grade.NOT_FOUND,
				IconDetectionGrader.grade(0.9d, 0.70d, 0.10d, 0.20d).grade()
		);
		assertEquals(
				IconDetectionGrader.Grade.RED,
				IconDetectionGrader.grade(0.9d, 0.70d, 0.10d, 0.30d).grade()
		);
	}
}
