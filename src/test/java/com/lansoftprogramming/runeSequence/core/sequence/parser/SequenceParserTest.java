package com.lansoftprogramming.runeSequence.core.sequence.parser;

import com.lansoftprogramming.runeSequence.core.sequence.model.Alternative;
import com.lansoftprogramming.runeSequence.core.sequence.model.SequenceDefinition;
import com.lansoftprogramming.runeSequence.core.sequence.model.Step;
import com.lansoftprogramming.runeSequence.core.sequence.model.Term;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class SequenceParserTest {

	@Test
	void shouldParseAsciiArrowAndSpecSuffix() {
		SequenceDefinition definition = SequenceParser.parse("Bio spec -> Radiant / Shield spec");

		assertEquals(2, definition.size(), "ASCII arrows should normalize to steps");

		Step firstStep = definition.getStep(0);
		assertEquals(2, firstStep.getTerms().size());
		List<Alternative> firstTerms = firstStep.getTerms().get(0).getAlternatives();
		assertEquals("Bio", firstTerms.get(0).getToken());
		assertEquals("spec", firstStep.getTerms().get(1).getAlternatives().get(0).getToken(), "spec suffix should become its own term");

		Step secondStep = definition.getStep(1);
		assertEquals(2, secondStep.getTerms().size());
		List<Alternative> radiants = secondStep.getTerms().get(0).getAlternatives();
		assertEquals(2, radiants.size(), "Slash should keep alternatives together");
		assertEquals("Radiant", radiants.get(0).getToken());
		assertEquals("Shield", radiants.get(1).getToken());
		assertEquals("spec", secondStep.getTerms().get(1).getAlternatives().get(0).getToken());
	}

	@Test
	void shouldBuildNestedGroupsForParenthesizedExpressions() {
		SequenceDefinition definition = SequenceParser.parse("(Alpha → Beta + (Gamma / Delta)) + Epsilon");

		Step rootStep = definition.getStep(0);
		assertEquals(2, rootStep.getTerms().size(), "Outer '+' should produce two terms");

		Alternative grouped = rootStep.getTerms().get(0).getAlternatives().get(0);
		assertTrue(grouped.isGroup(), "Parenthesized expression must convert to subgroup alternative");
		SequenceDefinition subgroup = grouped.getSubgroup();
		assertEquals(2, subgroup.getSteps().size(), "Inner arrow should split subgroup into steps");

		Step subgroupSecondStep = subgroup.getStep(1);
		assertEquals(2, subgroupSecondStep.getTerms().size(), "Beta + (Gamma / Delta) should remain two terms");

		Alternative nestedGroup = subgroupSecondStep.getTerms().get(1).getAlternatives().get(0);
		assertTrue(nestedGroup.isGroup(), "Parenthesized OR should remain a subgroup");
		SequenceDefinition orGroup = nestedGroup.getSubgroup();
		Term nestedOrTerm = orGroup.getStep(0).getTerms().get(0);
		assertEquals(2, nestedOrTerm.getAlternatives().size(), "Nested slash inside subgroup must keep alternatives");

		Alternative trailing = rootStep.getTerms().get(1).getAlternatives().get(0);
		assertTrue(trailing.isToken());
		assertEquals("Epsilon", trailing.getToken());
	}

	@Test
	void shouldThrowWhenParenthesesAreUnbalanced() {
		assertThrows(IllegalStateException.class, () -> SequenceParser.parse("(Alpha + Beta"));
	}

	@Test
	void shouldRejectMalformedSequences() {
		List<String> invalidExpressions = List.of(
				"(A + B / C + D -> F)",
				"(A -> -> B)",
				"(-> A -> B)",
				"(A B -> C -> D)",
				"(A -> + B -> C)",
				"(A -> B -> )",
				"A -> -> B"
		);

		for (String expression : invalidExpressions) {
			assertThrows(IllegalStateException.class,
					() -> SequenceParser.parse(expression),
					() -> "Expected malformed sequence to fail parsing: " + expression);
		}
	}

	@Test
	void shouldNormalizeSpecSuffixInsideGroups() {
		SequenceDefinition definition = SequenceParser.parse("(Bio spec) -> Radiant");

		assertEquals(2, definition.size(), "Outer arrow should still split into two steps");
		assertEquals("(Bio + spec)", definition.getStep(0).toString(), "spec suffix should be split even inside parentheses");
		assertEquals("Radiant", definition.getStep(1).toString());
	}

	@Test
	void shouldRejectBlankExpressions() {
		assertThrows(IllegalStateException.class, () -> SequenceParser.parse("   "));
		assertThrows(IllegalStateException.class, () -> SequenceParser.parse(""));
	}

	@Test
	void shouldValidateAbilityNamesWithoutOperators() {
		assertThrows(IllegalStateException.class, () -> SequenceParser.parse("Alpha Beta"));

		SequenceDefinition definition = SequenceParser.parse("Alpha-Beta -> Gamma1");
		assertEquals("Alpha-Beta", definition.getStep(0).getTerms().get(0).getAlternatives().get(0).getToken());
		assertEquals(2, definition.size(), "Hyphenated or numeric ability names should parse");
	}
}
