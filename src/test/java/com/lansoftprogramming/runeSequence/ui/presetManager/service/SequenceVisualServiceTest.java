package com.lansoftprogramming.runeSequence.ui.presetManager.service;

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
	}
}
