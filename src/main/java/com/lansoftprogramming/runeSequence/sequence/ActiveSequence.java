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

		System.out.println("ActiveSequence: Created with " + def.getSteps().size() + " steps");
		this.stepTimer.startStep(def.getStep(currentStepIndex), configManager);
	}

	public List<String> getRequiredTemplates() {
		Step current = getCurrentStep();
		Step next = getNextStep();
		List<String> result = new ArrayList<>();


		System.out.println("ActiveSequence.getRequiredTemplates: currentStepIndex=" + currentStepIndex);

		if (current != null) {
			List<String> currentTemplates = current.getDetectableTokens(configManager);
			result.addAll(currentTemplates);

			System.out.println("  Current step templates: " + currentTemplates);
		}

		if (next != null) {
			List<String> nextTemplates = next.getDetectableTokens(configManager);
			result.addAll(nextTemplates);

			System.out.println("  Next step templates: " + nextTemplates);
		}


		System.out.println("  Total required templates: " + result);
		return result;
	}

	public void processDetections(List<DetectionResult> results) {

		System.out.println("ActiveSequence.processDetections: Received " + results.size() + " results");

		lastDetections.clear();
		for (DetectionResult r : results) {
			lastDetections.put(r.templateName, r);

			System.out.println("  Stored detection: " + r.templateName + " found=" + r.found);
		}


		System.out.println("  Checking if step is satisfied...");
		if (stepTimer.isStepSatisfied(lastDetections)) {

			System.out.println("  Step satisfied! Advancing...");
			advanceStep();
		} else {

			System.out.println("  Step not yet satisfied");
		}
	}

	public List<DetectionResult> getCurrentAbilities() {
		Step step = getCurrentStep();
		if (step == null) {

			System.out.println("ActiveSequence.getCurrentAbilities: No current step");
			return List.of();
		}

		List<DetectionResult> current = step.flattenDetections(lastDetections);

		System.out.println("ActiveSequence.getCurrentAbilities: " + current.size() + " abilities");

		for (DetectionResult result : current) {
			System.out.println("  Current ability: " + result.templateName + " found=" + result.found);
		}

		return current;
	}

	public List<DetectionResult> getNextAbilities() {
		Step step = getNextStep();
		if (step == null) {

			System.out.println("ActiveSequence.getNextAbilities: No next step");
			return List.of();
		}

		List<DetectionResult> next = step.flattenDetections(lastDetections);

		System.out.println("ActiveSequence.getNextAbilities: " + next.size() + " abilities");

		for (DetectionResult result : next) {
			System.out.println("  Next ability: " + result.templateName + " found=" + result.found);
		}

		return next;
	}

	private Step getCurrentStep() {

		if (currentStepIndex >= definition.size()) {
			System.out.println("getCurrentStep: Index " + currentStepIndex + " >= " + definition.size());
			return null;
		}
		return definition.getStep(currentStepIndex);
	}

	private Step getNextStep() {

		if (currentStepIndex + 1 >= definition.size()) {
			System.out.println("getNextStep: No next step (currentIndex=" + currentStepIndex + ")");
			return null;
		}
		return definition.getStep(currentStepIndex + 1);
	}

	private void advanceStep() {
		if (currentStepIndex >= definition.size() - 1) {

			System.out.println("advanceStep: Already at last step");
			return;
		}

		currentStepIndex++;

		System.out.println("advanceStep: Advanced to step " + currentStepIndex);

		Step step = getCurrentStep();
		stepTimer.startStep(step, configManager);
	}

	public void reset() {

		System.out.println("ActiveSequence.reset: Resetting to step 0");
		currentStepIndex = 0;
		stepTimer.reset();
	}
}