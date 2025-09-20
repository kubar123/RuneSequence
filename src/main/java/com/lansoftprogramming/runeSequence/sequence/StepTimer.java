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

	public void startStep(Step step, AbilityConfig abilityConfig) {
		stepStartTimeMs = System.currentTimeMillis();
		stepDurationMs = calculateStepDuration(step, abilityConfig);
	}

	public boolean isStepSatisfied(Map<String, DetectionResult> lastDetections) {
		long now = System.currentTimeMillis();
		return now - stepStartTimeMs >= stepDurationMs;
	}

	public void reset() {
		stepStartTimeMs = 0;
		stepDurationMs = 0;
	}

	private long calculateStepDuration(Step step, AbilityConfig abilityConfig) {
		long maxTicks = 3; // default 3 ticks

		for (String token : step.getDetectableTokens(abilityConfig)) {
			AbilityConfig.AbilityData data = abilityConfig.getAbility(token);
			if (data != null) {
				if (data.isTriggersGcd()) maxTicks = Math.max(maxTicks, 3);
				// allow custom cooldowns here
				if (data.getCooldown() > 0) {
					long ticks = (long) Math.ceil(data.getCooldown() / 0.6); // 1 tick = 0.6s
					maxTicks = Math.max(maxTicks, ticks);
				}
			}
		}
		return maxTicks * 600; // ms
	}
}
