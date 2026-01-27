package com.lansoftprogramming.runeSequence.core.sequence.runtime;

import com.lansoftprogramming.runeSequence.application.SequenceController;
import com.lansoftprogramming.runeSequence.core.detection.DetectionResult;
import com.lansoftprogramming.runeSequence.core.sequence.model.*;
import com.lansoftprogramming.runeSequence.core.sequence.modifier.AbilityModifierEngine;
import com.lansoftprogramming.runeSequence.infrastructure.config.AbilityConfig;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.*;

public class ActiveSequence implements SequenceController.StateChangeListener{

	private static final Logger logger = LoggerFactory.getLogger(ActiveSequence.class);

	private final SequenceDefinition definition;
	private final AbilityConfig abilityConfig;
	private final List<List<AbilityInstance>> stepInstances;
	private final List<ChannelInfo> channelInfoByStep;
	private final Map<String, AbilityInstance> instancesById = new HashMap<>();
	private boolean complete = false;

	private int currentStepIndex = 0;
	public final StepTimer stepTimer;
	private boolean playbackStarted = false;

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
		this.stepInstances = indexInstances(def, abilityConfig);
		this.channelInfoByStep = computeChannelInfoByStep(stepInstances);
		this.stepTimer = new StepTimer();

		if (logger.isDebugEnabled()) {
			logger.debug("ActiveSequence: Created with {} steps", def.getSteps().size());
			logStepInstances();
		}
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
		if (logger.isDebugEnabled()) {
			logger.debug("ActiveSequence.getDetectionRequirements: {}", out);
		}
		return out;
	}

	public void processDetections(List<DetectionResult> results) {

		if (complete) {
			return; // Finished rotations ignore further detections until reset
		}

		if (logger.isDebugEnabled()) {
			logger.debug("ActiveSequence.processDetections: Received {} results", results.size());
		}

		lastDetections.clear();
		for (DetectionResult r : results) {
			lastDetections.put(r.templateName, r);

			String abilityKey = getAbilityKeyForInstance(r.templateName);
			if (logger.isDebugEnabled()) {
				logger.debug("Stored detection: {}{} found={}",
						r.templateName,
						abilityKey != null ? " (" + abilityKey + ")" : "",
						r.found);
			}
		}


		if (logger.isDebugEnabled()) {
			logger.debug("Checking if step is satisfied...");
		}
		if (stepTimer.isStepSatisfied(lastDetections)) {

			if (logger.isDebugEnabled()) {
				logger.debug("Step satisfied! Advancing...");
			}
			if (!playbackStarted) {
				// Legacy behavior: advance based on the current step's own timer.
				if (isOnLastStep()) {
					complete = true;
					if (logger.isDebugEnabled()) {
						logger.debug("Sequence complete");
					}
				} else {
					advanceStep();
				}
				return;
			}

			// Playback mode: the current step represents the next ability to press, and the timer represents the
			// delay from the ability that was just used (previous step). When the timer satisfies, we assume the
			// current step is pressed immediately and advance to the next, starting a new delay window based on
			// the step we just advanced from.
			if (isOnLastStep()) {
				complete = true;
				if (logger.isDebugEnabled()) {
					logger.debug("Sequence complete");
				}
				return;
			}

			Step assumedUsed = getCurrentStep();
			if (assumedUsed == null) {
				complete = true;
				return;
			}

			currentStepIndex++;
			stepTimer.startStep(assumedUsed, abilityConfig);
			lastDetections.clear();
			if (logger.isDebugEnabled()) {
				logger.debug("Playback advanced to step {}", currentStepIndex);
			}
		} else {

			if (logger.isDebugEnabled()) {
				logger.debug("Step not yet satisfied");
			}
		}
	}

	public List<DetectionResult> getCurrentAbilities() {
		Step step = getCurrentStep();
		if (step == null) {

			if (logger.isDebugEnabled()) {
				logger.debug("ActiveSequence.getCurrentAbilities: No current step");
			}
			return List.of();
		}

		List<DetectionResult> current = buildDetectionsForStep(currentStepIndex);

		if (logger.isDebugEnabled()) {
			logger.debug("ActiveSequence.getCurrentAbilities: {} abilities", current.size());
		}

		for (DetectionResult result : current) {
			String abilityKey = getAbilityKeyForInstance(result.templateName);
			if (logger.isDebugEnabled()) {
				logger.debug("Current ability: {}{} found={}",
						result.templateName,
						abilityKey != null ? " (" + abilityKey + ")" : "",
						result.found);
			}
		}

		return current;
	}

	public List<DetectionResult> getNextAbilities() {
		Step step = getNextStep();
		if (step == null) {
			if (logger.isDebugEnabled()) {
				logger.debug("ActiveSequence.getNextAbilities: No next step");
			}
			return List.of();
		}

		List<DetectionResult> next = buildDetectionsForStep(currentStepIndex + 1);
		if (logger.isDebugEnabled()) {
			logger.debug("ActiveSequence.getNextAbilities: {} abilities", next.size());
		}

		return next;
	}

	private Step getCurrentStep() {

		if (currentStepIndex >= definition.size()) {
			if (logger.isDebugEnabled()) {
				logger.debug("getCurrentStep: Index {} >= {}", currentStepIndex, definition.size());
			}
			return null;
		}
		return definition.getStep(currentStepIndex);
	}

	private Step getNextStep() {

		if (currentStepIndex + 1 >= definition.size()) {
			if (logger.isDebugEnabled()) {
				logger.debug("getNextStep: No next step (currentIndex={})", currentStepIndex);
			}
			return null;
		}
		return definition.getStep(currentStepIndex + 1);
	}

	private void advanceStep() {
		if (currentStepIndex >= definition.size() - 1) {

			if (logger.isDebugEnabled()) {
				logger.debug("advanceStep: Already at last step");
			}
			return;
		}

		currentStepIndex++;

		if (logger.isDebugEnabled()) {
			logger.debug("advanceStep: Advanced to step {}", currentStepIndex);
		}

		Step step = getCurrentStep();
		stepTimer.startStep(step, abilityConfig);
	}

	public void reset() {

		if (logger.isDebugEnabled()) {
			logger.debug("ActiveSequence.reset: Resetting to step 0");
		}
		currentStepIndex = 0;
		playbackStarted = false;
		stepTimer.reset();
		// Restart baseline timing so future runs resume properly
		if (!definition.getSteps().isEmpty()) {
			stepTimer.startStep(definition.getStep(currentStepIndex), abilityConfig);
		}
		lastDetections.clear();
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

	public ChannelWaitStatus getChannelWaitStatus() {
		if (complete || stepTimer.isPaused()) {
			return null;
		}
		int timingStepIndex = playbackStarted ? currentStepIndex - 1 : currentStepIndex;
		if (timingStepIndex < 0 || timingStepIndex >= channelInfoByStep.size()) {
			return null;
		}
		ChannelInfo info = channelInfoByStep.get(timingStepIndex);
		if (info == null || info.durationMs <= 0 || info.abilityKey == null) {
			return null;
		}
		long elapsedMs = stepTimer.getEffectiveElapsedMs();
		long remainingMs = info.durationMs - elapsedMs;
		if (remainingMs <= 0) {
			return null;
		}
		return new ChannelWaitStatus(info.abilityKey, remainingMs);
	}

	/**
	 * Directly sets the current step index for testing and preview scenarios.
	 * Resets timers and clears cached detections to mirror a fresh step start.
	 */
	public void forceStepIndex(int stepIndex) {
		if (stepInstances.isEmpty()) {
			currentStepIndex = 0;
			complete = true;
			playbackStarted = false;
			stepTimer.reset();
			lastDetections.clear();
			return;
		}

		int normalizedIndex = Math.max(0, stepIndex);
		if (normalizedIndex >= stepInstances.size()) {
			currentStepIndex = stepInstances.size();
			complete = true;
			playbackStarted = false;
			stepTimer.reset();
			lastDetections.clear();
			return;
		}

		currentStepIndex = normalizedIndex;
		complete = false;
		playbackStarted = false;
		stepTimer.reset();
		stepTimer.startStep(definition.getStep(currentStepIndex), abilityConfig);
		lastDetections.clear();
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
			if (logger.isDebugEnabled()) {
				logger.debug("ActiveSequence.collectInstances: instanceId={} abilityKey={} isAlternative={}",
						instanceId, abilityKey, parentTermIsAlternative);
			}
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
		return EffectiveAbilityConfig.from(abilityKey, baseAbility, AbilityModifierEngine.effectiveOverrides(alt));
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
	 * Called when the visual latch fires. We assume the current ability was just used,
	 * so we restart timing for the current step from the latch moment.
	 * <p>
	 * This preserves the initial GCD/cast delay between the first and second abilities and keeps
	 * UI step-promotion animations aligned with actual step transitions.
	 * @return true if the sequence became complete as a result of this jump
	 */
	public boolean onLatchStart(long latchTimeMs) {
		if (complete) {
			return true;
		}

		Step currentStep = getCurrentStep();
		if (currentStep == null) {
			complete = true;
			return true;
		}

		if (isOnLastStep()) {
			complete = true;
			return true;
		}

		playbackStarted = true;

		// Latch means the current step was just used, so we immediately advance to show the next ability.
		// The timer window tracks the just-used step's GCD/cast/cooldown so we know when the next ability can be used.
		stepTimer.startStep(currentStep, abilityConfig);
		stepTimer.restartAt(latchTimeMs);
		currentStepIndex++;
		lastDetections.clear();
		return false;
	}

	private boolean isOnLastStep() {
		return currentStepIndex >= definition.size() - 1;
	}

	private void logStepInstances() {
		if (!logger.isDebugEnabled()) {
			return;
		}
		for (int i = 0; i < stepInstances.size(); i++) {
			logger.debug("ActiveSequence.stepInstances[{}]={}", i, stepInstances.get(i));
		}
	}

	private List<ChannelInfo> computeChannelInfoByStep(List<List<AbilityInstance>> stepInstances) {
		if (stepInstances == null || stepInstances.isEmpty()) {
			return List.of();
		}

		List<ChannelInfo> out = new ArrayList<>(stepInstances.size());
		for (List<AbilityInstance> instances : stepInstances) {
			String bestAbilityKey = null;
			short bestCastTicks = 0;
			if (instances != null) {
				for (AbilityInstance instance : instances) {
					if (instance == null || instance.effectiveAbilityConfig == null) {
						continue;
					}
					short castTicks = instance.effectiveAbilityConfig.getCastDuration();
					if (castTicks > bestCastTicks) {
						bestCastTicks = castTicks;
						bestAbilityKey = instance.abilityKey;
					}
				}
			}
			long durationMs = bestCastTicks > 0 ? bestCastTicks * StepTimer.TICK_MS : 0L;
			out.add(new ChannelInfo(bestAbilityKey, durationMs));
		}

		return List.copyOf(out);
	}

	private record ChannelInfo(String abilityKey, long durationMs) {
	}

	public record ChannelWaitStatus(String abilityKey, long remainingMs) {
	}
}
