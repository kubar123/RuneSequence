package com.lansoftprogramming.runeSequence.ui.presetManager.service;

import com.lansoftprogramming.runeSequence.core.sequence.model.AbilitySettingsOverrides;
import com.lansoftprogramming.runeSequence.core.sequence.parser.SequenceParser;
import com.lansoftprogramming.runeSequence.core.sequence.parser.TooltipStructure;
import com.lansoftprogramming.runeSequence.ui.presetManager.model.SequenceElement;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SequenceVisualServiceTest {

	private final SequenceVisualService service = new SequenceVisualService();

	@Test
	void shouldReturnEmptyElementsForInvalidExpression() {
		assertTrue(service.parseToVisualElements("A -> -> B").isEmpty());
	}

	@Test
	void shouldFlattenNestedGroupsIntoVisualOrder() {
		List<SequenceElement> elements = service.parseToVisualElements("(Alpha + (Beta / Gamma)) -> Delta");

		List<String> values = elements.stream().map(SequenceElement::getValue).toList();
		assertEquals(List.of("Alpha", "+", "Beta", "/", "Gamma", "→", "Delta"), values,
				"Nested groups should appear as linear ability/separator elements");

		List<TooltipStructure.StructuralElement> structure = TooltipStructure.linearize(
				SequenceParser.parse("(Alpha + (Beta / Gamma)) -> Delta"));
		List<String> structuralValues = structure.stream()
				.map(el -> el.isAbility() ? el.abilityName() : String.valueOf(el.operatorSymbol()))
				.toList();
		assertEquals(values, structuralValues, "Tooltip structure should mirror visual flattening");
	}

	@Test
	void shouldAttachOverridesFromInlineSettingsLines() {
		String input = String.join("\n",
				"cane[*1] → tc",
				"#*1 detection_threshold=0.7 cooldown=123"
		);

		List<SequenceElement> elements = service.parseToVisualElements(input);
		SequenceElement cane = elements.get(0);
		assertTrue(cane.isAbility());
		assertEquals("cane", cane.getAbilityKey());
		assertEquals("1", cane.getInstanceLabel());
		assertEquals(0.7d, cane.getAbilitySettingsOverrides().getDetectionThresholdOverride().orElseThrow(), 1e-9);
		assertEquals((short) 123, cane.getAbilitySettingsOverrides().getCooldownOverride().orElseThrow());
	}

	@Test
	void shouldPreferProvidedOverridesOverInlineSettingsLines() {
		String input = String.join("\n",
				"cane[*1] → tc",
				"#*1 detection_threshold=0.7"
		);

		Map<String, AbilitySettingsOverrides> provided = Map.of(
				"1", AbilitySettingsOverrides.builder().detectionThreshold(0.8d).build()
		);

		List<SequenceElement> elements = service.parseToVisualElements(input, provided);
		SequenceElement cane = elements.get(0);
		assertEquals(0.8d, cane.getAbilitySettingsOverrides().getDetectionThresholdOverride().orElseThrow(), 1e-9);
	}
}
