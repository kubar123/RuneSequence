package com.lansoftprogramming.runeSequence.core.sequence.model;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class AbilitySettingsOverridesTest {

	@Test
	void emptyShouldExposeNoOverrides() {
		AbilitySettingsOverrides overrides = AbilitySettingsOverrides.empty();

		assertTrue(overrides.isEmpty());
		assertTrue(overrides.getTypeOverride().isEmpty());
		assertTrue(overrides.getLevelOverride().isEmpty());
		assertTrue(overrides.getTriggersGcdOverride().isEmpty());
		assertTrue(overrides.getCastDurationOverride().isEmpty());
		assertTrue(overrides.getCooldownOverride().isEmpty());
		assertTrue(overrides.getDetectionThresholdOverride().isEmpty());
		assertTrue(overrides.getMaskOverride().isEmpty());
	}

	@Test
	void builderShouldSupportPartialOverrides() {
		AbilitySettingsOverrides overrides = AbilitySettingsOverrides.builder()
				.triggersGcd(false)
				.detectionThreshold(1.0)
				.build();

		assertFalse(overrides.isEmpty());
		assertEquals(false, overrides.getTriggersGcdOverride().orElseThrow());
		assertEquals(1.0, overrides.getDetectionThresholdOverride().orElseThrow());
		assertTrue(overrides.getCooldownOverride().isEmpty());
	}

	@Test
	void zeroValuesShouldStillCountAsExplicitOverrides() {
		AbilitySettingsOverrides overrides = AbilitySettingsOverrides.builder()
				.level(0)
				.castDuration((short) 0)
				.cooldown((short) 0)
				.detectionThreshold(0.0)
				.build();

		assertFalse(overrides.isEmpty());
		assertEquals(0, overrides.getLevelOverride().orElseThrow());
		assertEquals((short) 0, overrides.getCastDurationOverride().orElseThrow());
		assertEquals((short) 0, overrides.getCooldownOverride().orElseThrow());
		assertEquals(0.0, overrides.getDetectionThresholdOverride().orElseThrow());
	}

	@Test
	void equalsAndHashCodeShouldMatchForSameOverrides() {
		AbilitySettingsOverrides first = AbilitySettingsOverrides.builder()
				.type("Basic")
				.level(20)
				.triggersGcd(true)
				.castDuration((short) 3)
				.cooldown((short) 12)
				.detectionThreshold(0.75)
				.mask("maskA")
				.build();

		AbilitySettingsOverrides second = AbilitySettingsOverrides.builder()
				.type("Basic")
				.level(20)
				.triggersGcd(true)
				.castDuration((short) 3)
				.cooldown((short) 12)
				.detectionThreshold(0.75)
				.mask("maskA")
				.build();

		assertEquals(first, second);
		assertEquals(first.hashCode(), second.hashCode());
	}
}

