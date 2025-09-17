package com.lansoftprogramming.runeSequence.sequence;

import com.lansoftprogramming.runeSequence.config.AbilityConfig;
import com.lansoftprogramming.runeSequence.config.ConfigManager;
import com.lansoftprogramming.runeSequence.detection.DetectionResult;

import java.util.Map;


/**
 * Manages GCD / step cooldowns
 */
public class StepTimer {

	private long stepStartTimeMs;
	private long stepDurationMs;

	public void startStep(Step step, ConfigManager configManager) {
		stepStartTimeMs = System.currentTimeMillis();
		stepDurationMs = calculateStepDuration(step, configManager);
	}

	public boolean isStepSatisfied(Map<String, DetectionResult> lastDetections) {
		long now = System.currentTimeMillis();
		return now - stepStartTimeMs >= stepDurationMs;
	}

	public void reset() {
		stepStartTimeMs = 0;
		stepDurationMs = 0;
	}

	private long calculateStepDuration(Step step, ConfigManager configManager) {
		long maxTicks = 3; // default 3 ticks
		AbilityConfig abilities = configManager.getAbilities();

		for (String token : step.getDetectableTokens(configManager)) {
			AbilityConfig.AbilityData data = abilities.getAbility(token);
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
