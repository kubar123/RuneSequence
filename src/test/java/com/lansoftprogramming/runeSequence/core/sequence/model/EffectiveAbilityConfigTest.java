package com.lansoftprogramming.runeSequence.core.sequence.model;

import com.lansoftprogramming.runeSequence.infrastructure.config.AbilityConfig;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class EffectiveAbilityConfigTest {

	@Test
	void shouldUseBaseValuesWhenOverridesEmpty() {
		AbilityConfig.AbilityData base = abilityData("Base", 10, true,
				(short) 5, (short) 7, 0.90, "baseMask");

		EffectiveAbilityConfig effective = EffectiveAbilityConfig.from("AbilityA", base,
				AbilitySettingsOverrides.empty());

		assertEquals("AbilityA", effective.getAbilityKey());
		assertEquals("Base", effective.getType().orElseThrow());
		assertEquals(10, effective.getLevel().orElseThrow());
		assertTrue(effective.isTriggersGcd());
		assertEquals(5, effective.getCastDuration());
		assertEquals(7, effective.getCooldown());
		assertEquals(0.90, effective.getDetectionThreshold().orElseThrow());
		assertEquals("baseMask", effective.getMask().orElseThrow());
	}

	@Test
	void shouldApplySingleOverride() {
		AbilityConfig.AbilityData base = abilityData("Base", 15, true,
				(short) 2, (short) 4, 0.95, "baseMask");

		AbilitySettingsOverrides overrides = AbilitySettingsOverrides.builder()
				.detectionThreshold(0.80)
				.build();

		EffectiveAbilityConfig effective = EffectiveAbilityConfig.from("AbilityB", base, overrides);

		assertEquals("Base", effective.getType().orElseThrow());
		assertEquals(15, effective.getLevel().orElseThrow());
		assertTrue(effective.isTriggersGcd());
		assertEquals(2, effective.getCastDuration());
		assertEquals(4, effective.getCooldown());
		assertEquals(0.80, effective.getDetectionThreshold().orElseThrow());
		assertEquals("baseMask", effective.getMask().orElseThrow());
	}

	@Test
	void shouldCombineMultipleOverrides() {
		AbilityConfig.AbilityData base = abilityData("Base", 20, true,
				(short) 3, (short) 6, 0.85, "baseMask");

		AbilitySettingsOverrides overrides = AbilitySettingsOverrides.builder()
				.type("Override")
				.level(30)
				.triggersGcd(false)
				.castDuration((short) 9)
				.cooldown((short) 12)
				.detectionThreshold(0.60)
				.mask("customMask")
				.build();

		EffectiveAbilityConfig effective = EffectiveAbilityConfig.from("AbilityC", base, overrides);

		assertEquals("Override", effective.getType().orElseThrow());
		assertEquals(30, effective.getLevel().orElseThrow());
		assertFalse(effective.isTriggersGcd());
		assertEquals(9, effective.getCastDuration());
		assertEquals(12, effective.getCooldown());
		assertEquals(0.60, effective.getDetectionThreshold().orElseThrow());
		assertEquals("customMask", effective.getMask().orElseThrow());
	}

	@Test
	void shouldSanitizeInvalidOverrideValues() {
		AbilityConfig.AbilityData base = abilityData("Base", 10, true,
				(short) 1, (short) 1, 0.90, "baseMask");

		AbilitySettingsOverrides overrides = AbilitySettingsOverrides.builder()
				.level(-5)
				.castDuration((short) -10)
				.cooldown((short) -2)
				.detectionThreshold(Double.NaN)
				.mask("  customMask  ")
				.build();

		EffectiveAbilityConfig effective = EffectiveAbilityConfig.from("AbilityD", base, overrides);

		assertEquals(0, effective.getLevel().orElseThrow());
		assertEquals(0, effective.getCastDuration());
		assertEquals(0, effective.getCooldown());
		assertTrue(effective.getDetectionThreshold().isEmpty());
		assertEquals("customMask", effective.getMask().orElseThrow());
	}

	private AbilityConfig.AbilityData abilityData(String type,
	                                              int level,
	                                              boolean triggersGcd,
	                                              short castDuration,
	                                              short cooldown,
	                                              Double detectionThreshold,
	                                              String mask) {
		AbilityConfig.AbilityData data = new AbilityConfig.AbilityData();
		data.setType(type);
		data.setLevel(level);
		data.setTriggersGcd(triggersGcd);
		data.setCastDuration(castDuration);
		data.setCooldown(cooldown);
		data.setDetectionThreshold(detectionThreshold);
		data.setMask(mask);
		return data;
	}
}
