package com.lansoftprogramming.runeSequence.infrastructure.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.lansoftprogramming.runeSequence.core.sequence.model.AbilitySettingsOverrides;
import com.lansoftprogramming.runeSequence.infrastructure.config.dto.PresetAbilitySettings;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

class RotationPresetPersistenceTest {

	private static final ObjectMapper MAPPER = new ObjectMapper().findAndRegisterModules();

	@Test
	void presetJsonRoundTripShouldOmitAbilitySettingsWhenNoOverrides() throws Exception {
		RotationConfig.PresetData preset = new RotationConfig.PresetData();
		preset.setName("NoOverrides");
		preset.setExpression("alpha→beta");
		preset.setAbilitySettings(null);

		String json = MAPPER.writeValueAsString(preset);
		assertFalse(json.contains("ability_settings"), "Legacy-compatible presets should not emit ability_settings when absent");

		RotationConfig.PresetData loaded = MAPPER.readValue(json, RotationConfig.PresetData.class);
		assertEquals("alpha→beta", loaded.getExpression());
		assertNull(loaded.getAbilitySettings());
	}

	@Test
	void presetJsonRoundTripShouldPreservePartialOverrides() throws Exception {
		Map<String, AbilitySettingsOverrides> overrides = Map.of(
				"1", AbilitySettingsOverrides.builder().cooldown((short) 12).build()
		);

		AbilitySettingsOverridesMapper mapper = new AbilitySettingsOverridesMapper();
		PresetAbilitySettings abilitySettings = mapper.toConfig(overrides);

		RotationConfig.PresetData preset = new RotationConfig.PresetData();
		preset.setName("Partial");
		preset.setExpression("alpha[*1]");
		preset.setAbilitySettings(abilitySettings);

		String json = MAPPER.writeValueAsString(preset);
		assertTrue(json.contains("ability_settings"));
		assertTrue(json.contains("per_instance"));
		assertTrue(json.contains("cooldown"));

		RotationConfig.PresetData loaded = MAPPER.readValue(json, RotationConfig.PresetData.class);
		assertNotNull(loaded.getAbilitySettings());
		assertEquals(overrides, mapper.toDomain(loaded.getAbilitySettings()));
	}

	@Test
	void presetJsonRoundTripShouldPreserveFullOverrides() throws Exception {
		Map<String, AbilitySettingsOverrides> overrides = Map.of(
				"1", AbilitySettingsOverrides.builder()
						.type("Basic")
						.level(20)
						.triggersGcd(false)
						.castDuration((short) 3)
						.cooldown((short) 10)
						.detectionThreshold(0.8)
						.mask("spec_mask")
						.build()
		);

		AbilitySettingsOverridesMapper mapper = new AbilitySettingsOverridesMapper();
		PresetAbilitySettings abilitySettings = mapper.toConfig(overrides);

		RotationConfig.PresetData preset = new RotationConfig.PresetData();
		preset.setName("Full");
		preset.setExpression("alpha[*1]");
		preset.setAbilitySettings(abilitySettings);

		String json = MAPPER.writeValueAsString(preset);
		RotationConfig.PresetData loaded = MAPPER.readValue(json, RotationConfig.PresetData.class);

		assertNotNull(loaded.getAbilitySettings());
		assertEquals(overrides, mapper.toDomain(loaded.getAbilitySettings()));
	}

	@Test
	void legacyJsonWithoutAbilitySettingsShouldStillLoad() throws Exception {
		String legacyJson = """
				{
				  "name": "Legacy",
				  "expression": "alpha→beta"
				}
				""";

		RotationConfig.PresetData loaded = MAPPER.readValue(legacyJson, RotationConfig.PresetData.class);
		assertEquals("Legacy", loaded.getName());
		assertEquals("alpha→beta", loaded.getExpression());
		assertNull(loaded.getAbilitySettings());

		AbilitySettingsOverridesMapper mapper = new AbilitySettingsOverridesMapper();
		assertTrue(mapper.toDomain(loaded.getAbilitySettings()).isEmpty(), "Missing ability_settings should behave like no overrides");
		assertTrue(mapper.toDomainPerAbility(loaded.getAbilitySettings()).isEmpty());
	}
}

