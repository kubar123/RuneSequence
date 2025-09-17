package com.lansoftprogramming.runeSequence.sequence;


import com.lansoftprogramming.runeSequence.detection.DetectionResult;
import com.lansoftprogramming.runeSequence.config.ConfigManager;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class ActiveSequence {

	private final SequenceDefinition definition;
	private final ConfigManager configManager;

	private int currentStepIndex = 0;
	private final StepTimer stepTimer;

	private Map<String, DetectionResult> lastDetections = new HashMap<>();

	public ActiveSequence(SequenceDefinition def, ConfigManager configManager) {
		this.definition = def;
		this.configManager = configManager;
		this.stepTimer = new StepTimer();
		this.stepTimer.startStep(def.getStep(currentStepIndex), configManager);
	}

	public List<String> getRequiredTemplates() {
		Step current = getCurrentStep();
		Step next = getNextStep();
		List<String> result = new ArrayList<>();
		if (current != null) result.addAll(current.getDetectableTokens(configManager));
		if (next != null) result.addAll(next.getDetectableTokens(configManager));
		return result;
	}

	public void processDetections(List<DetectionResult> results) {
		lastDetections.clear();
		for (DetectionResult r : results) {
			lastDetections.put(r.templateName, r);
		}

		if (stepTimer.isStepSatisfied(lastDetections)) {
			advanceStep();
		}
	}

	public List<DetectionResult> getCurrentAbilities() {
		Step step = getCurrentStep();
		if (step == null) return List.of();
		return step.flattenDetections(lastDetections);
	}

	public List<DetectionResult> getNextAbilities() {
		Step step = getNextStep();
		if (step == null) return List.of();
		return step.flattenDetections(lastDetections);
	}

	private Step getCurrentStep() {
		return definition.getStep(currentStepIndex);
	}

	private Step getNextStep() {
		return definition.getStep(currentStepIndex + 1);
	}

	private void advanceStep() {
		if (currentStepIndex >= definition.size() - 1) return;
		currentStepIndex++;
		Step step = getCurrentStep();
		stepTimer.startStep(step, configManager);
	}

	public void reset() {
		currentStepIndex = 0;
		stepTimer.reset();
	}
}
