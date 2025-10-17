package com.lansoftprogramming.runeSequence.core.sequence.runtime;

import com.lansoftprogramming.runeSequence.core.sequence.model.Step;
import com.lansoftprogramming.runeSequence.infrastructure.config.AbilityConfig;
import com.lansoftprogramming.runeSequence.core.detection.DetectionResult;

import java.util.Map;


/**
 * Manages GCD / step cooldowns
 */
public class StepTimer {
	private long stepStartTimeMs;
	private long stepDurationMs;
	private long pausedAtMs = 0;
	private long totalPausedTimeMs = 0;
	private boolean isPaused = false;

	public void startStep(Step step, AbilityConfig abilityConfig) {
		stepStartTimeMs = System.currentTimeMillis();
		stepDurationMs = calculateStepDuration(step, abilityConfig);
		totalPausedTimeMs = 0;
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
		long effectiveElapsed = (now - stepStartTimeMs) - totalPausedTimeMs;
		return effectiveElapsed >= stepDurationMs;
	}

	public void reset() {
		stepStartTimeMs = 0;
		stepDurationMs = 0;
		pausedAtMs = 0;
		totalPausedTimeMs = 0;
		isPaused = false;
	}

	private long calculateStepDuration(Step step, AbilityConfig abilityConfig) {
		final long DEFAULT_GCD_TICKS = 3;
		long maxTicks = 0;
		// The duration of a step is determined by the ability within it that takes the longest to be ready again.
		// This can be its cast time, its global cooldown (GCD), or its specific cooldown.
		for (String token : step.getDetectableTokens(abilityConfig)) {
			AbilityConfig.AbilityData data = abilityConfig.getAbility(token);
			if(data==null){
				continue;
			}
			// 1. Determine the time taken by the ability's execution (cast or GCD).
			// A specific cast_duration overrides the default GCD.
			long castOrGcdTicks = 0;
			if (data.getCastDuration() > 0) {
				castOrGcdTicks = data.getCastDuration();
			} else if (data.isTriggersGcd()) {
				castOrGcdTicks = DEFAULT_GCD_TICKS;
			}
			// 2. Get the ability's own cooldown, which is independent of the GCD.
			// The value from the config is already in ticks.
			short cooldownTicks = data.getCooldown();
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