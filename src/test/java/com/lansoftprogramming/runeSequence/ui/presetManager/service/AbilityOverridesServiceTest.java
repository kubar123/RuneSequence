package com.lansoftprogramming.runeSequence.ui.presetManager.service;

import com.lansoftprogramming.runeSequence.core.sequence.model.AbilitySettingsOverrides;
import com.lansoftprogramming.runeSequence.infrastructure.config.AbilitySettingsOverridesMapper;
import com.lansoftprogramming.runeSequence.infrastructure.config.dto.PresetAbilitySettings;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

class AbilityOverridesServiceTest {

	@Test
	void shouldApplyPerAbilityOverridesIntoSettings() {
		AbilityOverridesService service = new AbilityOverridesService(new AbilitySettingsOverridesMapper());
		Map<String, AbilitySettingsOverrides> perAbility = Map.of(
				"Asphyxiate",
				AbilitySettingsOverrides.builder().cooldown((short) 12).build()
		);

		PresetAbilitySettings settings = service.applyPerAbilityOverrides(null, perAbility);

		assertNotNull(settings);
		assertNotNull(settings.getPerAbility());
		assertTrue(settings.getPerAbility().containsKey("Asphyxiate"));
	}

	@Test
	void shouldDropEmptySettingsWhenClearingPerAbilityOverrides() {
		AbilityOverridesService service = new AbilityOverridesService(new AbilitySettingsOverridesMapper());
		Map<String, AbilitySettingsOverrides> perAbility = Map.of(
				"Asphyxiate",
				AbilitySettingsOverrides.builder().cooldown((short) 12).build()
		);

		PresetAbilitySettings settings = service.applyPerAbilityOverrides(null, perAbility);
		PresetAbilitySettings cleared = service.applyPerAbilityOverrides(settings, Map.of());

		assertNull(cleared);
	}
}
