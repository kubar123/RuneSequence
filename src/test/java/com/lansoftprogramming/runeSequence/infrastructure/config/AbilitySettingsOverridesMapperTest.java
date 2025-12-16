package com.lansoftprogramming.runeSequence.infrastructure.config;

import com.lansoftprogramming.runeSequence.core.sequence.model.AbilitySettingsOverrides;
import com.lansoftprogramming.runeSequence.infrastructure.config.dto.PresetAbilityOverrides;
import com.lansoftprogramming.runeSequence.infrastructure.config.dto.PresetAbilitySettings;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

class AbilitySettingsOverridesMapperTest {

	@Test
	void shouldMapPerAbilityOverridesFromConfig() {
		PresetAbilityOverrides overrides = new PresetAbilityOverrides();
		overrides.setTriggersGcd(false);
		overrides.setCooldown((short) 12);

		PresetAbilitySettings settings = new PresetAbilitySettings();
		settings.setPerAbility(Map.of("Asphyxiate", overrides));

		AbilitySettingsOverridesMapper mapper = new AbilitySettingsOverridesMapper();
		Map<String, AbilitySettingsOverrides> domain = mapper.toDomainPerAbility(settings);

		assertEquals(1, domain.size());
		AbilitySettingsOverrides mapped = domain.get("Asphyxiate");
		assertNotNull(mapped);
		assertFalse(mapped.getTriggersGcdOverride().orElseThrow());
		assertEquals((short) 12, mapped.getCooldownOverride().orElseThrow());
	}

	@Test
	void shouldReturnEmptyMapWhenPerAbilityMissing() {
		AbilitySettingsOverridesMapper mapper = new AbilitySettingsOverridesMapper();
		assertTrue(mapper.toDomainPerAbility(null).isEmpty());
		assertTrue(mapper.toDomainPerAbility(new PresetAbilitySettings()).isEmpty());
	}

	@Test
	void shouldIgnoreInvalidNumericOverrides() {
		PresetAbilityOverrides overrides = new PresetAbilityOverrides();
		overrides.setDetectionThreshold(2.5d);
		overrides.setCooldown((short) -1);
		overrides.setCastDuration((short) -5);
		overrides.setLevel(-10);

		PresetAbilitySettings settings = new PresetAbilitySettings();
		settings.setPerInstance(Map.of("1", overrides));

		AbilitySettingsOverridesMapper mapper = new AbilitySettingsOverridesMapper();
		Map<String, AbilitySettingsOverrides> domain = mapper.toDomain(settings);

		assertTrue(domain.containsKey("1"));
		AbilitySettingsOverrides mapped = domain.get("1");
		assertNotNull(mapped);
		assertEquals(1.0d, mapped.getDetectionThresholdOverride().orElseThrow());
		assertTrue(mapped.getCooldownOverride().isEmpty());
		assertTrue(mapped.getCastDurationOverride().isEmpty());
		assertTrue(mapped.getLevelOverride().isEmpty());
	}

	@Test
	void shouldIgnoreInvalidMaskWithPathSeparators() {
		PresetAbilityOverrides overrides = new PresetAbilityOverrides();
		overrides.setMask("../mask.png");

		PresetAbilitySettings settings = new PresetAbilitySettings();
		settings.setPerInstance(Map.of("1", overrides));

		AbilitySettingsOverridesMapper mapper = new AbilitySettingsOverridesMapper();
		Map<String, AbilitySettingsOverrides> domain = mapper.toDomain(settings);

		assertFalse(domain.containsKey("1"));
	}
}
