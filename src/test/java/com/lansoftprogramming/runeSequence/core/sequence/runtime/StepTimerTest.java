package com.lansoftprogramming.runeSequence.core.sequence.runtime;

import com.lansoftprogramming.runeSequence.core.sequence.model.AbilitySettingsOverrides;
import com.lansoftprogramming.runeSequence.core.sequence.model.Alternative;
import com.lansoftprogramming.runeSequence.core.sequence.model.Step;
import com.lansoftprogramming.runeSequence.core.sequence.model.Term;
import com.lansoftprogramming.runeSequence.infrastructure.config.AbilityConfig;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicLong;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class StepTimerTest {

	@Test
	void shouldUseLongestEffectiveAbilityDurationWithoutSleeping() {
		AtomicLong nowMs = new AtomicLong(1_000_000L);
		AbilityConfig abilityConfig = new AbilityConfig();
		abilityConfig.putAbility("Alpha", abilityData(false, (short) 1, (short) 0)); // 1 tick (600ms)
		abilityConfig.putAbility("Beta", abilityData(false, (short) 0, (short) 2));  // 2 ticks (1200ms)

		Step step = new Step(List.of(
				new Term(List.of(new Alternative("Alpha"))),
				new Term(List.of(new Alternative("Beta")))
		));

		StepTimer timer = new StepTimer(nowMs::get);
		timer.startStep(step, abilityConfig);

		assertFalse(timer.isStepSatisfied(Map.of()), "Step should not be satisfied immediately");

		// 700ms > Alpha's 600ms but < Beta's 1200ms
		nowMs.addAndGet(700);
		assertFalse(timer.isStepSatisfied(Map.of()), "Timer should honor the longest effective duration across abilities");

		long expectedDurationMs = 2L * 600;
		nowMs.addAndGet(expectedDurationMs - 700 + 50);

		assertTrue(timer.isStepSatisfied(Map.of()), "Elapsed time beyond the longest effective duration should satisfy the step without wall-clock sleeps");
	}

	@Test
	void shouldUseDefaultGcdTicksWhenAbilityTriggersGcdAndNoCastDuration() {
		AtomicLong nowMs = new AtomicLong(1_000_000L);
		AbilityConfig abilityConfig = new AbilityConfig();
		abilityConfig.putAbility("Gcd", abilityData(true, (short) 0, (short) 0)); // Default GCD should apply (3 ticks)

		Step step = new Step(List.of(new Term(List.of(new Alternative("Gcd")))));

		StepTimer timer = new StepTimer(nowMs::get);
		timer.startStep(step, abilityConfig);

		long expectedDurationMs = 3L * 600;
		nowMs.addAndGet(expectedDurationMs - 50);
		assertFalse(timer.isStepSatisfied(Map.of()), "Step should not satisfy before default GCD duration elapses");

		nowMs.addAndGet(100);
		assertTrue(timer.isStepSatisfied(Map.of()), "Default GCD duration should satisfy once elapsed");
	}

	@Test
	void cooldownShouldOverrideDefaultGcdWhenLonger() {
		AtomicLong nowMs = new AtomicLong(1_000_000L);
		AbilityConfig abilityConfig = new AbilityConfig();
		abilityConfig.putAbility("GcdLongCd", abilityData(true, (short) 0, (short) 5)); // cooldown ticks > default GCD

		Step step = new Step(List.of(new Term(List.of(new Alternative("GcdLongCd")))));

		StepTimer timer = new StepTimer(nowMs::get);
		timer.startStep(step, abilityConfig);

		long expectedDurationMs = 5L * 600;
		nowMs.addAndGet(expectedDurationMs - 50);
		assertFalse(timer.isStepSatisfied(Map.of()));

		nowMs.addAndGet(100);
		assertTrue(timer.isStepSatisfied(Map.of()));
	}

	@Test
	void shouldNotAdvanceWhilePausedAndHonorElapsedAfterResume() {
		AtomicLong nowMs = new AtomicLong(1_000_000L);
		AbilityConfig abilityConfig = new AbilityConfig();
		abilityConfig.putAbility("Gamma", abilityData(false, (short) 1, (short) 0)); // 600ms

		Step step = new Step(List.of(new Term(List.of(new Alternative("Gamma")))));

		StepTimer timer = new StepTimer(nowMs::get);
		timer.startStep(step, abilityConfig);

		// Simulate time passing while running (counts toward satisfying the step).
		nowMs.addAndGet(2_000);

		timer.pause();

		// Simulate time passing while paused - should not count toward satisfying the step.
		nowMs.addAndGet(1_000);
		assertFalse(timer.isStepSatisfied(Map.of()), "Paused steps must not satisfy even after elapsed time");

		timer.resume();
		assertTrue(timer.isStepSatisfied(Map.of()), "After resuming, elapsed time should allow the step to satisfy");
	}

	@Test
	void shouldHonorOverridesWhenCalculatingStepDuration() {
		AtomicLong nowMs = new AtomicLong(1_000_000L);
		AbilityConfig abilityConfig = new AbilityConfig();
		AbilityConfig.AbilityData baseData = abilityData(true, (short) 0, (short) 0);
		abilityConfig.putAbility("Override", baseData);

		AbilitySettingsOverrides overrides = AbilitySettingsOverrides.builder()
				.castDuration((short) 4) // Override default GCD ticks (3) to 4
				.build();

		Step step = new Step(List.of(new Term(List.of(new Alternative("Override", overrides)))));

		StepTimer timer = new StepTimer(nowMs::get);
		timer.startStep(step, abilityConfig);

		long expectedDurationMs = 4L * 600;
		nowMs.addAndGet(expectedDurationMs - 50);
		assertFalse(timer.isStepSatisfied(Map.of()), "Duration should respect overridden cast duration");

		nowMs.addAndGet(100);
		assertTrue(timer.isStepSatisfied(Map.of()), "Step should satisfy after the overridden duration elapses");
	}

	@Test
	void shouldTreatNegativeOverridesAsZero() {
		AtomicLong nowMs = new AtomicLong(1_000_000L);
		AbilityConfig abilityConfig = new AbilityConfig();
		AbilityConfig.AbilityData baseData = abilityData(true, (short) 0, (short) 0);
		abilityConfig.putAbility("Neg", baseData);

		AbilitySettingsOverrides overrides = AbilitySettingsOverrides.builder()
				.castDuration((short) -4)
				.cooldown((short) -3)
				.build();

		Step step = new Step(List.of(new Term(List.of(new Alternative("Neg", overrides)))));

		StepTimer timer = new StepTimer(nowMs::get);
		timer.startStep(step, abilityConfig);

		assertFalse(timer.isStepSatisfied(Map.of()), "Step should not be satisfied immediately with negative overrides");
		nowMs.addAndGet(100);
		assertFalse(timer.isStepSatisfied(Map.of()), "Step duration should remain non-negative even with corrupted overrides");
	}

	private AbilityConfig.AbilityData abilityData(boolean triggersGcd, short castDuration, short cooldown) {
		AbilityConfig.AbilityData data = new AbilityConfig.AbilityData();
		data.setTriggersGcd(triggersGcd);
		data.setCastDuration(castDuration);
		data.setCooldown(cooldown);
		return data;
	}
}