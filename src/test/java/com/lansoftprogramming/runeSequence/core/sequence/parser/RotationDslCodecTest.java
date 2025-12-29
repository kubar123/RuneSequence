package com.lansoftprogramming.runeSequence.core.sequence.parser;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.lansoftprogramming.runeSequence.core.sequence.model.AbilitySettingsOverrides;
import com.lansoftprogramming.runeSequence.infrastructure.config.AbilitySettingsOverridesMapper;
import com.lansoftprogramming.runeSequence.infrastructure.config.RotationConfig;
import com.lansoftprogramming.runeSequence.infrastructure.config.dto.PresetAbilitySettings;
import org.junit.jupiter.api.Test;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

class RotationDslCodecTest {

	@Test
	void shouldStripLabelsAndSettingsForSimpleExport() {
		String input = "cane[*1]→tc\n#*1 cooldown=30000 detection_threshold=0.9";

		String exported = RotationDslCodec.exportSimple(input);

		assertEquals("cane→tc", exported);
		assertFalse(exported.contains("[*"));
		assertFalse(exported.contains("#*"));
	}

	@Test
	void shouldDeepExportRoundTripOverrides() {
		String expression = "cane[*1]→tc→cane[*2]";
		Map<String, AbilitySettingsOverrides> overrides = new LinkedHashMap<>();
		overrides.put("1", AbilitySettingsOverrides.builder().cooldown((short) 30000).build());
		overrides.put("2", AbilitySettingsOverrides.builder().triggersGcd(false).detectionThreshold(0.8).build());

		String deep = RotationDslCodec.exportDeep(expression, overrides);
		RotationDslCodec.ParsedRotation parsed = RotationDslCodec.parse(deep);

		assertEquals(expression, parsed.expression());
		assertEquals(overrides, parsed.perInstanceOverrides());
	}

	@Test
	void shouldMergeDuplicateSettingsLinesAndIgnoreMalformed() {
		String input = String.join("\n",
				"cane[*1]→tc",
				"#*1 cooldown=100",
				"#*1 cooldown=200 detection_threshold=0.9",
				"#*1 triggers_gcd=maybe",
				"#*oops cooldown=300"
		);

		RotationDslCodec.ParsedRotation parsed = RotationDslCodec.parse(input);

		assertEquals("cane[*1]→tc", parsed.expression());
		assertEquals(
				Map.of("1", AbilitySettingsOverrides.builder().cooldown((short) 200).detectionThreshold(0.9).build()),
				parsed.perInstanceOverrides()
		);
	}

	@Test
	void shouldRoundTripViaPresetJsonAndDeepDsl() throws Exception {
		String expression = "cane[*1]→tc";
		Map<String, AbilitySettingsOverrides> overrides = Map.of(
				"1", AbilitySettingsOverrides.builder().cooldown((short) 30000).mask("spec_mask").build()
		);

		AbilitySettingsOverridesMapper mapper = new AbilitySettingsOverridesMapper();
		PresetAbilitySettings abilitySettings = mapper.toConfig(overrides);

		RotationConfig.PresetData preset = new RotationConfig.PresetData();
		preset.setName("test");
		preset.setExpression(expression);
		preset.setAbilitySettings(abilitySettings);

		ObjectMapper objectMapper = new ObjectMapper().findAndRegisterModules();
		String json = objectMapper.writeValueAsString(preset);
		RotationConfig.PresetData loaded = objectMapper.readValue(json, RotationConfig.PresetData.class);

		assertEquals(expression, loaded.getExpression());
		assertNotNull(loaded.getAbilitySettings());
		assertEquals(overrides, mapper.toDomain(loaded.getAbilitySettings()));

		String deep = RotationDslCodec.exportDeep(loaded.getExpression(), mapper.toDomain(loaded.getAbilitySettings()));
		RotationDslCodec.ParsedRotation imported = RotationDslCodec.parse(deep);
		assertEquals(expression, imported.expression());
		assertEquals(overrides, imported.perInstanceOverrides());
	}

	@Test
	void deepExportShouldOnlyEmitOverridesForLabelsPresentInExpression() {
		String expression = "alpha[*1]→beta[*2]";
		Map<String, AbilitySettingsOverrides> overrides = new LinkedHashMap<>();
		overrides.put("2", AbilitySettingsOverrides.builder().cooldown((short) 200).build());
		overrides.put("99", AbilitySettingsOverrides.builder().cooldown((short) 999).build());

		String deep = RotationDslCodec.exportDeep(expression, overrides);

		assertTrue(deep.contains("#*2 cooldown=200"));
		assertFalse(deep.contains("#*99"), "exportDeep should ignore overrides with no matching label in the expression");
	}

	@Test
	void deepExportShouldSortLabelsNumerically() {
		String expression = "alpha[*10]→beta[*2]→gamma[*1]";
		Map<String, AbilitySettingsOverrides> overrides = new LinkedHashMap<>();
		overrides.put("10", AbilitySettingsOverrides.builder().cooldown((short) 10).build());
		overrides.put("2", AbilitySettingsOverrides.builder().cooldown((short) 2).build());
		overrides.put("1", AbilitySettingsOverrides.builder().cooldown((short) 1).build());

		String deep = RotationDslCodec.exportDeep(expression, overrides);

		int idx1 = deep.indexOf("#*1");
		int idx2 = deep.indexOf("#*2");
		int idx10 = deep.indexOf("#*10");
		assertTrue(idx1 > 0 && idx2 > 0 && idx10 > 0);
		assertTrue(idx1 < idx2 && idx2 < idx10, "Settings lines should be emitted in numeric label order");
	}

	@Test
	void parseShouldIgnoreEmptyOrMalformedSettingsLinesWithoutBreakingExpression() {
		String input = String.join("\n",
				"alpha[*1]→beta",
				"",
				"#*1",
				"#*1   ",
				"#*1 cooldown=",
				"#*1 =123",
				"#*1 cooldown=10"
		);

		RotationDslCodec.ParsedRotation parsed = RotationDslCodec.parse(input);
		assertEquals("alpha[*1]→beta", parsed.expression());
		assertEquals(Map.of("1", AbilitySettingsOverrides.builder().cooldown((short) 10).build()), parsed.perInstanceOverrides());
	}

	@Test
	void collectLabelsInExpressionShouldIgnoreTooltipMarkup() {
		Set<String> labels = RotationDslCodec.collectLabelsInExpression("alpha[*1]→(Hello)beta[*2]");
		assertEquals(Set.of("1", "2"), labels);
	}

	@Test
	void collectLabelsInExpressionShouldStillWorkWhenTooltipTextContainsStructuralOperators() {
		Set<String> labels = RotationDslCodec.collectLabelsInExpression("alpha[*1]→(bad→tip)beta[*2]");
		assertEquals(Set.of("1", "2"), labels);
	}
}
