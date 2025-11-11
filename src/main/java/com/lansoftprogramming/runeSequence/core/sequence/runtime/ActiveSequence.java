package com.lansoftprogramming.runeSequence.core.sequence.runtime;

import com.lansoftprogramming.runeSequence.application.SequenceController;
import com.lansoftprogramming.runeSequence.core.detection.DetectionResult;
import com.lansoftprogramming.runeSequence.core.sequence.model.Alternative;
import com.lansoftprogramming.runeSequence.core.sequence.model.SequenceDefinition;
import com.lansoftprogramming.runeSequence.core.sequence.model.Step;
import com.lansoftprogramming.runeSequence.core.sequence.model.Term;
import com.lansoftprogramming.runeSequence.infrastructure.config.AbilityConfig;

import java.util.*;

public class ActiveSequence implements SequenceController.StateChangeListener{

	private final SequenceDefinition definition;
	private final AbilityConfig abilityConfig;
	private final List<List<AbilityInstance>> stepInstances;
	private final Map<String, AbilityInstance> instancesById = new HashMap<>();

	private int currentStepIndex = 0;
	public final StepTimer stepTimer;

	private final Map<String, DetectionResult> lastDetections = new HashMap<>();

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
		this.stepInstances = indexInstances(def);
		this.stepTimer = new StepTimer();

		System.out.println("ActiveSequence: Created with " + def.getSteps().size() + " steps");
		logStepInstances();
		this.stepTimer.startStep(def.getStep(currentStepIndex), abilityConfig);
	}


	/**
	 * Return a map of required template names -> whether they belong to an OR term (alternative).
	 * This allows callers to know which detections should be treated as alternatives.
	 */
	public List<DetectionRequirement> getDetectionRequirements() {
		Map<String, DetectionRequirement> requirements = new LinkedHashMap<>();
		addRequirementsForStep(currentStepIndex, requirements);
		addRequirementsForStep(currentStepIndex + 1, requirements);
		List<DetectionRequirement> out = new ArrayList<>(requirements.values());
		System.out.println("ActiveSequence.getDetectionRequirements: " + out);
		return out;
	}

	public void processDetections(List<DetectionResult> results) {

		System.out.println("ActiveSequence.processDetections: Received " + results.size() + " results");

		lastDetections.clear();
		for (DetectionResult r : results) {
			lastDetections.put(r.templateName, r);

			String abilityKey = getAbilityKeyForInstance(r.templateName);
			System.out.println("  Stored detection: " + r.templateName +
					(abilityKey != null ? " (" + abilityKey + ")" : "") +
					" found=" + r.found);
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

		List<DetectionResult> current = buildDetectionsForStep(currentStepIndex);

		System.out.println("ActiveSequence.getCurrentAbilities: " + current.size() + " abilities");

		for (DetectionResult result : current) {
			String abilityKey = getAbilityKeyForInstance(result.templateName);
			System.out.println("  Current ability: " + result.templateName +
					(abilityKey != null ? " (" + abilityKey + ")" : "") +
					" found=" + result.found);
		}

		return current;
	}

	public List<DetectionResult> getNextAbilities() {
		Step step = getNextStep();
		if (step == null) {
			System.out.println("ActiveSequence.getNextAbilities: No next step");
			return List.of();
		}

		List<DetectionResult> next = buildDetectionsForStep(currentStepIndex + 1);
		System.out.println("ActiveSequence.getNextAbilities: " + next.size() + " abilities");

		for (DetectionResult result : next) {
			String abilityKey = getAbilityKeyForInstance(result.templateName);
			System.out.println("  Next ability: " + result.templateName +
					(abilityKey != null ? " (" + abilityKey + ")" : "") +
					" found=" + result.found);
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
		lastDetections.clear();
	}

	public String getAbilityKeyForInstance(String instanceId) {
		AbilityInstance instance = instancesById.get(instanceId);
		return instance != null ? instance.abilityKey : null;
	}

	private List<DetectionResult> buildDetectionsForStep(int stepIndex) {
		if (stepIndex < 0 || stepIndex >= stepInstances.size()) {
			return List.of();
		}

		List<AbilityInstance> instances = stepInstances.get(stepIndex);
		List<DetectionResult> out = new ArrayList<>(instances.size());
		for (AbilityInstance instance : instances) {
			DetectionResult existing = lastDetections.get(instance.instanceId);
			if (existing != null) {
				out.add(existing);
			} else {
				out.add(DetectionResult.notFound(instance.instanceId, instance.isAlternative));
			}
		}
		return out;
	}

	private void addRequirementsForStep(int stepIndex, Map<String, DetectionRequirement> out) {
		if (stepIndex < 0 || stepIndex >= stepInstances.size()) {
			return;
		}
		for (AbilityInstance instance : stepInstances.get(stepIndex)) {
			out.putIfAbsent(instance.instanceId,
					new DetectionRequirement(instance.instanceId, instance.abilityKey, instance.isAlternative));
		}
	}

	private List<List<AbilityInstance>> indexInstances(SequenceDefinition def) {
		List<List<AbilityInstance>> perStep = new ArrayList<>();
		Map<String, Integer> occurrenceCounters = new HashMap<>();
		List<Step> steps = def.getSteps();
		for (Step step : steps) {
			List<AbilityInstance> collected = new ArrayList<>();
			collectInstancesFromStep(step, occurrenceCounters, collected, false);
			perStep.add(List.copyOf(collected));
		}
		return List.copyOf(perStep);
	}

	private void collectInstancesFromStep(Step step,
	                                     Map<String, Integer> occurrenceCounters,
	                                     List<AbilityInstance> collector,
	                                     boolean inheritedAlternative) {
		for (Term term : step.getTerms()) {
			boolean termIsAlternative = inheritedAlternative || term.getAlternatives().size() > 1;
			for (Alternative alt : term.getAlternatives()) {
				collectInstancesFromAlternative(alt, occurrenceCounters, collector, termIsAlternative);
			}
		}
	}

	private void collectInstancesFromAlternative(Alternative alt,
	                                             Map<String, Integer> occurrenceCounters,
	                                             List<AbilityInstance> collector,
	                                             boolean parentTermIsAlternative) {
		if (alt.isToken()) {
			String abilityKey = alt.getToken();
			int occurrenceIndex = occurrenceCounters.getOrDefault(abilityKey, 0);
			String instanceId = abilityKey + "#" + occurrenceIndex;
			occurrenceCounters.put(abilityKey, occurrenceIndex + 1);
			AbilityInstance instance = new AbilityInstance(instanceId, abilityKey, parentTermIsAlternative);
			collector.add(instance);
			instancesById.put(instanceId, instance);
			System.out.println("ActiveSequence.collectInstances: instanceId=" + instanceId +
					" abilityKey=" + abilityKey + " isAlternative=" + parentTermIsAlternative);
		} else {
			for (Step step : alt.getSubgroup().getSteps()) {
				collectInstancesFromStep(step, occurrenceCounters, collector, parentTermIsAlternative);
			}
		}
	}

	private static final class AbilityInstance {
		private final String instanceId;
		private final String abilityKey;
		private final boolean isAlternative;

		private AbilityInstance(String instanceId, String abilityKey, boolean isAlternative) {
			this.instanceId = instanceId;
			this.abilityKey = abilityKey;
			this.isAlternative = isAlternative;
		}

		@Override
		public String toString() {
			return instanceId + "[" + (isAlternative ? "OR" : "AND") + "]";
		}
	}

	public record DetectionRequirement(String instanceId, String abilityKey, boolean isAlternative) {
		@Override
		public String toString() {
			return instanceId + "->" + abilityKey + "[" + (isAlternative ? "OR" : "AND") + "]";
		}
	}

	public List<String> getAllAbilityKeys() {
		LinkedHashSet<String> keys = new LinkedHashSet<>();
		for (List<AbilityInstance> perStep : stepInstances) {
			for (AbilityInstance instance : perStep) {
				keys.add(instance.abilityKey);
			}
		}
		return List.copyOf(keys);
	}

	private void logStepInstances() {
		for (int i = 0; i < stepInstances.size(); i++) {
			System.out.println("ActiveSequence.stepInstances[" + i + "]=" + stepInstances.get(i));
		}
	}
}
