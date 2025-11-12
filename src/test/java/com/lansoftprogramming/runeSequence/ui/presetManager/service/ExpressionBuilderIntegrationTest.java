package com.lansoftprogramming.runeSequence.ui.presetManager.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.lansoftprogramming.runeSequence.core.sequence.model.SequenceDefinition;
import com.lansoftprogramming.runeSequence.core.sequence.model.Step;
import com.lansoftprogramming.runeSequence.core.sequence.model.Term;
import com.lansoftprogramming.runeSequence.core.sequence.parser.SequenceParser;
import com.lansoftprogramming.runeSequence.infrastructure.config.AbilityConfig;
import com.lansoftprogramming.runeSequence.ui.presetManager.model.SequenceElement;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

class ExpressionBuilderIntegrationTest {

	private static final Path ROTATIONS_PATH = Paths.get("src/main/resources/defaults/rotations.json");
	private static final Path ABILITIES_PATH = Paths.get("src/main/resources/defaults/abilities.json");

	private static final ObjectMapper MAPPER = new ObjectMapper();

	private static List<String> defaultExpressions;
	private static AbilityConfig defaultAbilityConfig;

	private final ExpressionBuilder expressionBuilder = new ExpressionBuilder();
	private final SequenceVisualService visualService = new SequenceVisualService();

	@BeforeAll
	static void loadFixtures() throws IOException {
		defaultExpressions = readDefaultExpressions();
		defaultAbilityConfig = readAbilityConfig();

		assertFalse(defaultExpressions.isEmpty(), "defaults/rotations.json should provide at least one preset expression");
		assertNotNull(defaultAbilityConfig, "defaults/abilities.json should deserialize into AbilityConfig");
	}

	@Test
	void shouldRoundTripDefaultRotationsThroughExpressionBuilder() {
		for (String expression : defaultExpressions) {
			final String expr = expression;
			SequenceDefinition original = SequenceParser.parse(expr);
			String canonical = original.toString();

			List<SequenceElement> elements = visualService.parseToVisualElements(expr);
			assertFalse(elements.isEmpty(), () -> "Visual pipeline produced no elements for expression: " + expr);

			long abilityCount = elements.stream().filter(SequenceElement::isAbility).count();
			assertTrue(abilityCount > 0, () -> "Visual pipeline should expose at least one ability for expression: " + expr);

			String rebuiltExpression = expressionBuilder.buildExpression(elements);
			SequenceDefinition rebuilt = SequenceParser.parse(rebuiltExpression);

			assertEquals(canonical, rebuilt.toString(), () -> "ExpressionBuilder round-trip mismatch for expression: " + expr);
			assertEquals(original.size(), rebuilt.size(), () -> "Step counts diverged for expression: " + expr);
		}
	}

	@Test
	void shouldProduceWellFormedStepsWithDetectableTokens() {
		for (String expression : defaultExpressions) {
			final String expr = expression;
			SequenceDefinition definition = SequenceParser.parse(expr);
			assertTrue(definition.size() > 0, () -> "Parsed definition should expose steps for expression: " + expr);

			for (int stepIndex = 0; stepIndex < definition.size(); stepIndex++) {
				final int idx = stepIndex;
				Step step = definition.getStep(stepIndex);
				assertNotNull(step, () -> "Step should not be null at index " + idx + " for expression: " + expr);
				assertFalse(step.getTerms().isEmpty(), () -> "Step must contain at least one term; expression: " + expr);

				for (Term term : step.getTerms()) {
					assertFalse(term.getAlternatives().isEmpty(), () -> "Term must expose alternatives; expression: " + expr);
				}

				List<String> detectable = step.getDetectableTokens(defaultAbilityConfig);
				assertTrue(detectable.stream().allMatch(name -> defaultAbilityConfig.getAbility(name) != null),
						() -> "Detectable tokens should resolve to configured abilities; expression: " + expr);
			}
		}
	}

	private static List<String> readDefaultExpressions() throws IOException {
		if (!Files.exists(ROTATIONS_PATH)) {
			return List.of();
		}

		JsonNode root = MAPPER.readTree(Files.newBufferedReader(ROTATIONS_PATH));
		JsonNode presetsNode = root.path("presets");
		List<String> expressions = new ArrayList<>();

		if (presetsNode.isObject()) {
			for (Iterator<Map.Entry<String, JsonNode>> it = presetsNode.fields(); it.hasNext(); ) {
				Map.Entry<String, JsonNode> entry = it.next();
				JsonNode preset = entry.getValue();
				if (preset.hasNonNull("expression")) {
					String expression = preset.get("expression").asText();
					if (!expression.isBlank()) {
						expressions.add(expression);
					}
				}
			}
		}

		return expressions;
	}

	private static AbilityConfig readAbilityConfig() throws IOException {
		if (!Files.exists(ABILITIES_PATH)) {
			return null;
		}
		return MAPPER.readValue(Files.newBufferedReader(ABILITIES_PATH), AbilityConfig.class);
	}
}