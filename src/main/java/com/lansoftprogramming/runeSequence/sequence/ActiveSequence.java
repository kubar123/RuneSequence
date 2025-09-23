package com.lansoftprogramming.runeSequence.sequence;

import com.lansoftprogramming.runeSequence.detection.DetectionResult;
import com.lansoftprogramming.runeSequence.config.ConfigManager;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class ActiveSequence {
	private static final Logger logger = LoggerFactory.getLogger(ActiveSequence.class);
	private final SequenceDefinition definition;
	private final ConfigManager configManager;

	private int currentStepIndex = 0;
	private final StepTimer stepTimer;

	private Map<String, DetectionResult> lastDetections = new HashMap<>();

	public ActiveSequence(SequenceDefinition def, ConfigManager configManager) {
		this.definition = def;
		this.configManager = configManager;
		this.stepTimer = new StepTimer();

		logger.debug("ActiveSequence: Created with {} steps", def.getSteps().size());
		this.stepTimer.startStep(def.getStep(currentStepIndex), configManager);
	}

	public List<String> getRequiredTemplates() {
		Step current = getCurrentStep();
		Step next = getNextStep();
		List<String> result = new ArrayList<>();

		logger.debug("ActiveSequence.getRequiredTemplates: currentStepIndex={}", currentStepIndex);

		if (current != null) {
			List<String> currentTemplates = current.getDetectableTokens(configManager);
			result.addAll(currentTemplates);
			logger.debug("  Current step templates: {}", currentTemplates);
		}

		if (next != null) {
			List<String> nextTemplates = next.getDetectableTokens(configManager);
			result.addAll(nextTemplates);
			logger.debug("  Next step templates: {}", nextTemplates);
		}

		logger.debug("  Total required templates: {}", result);
		return result;
	}

	public void processDetections(List<DetectionResult> results) {
		logger.debug("ActiveSequence.processDetections: Received {} results", results.size());

		lastDetections.clear();
		for (DetectionResult r : results) {
			lastDetections.put(r.templateName, r);
			logger.debug("  Stored detection: {} found={}", r.templateName, r.found);
		}

		logger.debug("  Checking if step is satisfied...");
		if (stepTimer.isStepSatisfied(lastDetections)) {
			logger.debug("  Step satisfied! Advancing...");
			advanceStep();
		} else {
			logger.debug("  Step not yet satisfied");
		}
	}

	public List<DetectionResult> getCurrentAbilities() {
		Step step = getCurrentStep();
		if (step == null) {
			logger.debug("ActiveSequence.getCurrentAbilities: No current step");
			return List.of();
		}

		List<DetectionResult> current = step.flattenDetections(lastDetections);
		logger.debug("ActiveSequence.getCurrentAbilities: {} abilities", current.size());

		for (DetectionResult result : current) {
			logger.debug("  Current ability: {} found={}", result.templateName, result.found);
		}

		return current;
	}

	public List<DetectionResult> getNextAbilities() {
		Step step = getNextStep();
		if (step == null) {
			logger.debug("ActiveSequence.getNextAbilities: No next step");
			return List.of();
		}

		List<DetectionResult> next = step.flattenDetections(lastDetections);
		logger.debug("ActiveSequence.getNextAbilities: {} abilities", next.size());

		for (DetectionResult result : next) {
			logger.debug("  Next ability: {} found={}", result.templateName, result.found);
		}

		return next;
	}

	private Step getCurrentStep() {
		if (currentStepIndex >= definition.size()) {
			logger.debug("getCurrentStep: Index {} >= {}", currentStepIndex, definition.size());
			return null;
		}
		return definition.getStep(currentStepIndex);
	}

	private Step getNextStep() {
		if (currentStepIndex + 1 >= definition.size()) {
			logger.debug("getNextStep: No next step (currentIndex={})", currentStepIndex);
			return null;
		}
		return definition.getStep(currentStepIndex + 1);
	}

	private void advanceStep() {
		if (currentStepIndex >= definition.size() - 1) {
			logger.debug("advanceStep: Already at last step");
			return;
		}

		currentStepIndex++;
		logger.debug("advanceStep: Advanced to step {}", currentStepIndex);

		Step step = getCurrentStep();
		stepTimer.startStep(step, configManager);
	}

	public void reset() {
		logger.debug("ActiveSequence.reset: Resetting to step 0");
		currentStepIndex = 0;
		stepTimer.reset();
	}
}