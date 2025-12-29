package com.lansoftprogramming.runeSequence.core.detection;

import com.lansoftprogramming.runeSequence.application.TemplateCache;
import com.lansoftprogramming.runeSequence.infrastructure.config.AbilityConfig;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertEquals;

class TemplateDetectorTest {
	@Test
	void normalizeAbilityKeyForLookup_shouldStripStackSuffix() {
		assertEquals("gfury", TemplateDetector.normalizeAbilityKeyForLookup("gfury[*1]"));
		assertEquals("gfury", TemplateDetector.normalizeAbilityKeyForLookup(" gfury[*12] "));
	}

	@Test
	void normalizeAbilityKeyForLookup_shouldStripMultipleSuffixes() {
		assertEquals("a", TemplateDetector.normalizeAbilityKeyForLookup("a[*1][*2]"));
	}

	@Test
	void getThresholdForTemplate_shouldClampInvalidOverridesAndFallbackToConfig(@TempDir Path tempDir) throws Exception {
		TemplateCache cache = new TemplateCache(tempDir);
		AbilityConfig abilityConfig = new AbilityConfig();
		AbilityConfig.AbilityData data = new AbilityConfig.AbilityData();
		data.setDetectionThreshold(0.7d);
		abilityConfig.putAbility("a", data);

		TemplateDetector detector = new TemplateDetector(cache, abilityConfig);

		double clamped = detector.getThresholdForTemplate("a", 2.0d);
		assertEquals(1.0d, clamped);

		double fromConfig = detector.getThresholdForTemplate("a", Double.NaN);
		assertEquals(0.7d, fromConfig);
	}

	@Test
	void getThresholdForTemplate_shouldUseDefaultWhenNoOverrideOrConfig(@TempDir Path tempDir) {
		TemplateCache cache = new TemplateCache(tempDir);
		TemplateDetector detector = new TemplateDetector(cache, new AbilityConfig());

		assertEquals(0.99d, detector.getThresholdForTemplate("missing", null));
	}
}
