package com.lansoftprogramming.runeSequence.core.sequence.parser;

import com.lansoftprogramming.runeSequence.application.TooltipScheduleBuilder;
import com.lansoftprogramming.runeSequence.ui.presetManager.model.SequenceElement;
import com.lansoftprogramming.runeSequence.ui.presetManager.service.ExpressionBuilder;
import com.lansoftprogramming.runeSequence.ui.presetManager.service.SequenceVisualService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

import java.util.List;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.*;

class SpecialKeywordExpressionTest {

	private static Stream<Arguments> specialKeywordCases() {
		return Stream.of(
				Arguments.of(
						"freedom + drop dummy",
						"freedom+(Drop)dummy"
				),
				Arguments.of(
						"s cane",
						"(s)cane"
				),
				Arguments.of(
						"r cane",
						"(r)cane"
				),
				Arguments.of(
						"gmaul eofspec",
						"gmaul+eofspec"
				)
		);
	}

	@ParameterizedTest
	@MethodSource("specialKeywordCases")
	void shouldAcceptAndNormalizeSpecialKeywords(String input, String expectedCanonical) {
		assertParsesForDetection(input);
		assertRoundTripsViaUiParsing(input, expectedCanonical);
		assertEquals(expectedCanonical, RotationDslCodec.exportSimple(input));
	}

	@Test
	void shouldAcceptAndNormalizeFullExpressionExample() {
		String input = "anti + surge → freedom + drop dummy → slice → sever → chaosroar (gfury if not owned) → s cane → ( tc → smokecloud + r cane → gmaul eofspec → gmaul eofspec → enhreplen → gmaul eofspec → deci / backhandflank)";
		String expectedCanonical = "anti+surge→freedom+(Drop)dummy→slice→sever→chaosroar(gfury if not owned)→(s)cane→tc→smokecloud+(r)cane→gmaul+eofspec→gmaul+eofspec→enhreplen→gmaul+eofspec→deci/backhandflank";

		assertParsesForDetection(input);
		assertRoundTripsViaUiParsing(input, expectedCanonical);

		List<SequenceElement> elements = new SequenceVisualService().parseToVisualElements(input);
		List<String> tooltipMessages = elements.stream()
				.filter(SequenceElement::isTooltip)
				.map(SequenceElement::getValue)
				.toList();
		assertEquals(List.of("Drop", "gfury if not owned", "s", "r"), tooltipMessages);

		assertEquals(expectedCanonical, RotationDslCodec.exportSimple(input));
	}

	@Test
	void shouldAcceptAndNormalizeSecondExpressionExample() {
		String input = "surge + vulnbomb + bloat → deathskulls → volleyofsouls → soulsap → omniguard spec → necroauto → necroauto → ingen + roarofawakening odetodeceit spec (0 tick) → soulsap → touchofdeath → commandskeleton → soulsap → deathguard90 eofspec → necroauto → volleyofsouls → soulsap → necroauto → touchofdeath → soulsap";
		String expectedCanonical = "surge+vulnbomb+bloat→deathskulls→volleyofsouls→soulsap→omniguard+spec→necroauto→necroauto→ingen+roarofawakening+odetodeceit+spec(0 tick)→soulsap→touchofdeath→commandskeleton→soulsap→deathguard90+eofspec→necroauto→volleyofsouls→soulsap→necroauto→touchofdeath→soulsap";

		assertParsesForDetection(input);
		assertRoundTripsViaUiParsing(input, expectedCanonical);

		List<SequenceElement> elements = new SequenceVisualService().parseToVisualElements(input);
		List<String> tooltipMessages = elements.stream()
				.filter(SequenceElement::isTooltip)
				.map(SequenceElement::getValue)
				.toList();
		assertEquals(List.of("0 tick"), tooltipMessages);

		assertEquals(expectedCanonical, RotationDslCodec.exportSimple(input));
	}

	@Test
	void shouldAllowBareSpecOrEofspecToken() {
		assertParsesForDetection("spec");
		assertParsesForDetection("eofspec");
		assertRoundTripsViaUiParsing("spec", "spec");
		assertRoundTripsViaUiParsing("eofspec", "eofspec");
	}

	@Test
	void shouldParseMultilineSequenceWithZeroWidthSpacesAndTickKeyword() {
		String input = String.join("\n",
				" ",
				"livingdeath + adrenrenewal → touchofdeath → deathskulls → divert → splitsoul → volleyofsouls → soulsap → fingerofdeath → commandskeleton",
				"",
				"\u200B",
				"",
				"deathskulls + 2t undeadslayer → soulsap → touchofdeath → fingerofdeath → soulsap → volleyofsouls → necroauto / fingerofdeath (if stacks) → deathskulls",
				"\u200B",
				" "
		);
		String expectedCanonical = "livingdeath+adrenrenewal→touchofdeath→deathskulls→divert→splitsoul→volleyofsouls→soulsap→fingerofdeath→commandskeleton→deathskulls+(2t)undeadslayer→soulsap→touchofdeath→fingerofdeath→soulsap→volleyofsouls→necroauto/fingerofdeath(if stacks)→deathskulls";

		assertParsesForDetection(input);
		assertRoundTripsViaUiParsing(input, expectedCanonical);

		List<SequenceElement> elements = new SequenceVisualService().parseToVisualElements(input);
		List<String> tooltipMessages = elements.stream()
				.filter(SequenceElement::isTooltip)
				.map(SequenceElement::getValue)
				.toList();
		assertEquals(List.of("2t", "if stacks"), tooltipMessages);
	}

	private static void assertParsesForDetection(String input) {
		TooltipScheduleBuilder builder = new TooltipScheduleBuilder();
		TooltipScheduleBuilder.BuildResult result = builder.build(input);
		assertNotNull(result.definition(), () -> "Expected expression to build a definition for detection: " + input);
	}

	private static void assertRoundTripsViaUiParsing(String input, String expectedCanonical) {
		SequenceVisualService visualService = new SequenceVisualService();
		ExpressionBuilder expressionBuilder = new ExpressionBuilder();

		List<SequenceElement> elements = visualService.parseToVisualElements(input);
		assertFalse(elements.isEmpty(), () -> "Expected UI parsing to produce elements: " + input);

		String rebuilt = expressionBuilder.buildExpression(elements);
		assertEquals(expectedCanonical, rebuilt, () -> "Expected canonical expression mismatch for input: " + input);
		assertDoesNotThrow(() -> SequenceParser.parse(rebuilt), "Canonical expression must remain parseable");
	}
}
