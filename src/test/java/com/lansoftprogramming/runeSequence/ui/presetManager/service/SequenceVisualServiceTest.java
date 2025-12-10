package com.lansoftprogramming.runeSequence.ui.presetManager.service;

import com.lansoftprogramming.runeSequence.core.sequence.parser.SequenceParser;
import com.lansoftprogramming.runeSequence.core.sequence.parser.TooltipStructure;
import com.lansoftprogramming.runeSequence.ui.presetManager.model.SequenceElement;
import org.junit.jupiter.api.Test;

import java.util.List;

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
}
