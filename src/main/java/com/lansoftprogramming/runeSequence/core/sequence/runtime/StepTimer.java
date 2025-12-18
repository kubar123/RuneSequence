package com.lansoftprogramming.runeSequence.core.sequence.runtime;

import com.lansoftprogramming.runeSequence.core.detection.DetectionResult;
import com.lansoftprogramming.runeSequence.core.sequence.model.EffectiveAbilityConfig;
import com.lansoftprogramming.runeSequence.core.sequence.model.Step;
import com.lansoftprogramming.runeSequence.core.sequence.runtime.timing.AbilityTimingProfile;
import com.lansoftprogramming.runeSequence.core.sequence.runtime.timing.StepTimingView;
import com.lansoftprogramming.runeSequence.infrastructure.config.AbilityConfig;

import java.util.Map;
import java.util.function.UnaryOperator;


/**
 * Manages GCD / step cooldowns
 */
public class StepTimer implements StepTimingView {
	private long stepStartTimeMs;
	private long stepDurationMs;
	private long pausedAtMs = 0;
	private long totalPausedTimeMs = 0;
	private boolean isPaused = false;

	public void startStep(Step step, AbilityConfig abilityConfig) {
		startStep(step, abilityConfig, UnaryOperator.identity());
	}

	public void startStep(Step step, AbilityConfig abilityConfig, UnaryOperator<AbilityTimingProfile> timingTransformer) {
		stepDurationMs = calculateStepDuration(step, abilityConfig, timingTransformer != null ? timingTransformer : UnaryOperator.identity());
		restartAt(System.currentTimeMillis());
	}

	public void restartAt(long startTimeMs) {
		stepStartTimeMs = startTimeMs;
		totalPausedTimeMs = 0;
		pausedAtMs = 0;
		isPaused = false;
	}

	public void pause() {
		if (!isPaused) {
			pausedAtMs = System.currentTimeMillis();
			isPaused = true;
		}
	}

	public void resume() {
		if (isPaused) {
			totalPausedTimeMs += System.currentTimeMillis() - pausedAtMs;
			isPaused = false;
		}
	}

	public boolean isStepSatisfied(Map<String, DetectionResult> lastDetections) {
		if (isPaused) {
			return false; // Never satisfied while paused
		}

		long now = System.currentTimeMillis();
		return getEffectiveElapsedMs(now) >= stepDurationMs;
	}

	public void reset() {
		stepStartTimeMs = 0;
		stepDurationMs = 0;
		pausedAtMs = 0;
		totalPausedTimeMs = 0;
		isPaused = false;
	}

	public void setStepDurationMs(long durationMs) {
		stepDurationMs = Math.max(0, durationMs);
	}

	public void forceSatisfiedAt(long nowMs) {
		if (isPaused) {
			return;
		}
		stepStartTimeMs = nowMs - stepDurationMs - totalPausedTimeMs;
	}

	@Override
	public long getStepStartTimeMs() {
		return stepStartTimeMs;
	}

	@Override
	public long getStepDurationMs() {
		return stepDurationMs;
	}

	@Override
	public long getEffectiveElapsedMs(long nowMs) {
		if (stepStartTimeMs == 0) {
			return 0;
		}
		long pausedMs = totalPausedTimeMs;
		if (isPaused) {
			pausedMs += nowMs - pausedAtMs;
		}
		return (nowMs - stepStartTimeMs) - pausedMs;
	}

	@Override
	public boolean isPaused() {
		return isPaused;
	}

	private long calculateStepDuration(Step step, AbilityConfig abilityConfig, UnaryOperator<AbilityTimingProfile> timingTransformer) {
		final long DEFAULT_GCD_TICKS = 3;
		long maxTicks = 0;
		// The duration of a step is determined by the ability within it that takes the longest to be ready again.
		// This can be its cast time, its global cooldown (GCD), or its specific cooldown.
		for (EffectiveAbilityConfig ability : step.getEffectiveAbilityConfigs(abilityConfig)) {
			AbilityTimingProfile timingProfile = AbilityTimingProfile.from(ability);
			AbilityTimingProfile effectiveTiming = timingTransformer.apply(timingProfile);
			if (effectiveTiming == null) {
				effectiveTiming = timingProfile;
			}

			// 1. Determine the time taken by the ability's execution (cast or GCD).
			// A specific cast_duration overrides the default GCD.
			long castOrGcdTicks = 0;
			if (effectiveTiming.castDurationTicks() > 0) {
				castOrGcdTicks = effectiveTiming.castDurationTicks();
			} else if (effectiveTiming.triggersGcd()) {
				Short gcdOverride = effectiveTiming.gcdTicksOverride();
				castOrGcdTicks = gcdOverride != null ? gcdOverride.shortValue() : DEFAULT_GCD_TICKS;
			}
			// 2. Get the ability's own cooldown, which is independent of the GCD.
			// The value from the config is already in ticks.
			short cooldownTicks = effectiveTiming.cooldownTicks();
			// 3. The effective time for this ability is the longer of its execution time and its cooldown.
			short effectiveTicks = (short) Math.max(castOrGcdTicks, cooldownTicks);
			// 4. The step's total duration is dictated by the longest effective time of any ability in it.
			if(effectiveTicks>maxTicks){
				maxTicks = effectiveTicks;
			}
		}
		return maxTicks * 600;
	}
}
