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

	@Test
	void insertNextAfterTrailingTooltipShouldKeepTooltipWithLeftAbility() {
		List<SequenceElement> elements = new ArrayList<>(List.of(
				SequenceElement.ability("A"),
				SequenceElement.arrow(),
				SequenceElement.ability("B"),
				SequenceElement.tooltip("note")
		));

		List<SequenceElement> result = builder.insertAbility(elements, "C", elements.size(), DropZoneType.NEXT, DropSide.RIGHT);

		assertEquals("A→B(note)→C", builder.buildExpression(result));
	}

	@Test
	void insertAndAfterTrailingTooltipShouldKeepTooltipWithLeftAbility() {
		List<SequenceElement> elements = new ArrayList<>(List.of(
				SequenceElement.ability("A"),
				SequenceElement.plus(),
				SequenceElement.ability("B"),
				SequenceElement.tooltip("note")
		));

		List<SequenceElement> result = builder.insertAbility(elements, "C", elements.size(), DropZoneType.AND, DropSide.RIGHT);

		assertEquals("A+B(note)+C", builder.buildExpression(result));
	}

	@Test
	void removeLeadingAbilityShouldNotLeaveGroupSeparatorBetweenTooltipAndNextAbility() {
		List<SequenceElement> elements = new ArrayList<>(List.of(
				SequenceElement.ability("A"),
				SequenceElement.tooltip("note"),
				SequenceElement.plus(),
				SequenceElement.ability("B"),
				SequenceElement.plus(),
				SequenceElement.ability("C")
		));

		List<SequenceElement> result = builder.removeAbilityAt(elements, 0);

		assertEquals("(note)B+C", builder.buildExpression(result));
	}

	@Test
	void moveMiddleAbilityNextAfterLast_withLeadingTooltipGroup_shouldKeepAndGroupAndInsertAfterLast() {
		List<SequenceElement> elements = new ArrayList<>(List.of(
				SequenceElement.ability("A"),
				SequenceElement.tooltip("note"),
				SequenceElement.plus(),
				SequenceElement.ability("B"),
				SequenceElement.plus(),
				SequenceElement.ability("C")
		));

		List<SequenceElement> afterRemoval = builder.removeAbilityAt(elements, 3);
		List<SequenceElement> afterInsert = builder.insertAbility(afterRemoval, "B", afterRemoval.size(), DropZoneType.NEXT, DropSide.RIGHT);

		assertEquals("A(note)+C→B", builder.buildExpression(afterInsert));
	}

	@Test
	void moveAbilityOutOfStepShouldKeepTooltipAtStepBoundary_NotAttachedToPreviousStep() {
		List<SequenceElement> elements = new ArrayList<>(List.of(
				SequenceElement.ability("A"),
				SequenceElement.arrow(),
				SequenceElement.ability("B"),
				SequenceElement.tooltip("tip"),
				SequenceElement.plus(),
				SequenceElement.ability("C"),
				SequenceElement.plus(),
				SequenceElement.ability("D")
		));

		List<SequenceElement> afterRemoval = builder.removeAbilityAt(elements, 2);
		List<SequenceElement> afterInsert = builder.insertAbility(afterRemoval, "B", afterRemoval.size(), DropZoneType.NEXT, DropSide.RIGHT);

		assertEquals("A→(tip)C+D→B", builder.buildExpression(afterInsert));
	}
}
