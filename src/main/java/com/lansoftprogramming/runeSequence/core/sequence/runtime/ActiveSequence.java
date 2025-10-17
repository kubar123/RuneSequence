package com.lansoftprogramming.runeSequence.core.sequence.runtime;

import com.lansoftprogramming.runeSequence.core.sequence.model.Alternative;
import com.lansoftprogramming.runeSequence.core.sequence.model.SequenceDefinition;
import com.lansoftprogramming.runeSequence.core.sequence.model.Step;
import com.lansoftprogramming.runeSequence.core.sequence.model.Term;
import com.lansoftprogramming.runeSequence.infrastructure.config.AbilityConfig;
import com.lansoftprogramming.runeSequence.core.detection.DetectionResult;
import com.lansoftprogramming.runeSequence.application.SequenceController;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class ActiveSequence implements SequenceController.StateChangeListener{

	private final SequenceDefinition definition;
	private final AbilityConfig abilityConfig;

	private int currentStepIndex = 0;
	public final StepTimer stepTimer;

	private Map<String, DetectionResult> lastDetections = new HashMap<>();

	@Override
	public void onStateChanged(SequenceController.State oldState, SequenceController.State newState) {
		if (newState == SequenceController.State.RUNNING) {
			stepTimer.resume();
		} else {
			stepTimer.pause();
		}
	}

	public ActiveSequence(SequenceDefinition def, AbilityConfig abilityConfig) {
		this.definition = def;
		this.abilityConfig = abilityConfig;
		this.stepTimer = new StepTimer();

		System.out.println("ActiveSequence: Created with " + def.getSteps().size() + " steps");
		this.stepTimer.startStep(def.getStep(currentStepIndex), abilityConfig);
	}


	/**
	 * Return a map of required template names -> whether they belong to an OR term (alternative).
	 * This allows callers to know which detections should be treated as alternatives.
	 */
	public java.util.Map<String, Boolean> getRequiredTemplateFlags() {
		java.util.Map<String, Boolean> out = new java.util.HashMap<>();

		Step current = getCurrentStep();
		Step next = getNextStep();

		if (current != null) {
			addFlagsFromStep(current, out);
		}
		if (next != null) {
			addFlagsFromStep(next, out);
		}

		System.out.println("ActiveSequence.getRequiredTemplateFlags: " + out);
		return out;
	}

	private void addFlagsFromStep(Step step, java.util.Map<String, Boolean> out) {
		for (Term t : step.getTerms()) {
			boolean termIsAlternative = t.getAlternatives().size() > 1;
			for (Alternative alt : t.getAlternatives()) {
				collectFlags(alt, termIsAlternative, out);
			}
		}
	}

	private void collectFlags(Alternative alt, boolean parentTermIsAlternative, java.util.Map<String, Boolean> out) {
		if (alt.isToken()) {
			String tokenName = alt.getToken();
			// Only put if absent to prefer first occurrence's alternative semantics
			out.putIfAbsent(tokenName, parentTermIsAlternative);
			System.out.println("ActiveSequence.collectFlags: token=" + tokenName + " isAlternative=" + parentTermIsAlternative);
		} else {
			for (Step step : alt.getSubgroup().getSteps()) {
				addFlagsFromStep(step, out);
			}
		}
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
		stepTimer.startStep(step, abilityConfig);
	}

	public void reset() {

		System.out.println("ActiveSequence.reset: Resetting to step 0");
		currentStepIndex = 0;
		stepTimer.reset();
	}
}