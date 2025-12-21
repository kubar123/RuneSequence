package com.lansoftprogramming.runeSequence.core.sequence.runtime;

import com.lansoftprogramming.runeSequence.application.SequenceController;
import com.lansoftprogramming.runeSequence.core.detection.DetectionResult;
import com.lansoftprogramming.runeSequence.core.sequence.model.*;
import com.lansoftprogramming.runeSequence.core.sequence.runtime.modification.*;
import com.lansoftprogramming.runeSequence.infrastructure.config.AbilityConfig;

import java.util.*;

public class ActiveSequence implements SequenceController.StateChangeListener{

	private final SequenceDefinition definition;
	private final AbilityConfig abilityConfig;
	private final List<List<AbilityInstance>> stepInstances;
	private final Map<String, AbilityInstance> instancesById = new HashMap<>();
	private boolean complete = false;

	private int currentStepIndex = 0;
	public final StepTimer stepTimer;
	private final AbilityModificationEngine modificationEngine;

	private final Map<String, DetectionResult> lastDetections = new HashMap<>();
	private final Map<String, Boolean> previousFoundByInstanceId = new HashMap<>();

	@Override
	public void onStateChanged(SequenceController.State oldState, SequenceController.State newState) {
		if (newState == SequenceController.State.RUNNING) {
			long nowMs = System.currentTimeMillis();
			stepTimer.resume();
			modificationEngine.onEvent(buildContext(nowMs), new SequenceEvent.SequenceResumed());
			applyTimingDirectives(nowMs);
		} else {
			long nowMs = System.currentTimeMillis();
			modificationEngine.onEvent(buildContext(nowMs), new SequenceEvent.SequencePaused());
			applyTimingDirectives(nowMs);
			stepTimer.pause();
		}
	}

	public ActiveSequence(SequenceDefinition def, AbilityConfig abilityConfig) {
		this(def, abilityConfig, AbilityModificationEngine.empty());
	}

	public ActiveSequence(SequenceDefinition def, AbilityConfig abilityConfig, AbilityModificationEngine modificationEngine) {
		this.definition = def;
		this.abilityConfig = abilityConfig;
		this.stepInstances = indexInstances(def, abilityConfig);
		this.stepTimer = new StepTimer();
		this.modificationEngine = modificationEngine != null ? modificationEngine : AbilityModificationEngine.empty();

		System.out.println("ActiveSequence: Created with " + def.getSteps().size() + " steps");
		logStepInstances();
		long nowMs = System.currentTimeMillis();
		startCurrentStepTiming(nowMs, false);
		SequenceRuntimeContext context = buildContext(nowMs);
		this.modificationEngine.onEvent(context, new SequenceEvent.SequenceInitialized());
		this.modificationEngine.onEvent(context, new SequenceEvent.StepStarted(currentStepIndex));
		applyTimingDirectives(nowMs);
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

	public List<DetectionRequirement> getDetectionRequirementsForStep(int stepIndex) {
		Map<String, DetectionRequirement> requirements = new LinkedHashMap<>();
		addRequirementsForStep(stepIndex, requirements);
		List<DetectionRequirement> out = new ArrayList<>(requirements.values());
		System.out.println("ActiveSequence.getDetectionRequirementsForStep[" + stepIndex + "]: " + out);
		return out;
	}

	/**
	 * Allows external (non-template) signals (e.g. cooldown darken / latch-like events)
	 * to feed the runtime modification engine.
	 */
	public void onAbilityUsed(String abilityKey, String instanceId, StepPosition position, long nowMs) {
		if (complete) {
			return;
		}
		SequenceRuntimeContext context = buildContext(nowMs);
		modificationEngine.onEvent(context, new SequenceEvent.AbilityUsed(abilityKey, instanceId, position));
		applyTimingDirectives(nowMs);
	}

	public void processDetections(List<DetectionResult> results) {

		if (complete) {
			return; // Finished rotations ignore further detections until reset
		}

		long nowMs = System.currentTimeMillis();
		SequenceRuntimeContext context = buildContext(nowMs);
		modificationEngine.onEvent(context, new SequenceEvent.Heartbeat());

		System.out.println("ActiveSequence.processDetections: Received " + results.size() + " results");

		lastDetections.clear();
		Set<String> currentInstanceIds = getInstanceIdsForStep(currentStepIndex);
		Set<String> nextInstanceIds = getInstanceIdsForStep(currentStepIndex + 1);
		for (DetectionResult r : results) {
			lastDetections.put(r.templateName, r);

			String abilityKey = getAbilityKeyForInstance(r.templateName);
			System.out.println("  Stored detection: " + r.templateName +
					(abilityKey != null ? " (" + abilityKey + ")" : "") +
					" found=" + r.found);

			boolean previouslyFound = previousFoundByInstanceId.getOrDefault(r.templateName, false);
			if (r.found && !previouslyFound) {
				StepPosition position = resolvePosition(r.templateName, currentInstanceIds, nextInstanceIds);
				String resolvedKey = abilityKey != null ? abilityKey : fallbackAbilityKey(r.templateName);
				modificationEngine.onEvent(context, new SequenceEvent.AbilityDetected(resolvedKey, r.templateName, position));
			}
			previousFoundByInstanceId.put(r.templateName, r.found);
		}

		applyTimingDirectives(nowMs);

		System.out.println("  Checking if step is satisfied...");
		if (stepTimer.isStepSatisfied(lastDetections)) {

			System.out.println("  Step satisfied! Advancing...");
			if (isOnLastStep()) {
				complete = true;
				System.out.println("  Sequence complete");
			} else {
				advanceStep();
			}
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

		int fromStepIndex = currentStepIndex;
		currentStepIndex++;

		System.out.println("advanceStep: Advanced to step " + currentStepIndex);

		long nowMs = System.currentTimeMillis();
		startCurrentStepTiming(nowMs, false);
		SequenceRuntimeContext context = buildContext(nowMs);
		modificationEngine.onEvent(context, new SequenceEvent.StepAdvanced(fromStepIndex, currentStepIndex));
		modificationEngine.onEvent(context, new SequenceEvent.StepStarted(currentStepIndex));
		applyTimingDirectives(nowMs);
	}

	public void reset() {

		System.out.println("ActiveSequence.reset: Resetting to step 0");
		currentStepIndex = 0;
		stepTimer.reset();
		modificationEngine.reset();
		// Restart baseline timing so future runs resume properly
		if (!definition.getSteps().isEmpty()) {
			long nowMs = System.currentTimeMillis();
			startCurrentStepTiming(nowMs, false);
			SequenceRuntimeContext context = buildContext(nowMs);
			modificationEngine.onEvent(context, new SequenceEvent.SequenceReset());
			modificationEngine.onEvent(context, new SequenceEvent.StepStarted(currentStepIndex));
			applyTimingDirectives(nowMs);
		}
		lastDetections.clear();
		previousFoundByInstanceId.clear();
		complete = false;
	}

	public String getAbilityKeyForInstance(String instanceId) {
		AbilityInstance instance = instancesById.get(instanceId);
		return instance != null ? instance.abilityKey : null;
	}

	public int getCurrentStepIndex() {
		return currentStepIndex;
	}

	public int getStepCount() {
		return stepInstances.size();
	}

	/**
	 * Directly sets the current step index for testing and preview scenarios.
	 * Resets timers and clears cached detections to mirror a fresh step start.
	 */
	public void forceStepIndex(int stepIndex) {
		if (stepInstances.isEmpty()) {
			currentStepIndex = 0;
			complete = true;
			stepTimer.reset();
			lastDetections.clear();
			previousFoundByInstanceId.clear();
			modificationEngine.reset();
			return;
		}

		int normalizedIndex = Math.max(0, stepIndex);
		if (normalizedIndex >= stepInstances.size()) {
			currentStepIndex = stepInstances.size();
			complete = true;
			stepTimer.reset();
			lastDetections.clear();
			previousFoundByInstanceId.clear();
			modificationEngine.reset();
			return;
		}

		currentStepIndex = normalizedIndex;
		complete = false;
		stepTimer.reset();
		modificationEngine.reset();
		long nowMs = System.currentTimeMillis();
		startCurrentStepTiming(nowMs, false);
		lastDetections.clear();
		previousFoundByInstanceId.clear();
		SequenceRuntimeContext context = buildContext(nowMs);
		modificationEngine.onEvent(context, new SequenceEvent.StepStarted(currentStepIndex));
		applyTimingDirectives(nowMs);
	}

	public List<String> getAbilityKeysForStep(int stepIndex) {
		if (stepIndex < 0 || stepIndex >= stepInstances.size()) {
			return List.of();
		}
		List<AbilityInstance> instances = stepInstances.get(stepIndex);
		LinkedHashSet<String> keys = new LinkedHashSet<>();
		for (AbilityInstance instance : instances) {
			keys.add(instance.abilityKey);
		}
		return List.copyOf(keys);
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
					new DetectionRequirement(instance.instanceId, instance.abilityKey, instance.isAlternative, instance.effectiveAbilityConfig));
		}
	}

	private List<List<AbilityInstance>> indexInstances(SequenceDefinition def, AbilityConfig abilityConfig) {
		List<List<AbilityInstance>> perStep = new ArrayList<>();
		Map<String, Integer> occurrenceCounters = new HashMap<>();
		List<Step> steps = def.getSteps();
		for (Step step : steps) {
			List<AbilityInstance> collected = new ArrayList<>();
			collectInstancesFromStep(step, occurrenceCounters, collected, false, abilityConfig);
			perStep.add(List.copyOf(collected));
		}
		return List.copyOf(perStep);
	}

	private void collectInstancesFromStep(Step step,
	                                     Map<String, Integer> occurrenceCounters,
	                                     List<AbilityInstance> collector,
	                                      boolean inheritedAlternative,
	                                      AbilityConfig abilityConfig) {
		for (Term term : step.getTerms()) {
			boolean termIsAlternative = inheritedAlternative || term.getAlternatives().size() > 1;
			for (Alternative alt : term.getAlternatives()) {
				collectInstancesFromAlternative(alt, occurrenceCounters, collector, termIsAlternative, abilityConfig);
			}
		}
	}

	private void collectInstancesFromAlternative(Alternative alt,
	                                             Map<String, Integer> occurrenceCounters,
	                                             List<AbilityInstance> collector,
	                                             boolean parentTermIsAlternative,
	                                             AbilityConfig abilityConfig) {
		if (alt.isToken()) {
			String abilityKey = alt.getToken();
			int occurrenceIndex = occurrenceCounters.getOrDefault(abilityKey, 0);
			String instanceId = abilityKey + "#" + occurrenceIndex;
			occurrenceCounters.put(abilityKey, occurrenceIndex + 1);
			EffectiveAbilityConfig effectiveConfig = buildEffectiveConfig(alt, abilityKey, abilityConfig);
			AbilityInstance instance = new AbilityInstance(instanceId, abilityKey, parentTermIsAlternative, effectiveConfig);
			collector.add(instance);
			instancesById.put(instanceId, instance);
			System.out.println("ActiveSequence.collectInstances: instanceId=" + instanceId +
					" abilityKey=" + abilityKey + " isAlternative=" + parentTermIsAlternative);
		} else {
			for (Step step : alt.getSubgroup().getSteps()) {
				collectInstancesFromStep(step, occurrenceCounters, collector, parentTermIsAlternative, abilityConfig);
			}
		}
	}

	private EffectiveAbilityConfig buildEffectiveConfig(Alternative alt,
	                                                    String abilityKey,
	                                                    AbilityConfig abilityConfig) {
		if (abilityConfig == null) {
			return null;
		}
		AbilityConfig.AbilityData baseAbility = abilityConfig.getAbility(abilityKey);
		if (baseAbility == null) {
			return null;
		}
		return EffectiveAbilityConfig.from(abilityKey, baseAbility, alt.getAbilitySettingsOverrides());
	}

	private static final class AbilityInstance {
		private final String instanceId;
		private final String abilityKey;
		private final boolean isAlternative;
		private final EffectiveAbilityConfig effectiveAbilityConfig;

		private AbilityInstance(String instanceId, String abilityKey, boolean isAlternative, EffectiveAbilityConfig effectiveAbilityConfig) {
			this.instanceId = instanceId;
			this.abilityKey = abilityKey;
			this.isAlternative = isAlternative;
			this.effectiveAbilityConfig = effectiveAbilityConfig;
		}

		@Override
		public String toString() {
			return instanceId + "[" + (isAlternative ? "OR" : "AND") + "]";
		}
	}

	public record DetectionRequirement(String instanceId, String abilityKey, boolean isAlternative,
	                                   EffectiveAbilityConfig effectiveAbilityConfig) {
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

	public boolean isComplete() {
		return complete;
	}

	/**
	 * Called when the visual latch fires. We assume the first ability was just used,
	 * so advance to the next step and restart timing from the latch moment.
	 * @return true if the sequence became complete as a result of this jump
	 */
	public boolean onLatchStart(long latchTimeMs) {
		if (complete) {
			return true;
		}

		if (isOnLastStep()) {
			complete = true;
			return true;
		}

		currentStepIndex++;
		startCurrentStepTiming(latchTimeMs, true);
		SequenceRuntimeContext context = buildContext(latchTimeMs);
		modificationEngine.onEvent(context, new SequenceEvent.LatchStarted(latchTimeMs));
		modificationEngine.onEvent(context, new SequenceEvent.StepStarted(currentStepIndex));
		applyTimingDirectives(latchTimeMs);
		lastDetections.clear();
		previousFoundByInstanceId.clear();
		return false;
	}

	private boolean isOnLastStep() {
		return currentStepIndex >= definition.size() - 1;
	}

	private void logStepInstances() {
		for (int i = 0; i < stepInstances.size(); i++) {
			System.out.println("ActiveSequence.stepInstances[" + i + "]=" + stepInstances.get(i));
		}
	}

	public List<SequenceTooltip> getRuntimeTooltips() {
		long nowMs = System.currentTimeMillis();
		return modificationEngine.getRuntimeTooltips(buildContext(nowMs));
	}

	private SequenceRuntimeContext buildContext(long nowMs) {
		List<String> currentKeys = getAbilityKeysForStep(currentStepIndex);
		List<String> nextKeys = getAbilityKeysForStep(currentStepIndex + 1);
		return new SequenceRuntimeContext(abilityConfig, currentStepIndex, currentKeys, nextKeys, stepTimer, nowMs);
	}

	private void startCurrentStepTiming(long startTimeMs, boolean overrideStartTime) {
		if (currentStepIndex < 0 || currentStepIndex >= definition.size()) {
			return;
		}
		Step step = definition.getStep(currentStepIndex);
		SequenceRuntimeContext context = buildContext(startTimeMs);
		stepTimer.startStep(step, abilityConfig, profile -> modificationEngine.applyTimingOverrides(context, profile));
		if (overrideStartTime) {
			stepTimer.restartAt(startTimeMs);
		}
	}

	private void applyTimingDirectives(long nowMs) {
		List<TimingDirective> directives = modificationEngine.drainTimingDirectives();
		for (TimingDirective directive : directives) {
			if (directive instanceof TimingDirective.RestartStepAt restart) {
				stepTimer.restartAt(restart.startTimeMs());
			} else if (directive instanceof TimingDirective.SetStepDurationMs duration) {
				stepTimer.setStepDurationMs(duration.durationMs());
			} else if (directive instanceof TimingDirective.ForceStepSatisfiedAt force) {
				stepTimer.forceSatisfiedAt(force.nowMs());
			} else if (directive instanceof TimingDirective.PauseStepTimer) {
				stepTimer.pause();
			} else if (directive instanceof TimingDirective.ResumeStepTimer) {
				stepTimer.resume();
			}
		}
	}

	private Set<String> getInstanceIdsForStep(int stepIndex) {
		if (stepIndex < 0 || stepIndex >= stepInstances.size()) {
			return Set.of();
		}
		List<AbilityInstance> instances = stepInstances.get(stepIndex);
		if (instances == null || instances.isEmpty()) {
			return Set.of();
		}
		Set<String> ids = new HashSet<>(instances.size());
		for (AbilityInstance instance : instances) {
			ids.add(instance.instanceId);
		}
		return ids;
	}

	private StepPosition resolvePosition(String instanceId, Set<String> currentInstanceIds, Set<String> nextInstanceIds) {
		if (instanceId == null) {
			return StepPosition.OTHER;
		}
		if (currentInstanceIds.contains(instanceId)) {
			return StepPosition.CURRENT_STEP;
		}
		if (nextInstanceIds.contains(instanceId)) {
			return StepPosition.NEXT_STEP;
		}
		return StepPosition.OTHER;
	}

	private String fallbackAbilityKey(String instanceId) {
		if (instanceId == null) {
			return null;
		}
		int hash = instanceId.indexOf('#');
		if (hash > 0) {
			return instanceId.substring(0, hash);
		}
		return instanceId;
	}
}
