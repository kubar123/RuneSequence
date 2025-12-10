package com.lansoftprogramming.runeSequence.ui.presetManager.service;

import com.lansoftprogramming.runeSequence.core.sequence.parser.SequenceParser;
import com.lansoftprogramming.runeSequence.core.sequence.parser.TooltipGrammar;
import com.lansoftprogramming.runeSequence.core.sequence.parser.TooltipMarkupParser;
import com.lansoftprogramming.runeSequence.ui.presetManager.model.SequenceElement;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

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

		@Test
		void shouldHandlePartiallyEscapedTooltipParensGracefully() {
			String expression = "A→(Use \\(macro)B";

			List<SequenceElement> elements = visualService.parseToVisualElements(expression);

			SequenceElement tooltip = elements.stream()
					.filter(SequenceElement::isTooltip)
					.findFirst()
					.orElseThrow();
			assertEquals("Use (macro", tooltip.getValue());

			String rebuilt = expressionBuilder.buildExpression(elements);
			assertEquals("A→(Use \\(macro)B", rebuilt);
		}

		@Test
		void shouldTreatNestedGroupedExpressionsAsNonTooltips() {
			String expression = "(A + (B/C)) → D";
			List<SequenceElement> elements = visualService.parseToVisualElements(expression);

			long tooltipCount = elements.stream().filter(SequenceElement::isTooltip).count();
			assertEquals(0, tooltipCount);
			assertTrue(elements.stream().anyMatch(SequenceElement::isAbility));

			String rebuilt = expressionBuilder.buildExpression(elements);
			SequenceParser.parse(rebuilt);
		}

		@Test
		void shouldParseTooltipsInMultiLineExpressions() {
			String expression = "A→(First)\nB + C (Second) → D";

			List<SequenceElement> elements = visualService.parseToVisualElements(expression);

			List<String> tooltipMessages = elements.stream()
					.filter(SequenceElement::isTooltip)
					.map(SequenceElement::getValue)
					.toList();

			assertEquals(List.of("First", "Second"), tooltipMessages);

			String rebuilt = expressionBuilder.buildExpression(elements);
			assertTrue(rebuilt.contains("(First)"));
			assertTrue(rebuilt.contains("(Second)"));
			assertTrue(rebuilt.indexOf('\n') < 0);
			SequenceParser.parse(rebuilt);
		}

	@Test
	void shouldFallbackToPlainParsingWhenTooltipParsingFails() {
			SequenceVisualService service = new SequenceVisualService(new TooltipMarkupParser() {
				@Override
				public TooltipMarkupParser.ParseResult parse(String expression) {
					throw new RuntimeException("boom");
				}
			});

			String expression = "A→B+C";
			List<SequenceElement> elements = service.parseToVisualElements(expression);

			long tooltipCount = elements.stream().filter(SequenceElement::isTooltip).count();
			assertEquals(0, tooltipCount);
			assertTrue(elements.stream().anyMatch(SequenceElement::isAbility));
		}

		@Test
		void shouldTreatAbilityParensAsGroupingAndRoundTrip() {
			Set<String> abilityNames = Set.of("A", "B");
			SequenceVisualService service = new SequenceVisualService(abilityNames);

			List<SequenceElement> leftElements = service.parseToVisualElements("(A) → B");
			long leftTooltipCount = leftElements.stream().filter(SequenceElement::isTooltip).count();
			assertEquals(0, leftTooltipCount);

			String leftRebuilt = expressionBuilder.buildExpression(leftElements);
			assertEquals("A→B", leftRebuilt);
			SequenceParser.parse(leftRebuilt);

			List<SequenceElement> rightElements = service.parseToVisualElements("A → (B)");
			long rightTooltipCount = rightElements.stream().filter(SequenceElement::isTooltip).count();
			assertEquals(0, rightTooltipCount);

			String rightRebuilt = expressionBuilder.buildExpression(rightElements);
			assertEquals("A→B", rightRebuilt);
			SequenceParser.parse(rightRebuilt);
		}

		@Test
		void shouldTreatKnownAbilityKeyParensAsGrouping() {
			Set<String> abilityNames = Set.of("trueNorth", "trickAttack");
			SequenceVisualService service = new SequenceVisualService(abilityNames);

			String expression = "(trueNorth) → trickAttack";
			List<SequenceElement> elements = service.parseToVisualElements(expression);

			long tooltipCount = elements.stream().filter(SequenceElement::isTooltip).count();
			assertEquals(0, tooltipCount);

			String rebuilt = expressionBuilder.buildExpression(elements);
			assertEquals("trueNorth→trickAttack", rebuilt);
			SequenceParser.parse(rebuilt);
		}

		@Test
		void shouldKeepTooltipInsideExtraGroupingParens() {
			String expression = "((Stand here)) B";
			List<SequenceElement> elements = visualService.parseToVisualElements(expression);

			List<String> tooltipMessages = elements.stream()
					.filter(SequenceElement::isTooltip)
					.map(SequenceElement::getValue)
					.toList();
			assertEquals(List.of("Stand here"), tooltipMessages);

			String rebuilt = expressionBuilder.buildExpression(elements);
			assertTrue(rebuilt.contains("(Stand here)"));
			assertTrue(rebuilt.contains("B"));
			assertTrue(rebuilt.indexOf('\n') < 0);
			SequenceParser.parse(rebuilt);
		}

		@Test
		void shouldHandleTooltipsWithBlankLinesAndNewlines() {
			String expression = "A (First)\n\n(Second) B";

			List<SequenceElement> elements = visualService.parseToVisualElements(expression);

			List<String> tooltipMessages = elements.stream()
					.filter(SequenceElement::isTooltip)
					.map(SequenceElement::getValue)
					.toList();

			assertEquals(List.of("First", "Second"), tooltipMessages);

			String rebuilt = expressionBuilder.buildExpression(elements);
			assertTrue(rebuilt.contains("(First)"));
			assertTrue(rebuilt.contains("(Second)"));
			assertTrue(rebuilt.indexOf('\n') < 0);
			SequenceParser.parse(rebuilt);
		}

		@Test
		void shouldHandleTooltipsAcrossLinesWithAbilitiesAndArrows() {
			String expression = "A\n(First) B → (Second)\nC";

			List<SequenceElement> elements = visualService.parseToVisualElements(expression);

			List<String> tooltipMessages = elements.stream()
					.filter(SequenceElement::isTooltip)
					.map(SequenceElement::getValue)
					.toList();

			assertEquals(List.of("First", "Second"), tooltipMessages);

			String rebuilt = expressionBuilder.buildExpression(elements);
			assertTrue(rebuilt.contains("(First)"));
			assertTrue(rebuilt.contains("(Second)"));
			assertTrue(rebuilt.indexOf('\n') < 0);
			SequenceParser.parse(rebuilt);
		}

	@Test
	void shouldValidateSharedTooltipGrammarRules() {
		assertTrue(TooltipGrammar.isValidTooltipMessage("Stack here"));
		assertFalse(TooltipGrammar.isValidTooltipMessage("Stack → here"));
		assertEquals("Use \\(defense\\)", TooltipGrammar.escapeTooltipText("Use (defense)"));
	}

	@Test
	void shouldRejectEditingTooltipThatContainsStructuralSymbols() {
		List<SequenceElement> elements = new ArrayList<>();
		elements.add(SequenceElement.ability("A"));
		elements.add(SequenceElement.tooltip("ok"));

		ExpressionBuilder.TooltipEditResult result = expressionBuilder.editTooltipAt(elements, 1, "bad → tooltip");
		assertEquals(ExpressionBuilder.TooltipEditStatus.INVALID, result.status());
		assertEquals(2, result.elements().size());
		assertEquals("ok", result.elements().get(1).getValue());
	}
}