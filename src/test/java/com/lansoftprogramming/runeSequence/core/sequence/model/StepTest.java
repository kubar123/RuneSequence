package com.lansoftprogramming.runeSequence.core.sequence.model;

import com.lansoftprogramming.runeSequence.core.detection.DetectionResult;
import com.lansoftprogramming.runeSequence.infrastructure.config.AbilityConfig;
import org.junit.jupiter.api.Test;

import java.awt.*;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

class StepTest {

	@Test
	void getDetectableTokensShouldFlattenNestedGroups() {
		AbilityConfig abilityConfig = abilityConfig("Alpha", "Gamma", "Delta");

		SequenceDefinition subgroup = new SequenceDefinition(List.of(
				new Step(List.of(new Term(List.of(new Alternative("Gamma"))))),
				new Step(List.of(new Term(List.of(new Alternative("Delta"), new Alternative("Missing")))))
		));

		Step step = new Step(List.of(
				new Term(List.of(new Alternative("Alpha"), new Alternative("Beta"))),
				new Term(List.of(new Alternative(subgroup)))
		));

		List<String> detectable = step.getDetectableTokens(abilityConfig);

		assertEquals(List.of("Alpha", "Gamma", "Delta"), detectable, "Unknown abilities must be skipped even inside subgroups");
	}

	@Test
	void flattenDetectionsShouldPropagateAlternativeFlags() {
		SequenceDefinition nested = new SequenceDefinition(List.of(
				new Step(List.of(new Term(List.of(new Alternative("Gamma"), new Alternative("Delta"))))),
				new Step(List.of(new Term(List.of(new Alternative("Epsilon")))))
		));

		Step step = new Step(List.of(
				new Term(List.of(new Alternative("Alpha"), new Alternative("Beta"))),
				new Term(List.of(new Alternative(nested)))
		));

		Map<String, DetectionResult> lastDetections = new HashMap<>();
		lastDetections.put("Alpha", DetectionResult.found("Alpha", new Point(1, 1), 0.95, new Rectangle(0, 0, 1, 1), false));
		lastDetections.put("Gamma", DetectionResult.found("Gamma", new Point(2, 2), 0.90, new Rectangle(1, 1, 1, 1), false));
		lastDetections.put("Epsilon", DetectionResult.found("Epsilon", new Point(3, 3), 0.85, new Rectangle(2, 2, 1, 1), false));

		List<DetectionResult> flattened = step.flattenDetections(lastDetections);

		assertEquals(5, flattened.size());

		DetectionResult alpha = flattened.get(0);
		assertTrue(alpha.found);
		assertEquals("Alpha", alpha.templateName);
		assertTrue(alpha.isAlternative, "Term-level OR should mark existing detections as alternative");

		DetectionResult beta = flattened.get(1);
		assertFalse(beta.found);
		assertEquals("Beta", beta.templateName);
		assertTrue(beta.isAlternative);

		DetectionResult gamma = flattened.get(2);
		assertTrue(gamma.found);
		assertEquals("Gamma", gamma.templateName);
		assertTrue(gamma.isAlternative, "Nested subgroup OR should still flag detections as alternatives");

		DetectionResult delta = flattened.get(3);
		assertFalse(delta.found);
		assertTrue(delta.isAlternative);

		DetectionResult epsilon = flattened.get(4);
		assertTrue(epsilon.found);
		assertFalse(epsilon.isAlternative, "Single-option terms in subgroups must remain non-alternative");
	}

	@Test
	void flattenDetectionsShouldKeepExistingAlternativeFlag() {
		Step step = new Step(List.of(new Term(List.of(new Alternative("Solo")))));

		Map<String, DetectionResult> lastDetections = new HashMap<>();
		lastDetections.put("Solo", DetectionResult.found("Solo", new Point(5, 5), 0.99, new Rectangle(0, 0, 1, 1), true));

		List<DetectionResult> flattened = step.flattenDetections(lastDetections);

		assertEquals(1, flattened.size());
		assertTrue(flattened.get(0).isAlternative, "Existing alternative flag should not be cleared when term is singular");
	}

	private AbilityConfig abilityConfig(String... names) {
		AbilityConfig config = new AbilityConfig();
		for (String name : names) {
			config.putAbility(name, new AbilityConfig.AbilityData());
		}
		return config;
	}
}
