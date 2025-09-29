package com.lansoftprogramming.runeSequence.sequence;

import com.lansoftprogramming.runeSequence.config.AbilityConfig;
import com.lansoftprogramming.runeSequence.detection.DetectionResult;

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
		long maxTicks = 3;
		for (String token : step.getDetectableTokens(abilityConfig)) {
			AbilityConfig.AbilityData data = abilityConfig.getAbility(token);
			if (data != null) {
				if (data.isTriggersGcd()) maxTicks = Math.max(maxTicks, 3);
				if (data.getCooldown() > 0) {
					long ticks = (long) Math.ceil(data.getCooldown() / 0.6);
					maxTicks = Math.max(maxTicks, ticks);
				}
			}
		}
		return maxTicks * 600;
	}
}