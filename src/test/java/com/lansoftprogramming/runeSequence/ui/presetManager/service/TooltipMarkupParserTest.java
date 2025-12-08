package com.lansoftprogramming.runeSequence.ui.presetManager.service;

import com.lansoftprogramming.runeSequence.ui.presetManager.model.SequenceElement;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class TooltipMarkupParserTest {

	private final SequenceVisualService visualService = new SequenceVisualService();
	private final ExpressionBuilder expressionBuilder = new ExpressionBuilder();

	@Test
	void shouldInjectTooltipElementsIntoVisualSequence() {
		String expression = "A -> (Stand behind) B + C (Finish him)";

		List<SequenceElement> elements = visualService.parseToVisualElements(expression);

		assertEquals(7, elements.size());
		assertEquals(SequenceElement.Type.ABILITY, elements.get(0).getType());
		assertEquals(SequenceElement.Type.TOOLTIP, elements.get(2).getType());
		assertEquals("Stand behind", elements.get(2).getValue());
		assertEquals(SequenceElement.Type.TOOLTIP, elements.get(6).getType());
		assertEquals("Finish him", elements.get(6).getValue());

		String rebuilt = expressionBuilder.buildExpression(elements);
		assertEquals("A→(Stand behind)B+C(Finish him)", rebuilt);
	}

	@Test
	void shouldRoundTripTooltipTextWithEscapedParens() {
		String expression = "A→(Use \\(defense\\) now)B";
		List<SequenceElement> elements = visualService.parseToVisualElements(expression);

		SequenceElement tooltip = elements.stream()
			.filter(SequenceElement::isTooltip)
			.findFirst()
			.orElseThrow();
		assertEquals("Use (defense) now", tooltip.getValue());

		String rebuilt = expressionBuilder.buildExpression(elements);
		assertEquals("A→(Use \\(defense\\) now)B", rebuilt);
	}

	@Test
	void shouldHandleTooltipsAroundArrowsConsistently() {
		String leftExpression = "A (Stand here) → B";
		List<SequenceElement> leftElements = visualService.parseToVisualElements(leftExpression);

		long leftTooltipCount = leftElements.stream().filter(SequenceElement::isTooltip).count();
		assertEquals(1, leftTooltipCount);
		assertEquals("Stand here", leftElements.stream()
				.filter(SequenceElement::isTooltip)
				.findFirst()
				.orElseThrow()
				.getValue());

		String rebuiltLeft = expressionBuilder.buildExpression(leftElements);
		assertEquals("A(Stand here)→B", rebuiltLeft);

		String rightExpression = "A → (Go next) B";
		List<SequenceElement> rightElements = visualService.parseToVisualElements(rightExpression);

		long rightTooltipCount = rightElements.stream().filter(SequenceElement::isTooltip).count();
		assertEquals(1, rightTooltipCount);
		assertEquals("Go next", rightElements.stream()
				.filter(SequenceElement::isTooltip)
				.findFirst()
				.orElseThrow()
				.getValue());

		String rebuiltRight = expressionBuilder.buildExpression(rightElements);
		assertEquals("A→(Go next)B", rebuiltRight);
	}

	@Test
	void shouldLeaveGroupedExpressionsUntouchedWhenParsing() {
		String expression = "(A+B) → C";
		List<SequenceElement> elements = visualService.parseToVisualElements(expression);

		long tooltipCount = elements.stream().filter(SequenceElement::isTooltip).count();
		assertEquals(0, tooltipCount);
		assertTrue(elements.stream().anyMatch(SequenceElement::isAbility));

		String rebuilt = expressionBuilder.buildExpression(elements);
		assertEquals("A+B→C", rebuilt);
	}

	@Test
	void shouldNotTreatOperatorParensAsTooltips() {
		String expression = "A→(B+C)→D";
		List<SequenceElement> elements = visualService.parseToVisualElements(expression);

		long tooltipCount = elements.stream().filter(SequenceElement::isTooltip).count();
		assertEquals(0, tooltipCount);

		String rebuilt = expressionBuilder.buildExpression(elements);
		assertEquals("A→B+C→D", rebuilt);
	}
}
