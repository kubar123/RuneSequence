package com.lansoftprogramming.runeSequence.core.detection;

import com.lansoftprogramming.runeSequence.application.TemplateCache;
import com.lansoftprogramming.runeSequence.infrastructure.config.AbilityConfig;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.lang.reflect.Method;
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

		Method method = TemplateDetector.class.getDeclaredMethod("getThresholdForTemplate", String.class, Double.class);
		method.setAccessible(true);

		double clamped = (double) method.invoke(detector, "a", 2.0d);
		assertEquals(1.0d, clamped);

		double fromConfig = (double) method.invoke(detector, "a", Double.NaN);
		assertEquals(0.7d, fromConfig);
	}
}
