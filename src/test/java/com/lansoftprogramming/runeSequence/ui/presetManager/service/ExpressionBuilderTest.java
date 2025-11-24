package com.lansoftprogramming.runeSequence.ui.presetManager.service;

import com.lansoftprogramming.runeSequence.ui.presetManager.drag.model.DropSide;
import com.lansoftprogramming.runeSequence.ui.presetManager.drag.model.DropZoneType;
import com.lansoftprogramming.runeSequence.ui.presetManager.model.SequenceElement;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

class ExpressionBuilderTest {

	private final ExpressionBuilder builder = new ExpressionBuilder();

	@Test
	void removeAbilityShouldCleanDanglingGroupSeparators() {
		List<SequenceElement> elements = new ArrayList<>(List.of(
				SequenceElement.ability("A"),
				SequenceElement.plus(),
				SequenceElement.ability("B"),
				SequenceElement.arrow(),
				SequenceElement.ability("C")
		));

		List<SequenceElement> result = builder.removeAbilityAt(elements, 2);

		assertEquals("A→C", builder.buildExpression(result), "Removing a grouped ability before an arrow should not leave orphaned separators");
	}

	@Test
	void insertAbilityShouldSplitGroupWhenAddingNextStep() {
		List<SequenceElement> elements = new ArrayList<>(List.of(
				SequenceElement.ability("A"),
				SequenceElement.plus(),
				SequenceElement.ability("B")
		));

		List<SequenceElement> result = builder.insertAbility(elements, "C", 1, DropZoneType.NEXT, DropSide.LEFT);

		assertEquals("A→C→B", builder.buildExpression(result), "Inserting NEXT inside a group should split it into sequential steps");
	}

	@Test
	void insertAbilityShouldHonorExistingGroupType() {
		List<SequenceElement> elements = new ArrayList<>(List.of(
				SequenceElement.ability("A"),
				SequenceElement.plus(),
				SequenceElement.ability("B")
		));

		List<SequenceElement> result = builder.insertAbility(elements, "C", 2, DropZoneType.OR, DropSide.LEFT);

		assertEquals("A+C+B", builder.buildExpression(result), "When dropping into an AND group, requested OR should still use '+' separators");
	}
}
