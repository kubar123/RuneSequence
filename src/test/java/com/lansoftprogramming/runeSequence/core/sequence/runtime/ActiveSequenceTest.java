package com.lansoftprogramming.runeSequence.core.sequence.runtime;

import com.lansoftprogramming.runeSequence.core.detection.DetectionResult;
import com.lansoftprogramming.runeSequence.core.sequence.model.*;
import com.lansoftprogramming.runeSequence.infrastructure.config.AbilityConfig;
import org.junit.jupiter.api.Test;

import java.awt.*;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class ActiveSequenceTest {

	@Test
	void detectionRequirementsShouldIncludeAlternativesAndNextStep() {
		SequenceDefinition definition = new SequenceDefinition(List.of(
				new Step(List.of(new Term(List.of(new Alternative("Alpha"), new Alternative("Beta"))))),
				new Step(List.of(new Term(List.of(new Alternative("Alpha")))))
		));

		ActiveSequence activeSequence = new ActiveSequence(definition, abilityConfig("Alpha", "Beta"));

		List<ActiveSequence.DetectionRequirement> requirements = activeSequence.getDetectionRequirements();

		assertEquals(3, requirements.size());
		assertEquals("Alpha#0", requirements.get(0).instanceId());
		assertTrue(requirements.get(0).isAlternative(), "Term with multiple alternatives should flag instances as alternative");

		assertEquals("Beta#0", requirements.get(1).instanceId());
		assertTrue(requirements.get(1).isAlternative());

		assertEquals("Alpha#1", requirements.get(2).instanceId(), "Next step should be indexed and included");
		assertFalse(requirements.get(2).isAlternative());
	}

	@Test
	void resetShouldReturnToFirstStepAndClearDetections() {
		SequenceDefinition definition = new SequenceDefinition(List.of(
				new Step(List.of(new Term(List.of(new Alternative("Alpha"), new Alternative("Beta"))))),
				new Step(List.of(new Term(List.of(new Alternative("Gamma")))))
		));

		AbilityConfig abilityConfig = abilityConfig("Alpha", "Beta", "Gamma");
		// Make the timer advance instantly so processing a detection immediately steps forward.
		for (AbilityConfig.AbilityData data : abilityConfig.getAbilities().values()) {
			data.setTriggersGcd(false);
		}

		ActiveSequence activeSequence = new ActiveSequence(definition, abilityConfig);

		List<DetectionResult> initial = activeSequence.getCurrentAbilities();
		assertEquals(List.of("Alpha#0", "Beta#0"), initial.stream().map(r -> r.templateName).toList());
		assertTrue(initial.stream().allMatch(r -> !r.found));

		List<DetectionResult> detections = List.of(
				DetectionResult.found("Alpha#0", new Point(1, 1), 0.9, new Rectangle(0, 0, 1, 1), true),
				DetectionResult.notFound("Beta#0", true)
		);
		activeSequence.processDetections(detections);

		List<DetectionResult> stepTwo = activeSequence.getCurrentAbilities();
		assertEquals(1, stepTwo.size());
		assertEquals("Gamma#0", stepTwo.get(0).templateName);
		assertFalse(stepTwo.get(0).isAlternative);

		activeSequence.reset();
		List<DetectionResult> afterReset = activeSequence.getCurrentAbilities();
		assertEquals(List.of("Alpha#0", "Beta#0"), afterReset.stream().map(r -> r.templateName).toList());
		assertTrue(afterReset.stream().allMatch(r -> !r.found), "Reset should clear detection state");
	}

	@Test
	void detectionRequirementsShouldExposeEffectiveOverrides() {
		AbilityConfig abilityConfig = new AbilityConfig();
		AbilityConfig.AbilityData base = new AbilityConfig.AbilityData();
		base.setTriggersGcd(true);
		base.setDetectionThreshold(0.5);
		abilityConfig.putAbility("Alpha", base);

		AbilitySettingsOverrides overrides = AbilitySettingsOverrides.builder()
				.triggersGcd(false)
				.detectionThreshold(0.9)
				.castDuration((short) 4)
				.build();

		SequenceDefinition definition = new SequenceDefinition(List.of(
				new Step(List.of(new Term(List.of(new Alternative("Alpha", overrides)))))
		));

		ActiveSequence activeSequence = new ActiveSequence(definition, abilityConfig);

		ActiveSequence.DetectionRequirement requirement = activeSequence.getDetectionRequirements().get(0);
		EffectiveAbilityConfig effective = requirement.effectiveAbilityConfig();

		assertNotNull(effective);
		assertEquals("Alpha", effective.getAbilityKey());
		assertFalse(effective.isTriggersGcd(), "Override should apply to triggersGcd");
		assertEquals(0.9, effective.getDetectionThreshold().orElseThrow());
		assertEquals(4, effective.getCastDuration());
	}

	@Test
	void detectionRequirementsShouldUseBaseConfigWhenNoOverridesPresent() {
		AbilityConfig abilityConfig = new AbilityConfig();
		AbilityConfig.AbilityData base = new AbilityConfig.AbilityData();
		base.setTriggersGcd(true);
		base.setDetectionThreshold(0.7);
		abilityConfig.putAbility("Alpha", base);

		SequenceDefinition definition = new SequenceDefinition(List.of(
				new Step(List.of(new Term(List.of(new Alternative("Alpha")))))
		));

		ActiveSequence activeSequence = new ActiveSequence(definition, abilityConfig);
		ActiveSequence.DetectionRequirement requirement = activeSequence.getDetectionRequirements().get(0);

		assertTrue(requirement.effectiveAbilityConfig().isTriggersGcd());
		assertEquals(0.7, requirement.effectiveAbilityConfig().getDetectionThreshold().orElseThrow());
	}

	@Test
	void latchStartShouldAdvanceToNextStepAndStartTimingFromLatch() {
		SequenceDefinition definition = new SequenceDefinition(List.of(
				new Step(List.of(new Term(List.of(new Alternative("Alpha"))))),
				new Step(List.of(new Term(List.of(new Alternative("Beta")))))
		));

		AbilityConfig abilityConfig = new AbilityConfig();
		AbilityConfig.AbilityData alpha = new AbilityConfig.AbilityData();
		alpha.setTriggersGcd(true);
		abilityConfig.putAbility("Alpha", alpha);

		AbilityConfig.AbilityData beta = new AbilityConfig.AbilityData();
		beta.setTriggersGcd(true);
		abilityConfig.putAbility("Beta", beta);

		ActiveSequence activeSequence = new ActiveSequence(definition, abilityConfig);
		activeSequence.stepTimer.pause();

		assertEquals(0, activeSequence.getCurrentStepIndex());
		assertTrue(activeSequence.stepTimer.isPaused());

		activeSequence.onLatchStart(System.currentTimeMillis());

		assertEquals(1, activeSequence.getCurrentStepIndex(), "Latch start should advance to the next step to press");
		assertFalse(activeSequence.stepTimer.isPaused(), "Latch start should begin step timing immediately");
	}

	private AbilityConfig abilityConfig(String... names) {
		AbilityConfig config = new AbilityConfig();
		for (String name : names) {
			AbilityConfig.AbilityData data = new AbilityConfig.AbilityData();
			// Zero the timers so steps complete immediately for deterministic tests
			data.setTriggersGcd(false);
			config.putAbility(name, data);
		}
		return config;
	}
}
