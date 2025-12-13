package com.lansoftprogramming.runeSequence.core.sequence.parser;

import com.lansoftprogramming.runeSequence.core.sequence.model.*;
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

	@Test
	void shouldParseInstanceLabelsAndStripSuffixFromToken() {
		SequenceDefinition definition = SequenceParser.parse("cane[*1] → tc");

		Alternative first = definition.getStep(0).getTerms().get(0).getAlternatives().get(0);
		assertEquals("cane", first.getToken(), "Ability token should exclude the label suffix");
		assertEquals("1", first.getInstanceLabel(), "Label suffix should populate instanceLabel");

		Alternative second = definition.getStep(1).getTerms().get(0).getAlternatives().get(0);
		assertEquals("tc", second.getToken());
		assertNull(second.getInstanceLabel());
	}

	@Test
	void shouldApplyPerInstanceOverridesFromSettingsLines() {
		String input = String.join("\n",
				"cane[*1] → tc",
				"#*1 cooldown=30000 triggers_gcd=false detection_threshold=0.9 mask=spec_mask cast_duration=332 level=20 type=Basic"
		);

		SequenceDefinition definition = SequenceParser.parse(input);

		Alternative labeled = definition.getStep(0).getTerms().get(0).getAlternatives().get(0);
		assertEquals("1", labeled.getInstanceLabel());

		AbilitySettingsOverrides overrides = labeled.getAbilitySettingsOverrides();
		assertNotNull(overrides, "Matching #*N line should attach overrides to the labeled node");
		assertEquals(false, overrides.getTriggersGcdOverride().orElse(null));
		assertEquals((short) 332, overrides.getCastDurationOverride().orElseThrow());
		assertEquals((short) 30000, overrides.getCooldownOverride().orElseThrow());
		assertEquals(0.9d, overrides.getDetectionThresholdOverride().orElseThrow(), 1e-9);
		assertEquals("spec_mask", overrides.getMaskOverride().orElseThrow());
		assertEquals(20, overrides.getLevelOverride().orElseThrow());
		assertEquals("Basic", overrides.getTypeOverride().orElseThrow());

		Alternative unlabeled = definition.getStep(1).getTerms().get(0).getAlternatives().get(0);
		assertNull(unlabeled.getAbilitySettingsOverrides(), "Unlabeled nodes should not receive per-instance overrides");
	}

	@Test
	void shouldOverwriteDuplicateSettingsLinesWithLaterValues() {
		String input = String.join("\n",
				"cane[*1] → tc",
				"#*1 cooldown=100",
				"#*1 cooldown=200"
		);

		SequenceDefinition definition = SequenceParser.parse(input);
		Alternative labeled = definition.getStep(0).getTerms().get(0).getAlternatives().get(0);
		assertEquals((short) 200, labeled.getAbilitySettingsOverrides().getCooldownOverride().orElseThrow());
	}

	@Test
	void shouldIgnoreMalformedSettingsLines() {
		String input = String.join("\n",
				"cane[*1] → tc",
				"#*x cooldown=100",
				"#*1cooldown=200",
				"#*1 bogus",
				"#*1 cooldown=not_a_number"
		);

		SequenceDefinition definition = SequenceParser.parse(input);
		Alternative labeled = definition.getStep(0).getTerms().get(0).getAlternatives().get(0);
		assertNull(labeled.getAbilitySettingsOverrides(), "Malformed #* lines should not crash or attach overrides");
	}
}