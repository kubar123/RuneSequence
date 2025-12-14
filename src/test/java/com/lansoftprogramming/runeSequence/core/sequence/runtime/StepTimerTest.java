package com.lansoftprogramming.runeSequence.core.sequence.runtime;

import com.lansoftprogramming.runeSequence.core.sequence.model.AbilitySettingsOverrides;
import com.lansoftprogramming.runeSequence.core.sequence.model.Alternative;
import com.lansoftprogramming.runeSequence.core.sequence.model.Step;
import com.lansoftprogramming.runeSequence.core.sequence.model.Term;
import com.lansoftprogramming.runeSequence.infrastructure.config.AbilityConfig;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Field;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

class StepTimerTest {

	@Test
	void shouldUseLongestEffectiveAbilityDurationWithoutSleeping() {
		AbilityConfig abilityConfig = new AbilityConfig();
		abilityConfig.putAbility("Alpha", abilityData(false, (short) 1, (short) 0)); // 1 tick (600ms)
		abilityConfig.putAbility("Beta", abilityData(false, (short) 0, (short) 2));  // 2 ticks (1200ms)

		Step step = new Step(List.of(
				new Term(List.of(new Alternative("Alpha"))),
				new Term(List.of(new Alternative("Beta")))
		));

		StepTimer timer = new StepTimer();
		timer.startStep(step, abilityConfig);

		assertFalse(timer.isStepSatisfied(Map.of()), "Step should not be satisfied immediately");

		// 700ms > Alpha's 600ms but < Beta's 1200ms
		setField(timer, "stepStartTimeMs", System.currentTimeMillis() - 700);
		assertFalse(timer.isStepSatisfied(Map.of()), "Timer should honor the longest effective duration across abilities");

		long expectedDurationMs = 2L * 600;
		setField(timer, "stepStartTimeMs", System.currentTimeMillis() - expectedDurationMs - 50);

		assertTrue(timer.isStepSatisfied(Map.of()), "Elapsed time beyond the longest effective duration should satisfy the step without wall-clock sleeps");
	}

	@Test
	void shouldUseDefaultGcdTicksWhenAbilityTriggersGcdAndNoCastDuration() {
		AbilityConfig abilityConfig = new AbilityConfig();
		abilityConfig.putAbility("Gcd", abilityData(true, (short) 0, (short) 0)); // Default GCD should apply (3 ticks)

		Step step = new Step(List.of(new Term(List.of(new Alternative("Gcd")))));

		StepTimer timer = new StepTimer();
		timer.startStep(step, abilityConfig);

		long expectedDurationMs = 3L * 600;
		setField(timer, "stepStartTimeMs", System.currentTimeMillis() - expectedDurationMs + 50);
		assertFalse(timer.isStepSatisfied(Map.of()), "Step should not satisfy before default GCD duration elapses");

		setField(timer, "stepStartTimeMs", System.currentTimeMillis() - expectedDurationMs - 50);
		assertTrue(timer.isStepSatisfied(Map.of()), "Default GCD duration should satisfy once elapsed");
	}

	@Test
	void cooldownShouldOverrideDefaultGcdWhenLonger() {
		AbilityConfig abilityConfig = new AbilityConfig();
		abilityConfig.putAbility("GcdLongCd", abilityData(true, (short) 0, (short) 5)); // cooldown ticks > default GCD

		Step step = new Step(List.of(new Term(List.of(new Alternative("GcdLongCd")))));

		StepTimer timer = new StepTimer();
		timer.startStep(step, abilityConfig);

		long expectedDurationMs = 5L * 600;
		setField(timer, "stepStartTimeMs", System.currentTimeMillis() - expectedDurationMs + 50);
		assertFalse(timer.isStepSatisfied(Map.of()));

		setField(timer, "stepStartTimeMs", System.currentTimeMillis() - expectedDurationMs - 50);
		assertTrue(timer.isStepSatisfied(Map.of()));
	}

	@Test
	void shouldNotAdvanceWhilePausedAndHonorElapsedAfterResume() {
		AbilityConfig abilityConfig = new AbilityConfig();
		abilityConfig.putAbility("Gamma", abilityData(false, (short) 1, (short) 0)); // 600ms

		Step step = new Step(List.of(new Term(List.of(new Alternative("Gamma")))));

		StepTimer timer = new StepTimer();
		timer.startStep(step, abilityConfig);
		timer.pause();

		// Simulate elapsed time while paused - should still report not satisfied
		setField(timer, "stepStartTimeMs", System.currentTimeMillis() - 2_000);
		assertFalse(timer.isStepSatisfied(Map.of()), "Paused steps must not satisfy even after elapsed time");

		timer.resume();
		assertTrue(timer.isStepSatisfied(Map.of()), "After resuming, elapsed time should allow the step to satisfy");
	}

	@Test
	void shouldHonorOverridesWhenCalculatingStepDuration() {
		AbilityConfig abilityConfig = new AbilityConfig();
		AbilityConfig.AbilityData baseData = abilityData(true, (short) 0, (short) 0);
		abilityConfig.putAbility("Override", baseData);

		AbilitySettingsOverrides overrides = AbilitySettingsOverrides.builder()
				.castDuration((short) 4) // Override default GCD ticks (3) to 4
				.build();

		Step step = new Step(List.of(new Term(List.of(new Alternative("Override", overrides)))));

		StepTimer timer = new StepTimer();
		timer.startStep(step, abilityConfig);

		long expectedDurationMs = 4L * 600;
		setField(timer, "stepStartTimeMs", System.currentTimeMillis() - expectedDurationMs + 50);
		assertFalse(timer.isStepSatisfied(Map.of()), "Duration should respect overridden cast duration");

		setField(timer, "stepStartTimeMs", System.currentTimeMillis() - expectedDurationMs - 50);
		assertTrue(timer.isStepSatisfied(Map.of()), "Step should satisfy after the overridden duration elapses");
	}

	@Test
	void shouldTreatNegativeOverridesAsZero() {
		AbilityConfig abilityConfig = new AbilityConfig();
		AbilityConfig.AbilityData baseData = abilityData(true, (short) 0, (short) 0);
		abilityConfig.putAbility("Neg", baseData);

		AbilitySettingsOverrides overrides = AbilitySettingsOverrides.builder()
				.castDuration((short) -4)
				.cooldown((short) -3)
				.build();

		Step step = new Step(List.of(new Term(List.of(new Alternative("Neg", overrides)))));

		StepTimer timer = new StepTimer();
		timer.startStep(step, abilityConfig);

		assertFalse(timer.isStepSatisfied(Map.of()), "Step should not be satisfied immediately with negative overrides");
		setField(timer, "stepStartTimeMs", System.currentTimeMillis() - 100);
		assertFalse(timer.isStepSatisfied(Map.of()), "Step duration should remain non-negative even with corrupted overrides");
	}

	private AbilityConfig.AbilityData abilityData(boolean triggersGcd, short castDuration, short cooldown) {
		AbilityConfig.AbilityData data = new AbilityConfig.AbilityData();
		data.setTriggersGcd(triggersGcd);
		data.setCastDuration(castDuration);
		data.setCooldown(cooldown);
		return data;
	}

	private void setField(StepTimer timer, String name, long value) {
		try {
			Field field = StepTimer.class.getDeclaredField(name);
			field.setAccessible(true);
			field.setLong(timer, value);
		} catch (Exception e) {
			fail("Failed to set field '" + name + "' for test setup: " + e.getMessage());
		}
	}
}
