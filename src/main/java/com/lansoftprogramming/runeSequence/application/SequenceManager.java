package com.lansoftprogramming.runeSequence.application;

import com.lansoftprogramming.runeSequence.core.detection.DetectionResult;
import com.lansoftprogramming.runeSequence.core.sequence.model.SequenceDefinition;
import com.lansoftprogramming.runeSequence.core.sequence.runtime.ActiveSequence;
import com.lansoftprogramming.runeSequence.infrastructure.config.AbilityConfig;

import java.util.List;
import java.util.Map;
import java.util.Objects;

public class SequenceManager {

	private final AbilityConfig abilityConfig;
	private final Map<String, SequenceDefinition> namedSequences;
	private ActiveSequence activeSequence;
	private SequenceController sequenceController;

	public SequenceManager(Map<String, SequenceDefinition> namedSequences, AbilityConfig abilityConfig) {
		this.abilityConfig = Objects.requireNonNull(abilityConfig);
		this.namedSequences = Objects.requireNonNull(namedSequences);
	}
	public void setSequenceController(SequenceController sequenceController) {
		this.sequenceController = sequenceController;
	}
	// -------------------------
	// Public API
	// -------------------------

	public synchronized boolean activateSequence(String name) {
		System.out.println("SequenceManager: Activating sequence: " + name);
		SequenceDefinition def = namedSequences.get(name);
		if (def == null) {
			System.out.println("SequenceManager: Sequence not found: " + name);
			System.out.println("Available sequences: " + namedSequences.keySet());
			return false;
		}

		if (activeSequence != null && sequenceController != null) {
			sequenceController.removeStateChangeListener(activeSequence);
		}

		this.activeSequence = new ActiveSequence(def, abilityConfig);

		if (sequenceController != null) {
			sequenceController.addStateChangeListener(activeSequence);
			activeSequence.stepTimer.pause();
		}

		System.out.println("SequenceManager: Sequence activated successfully");
		return true;
	}
	/**
	 * Return the per-occurrence detection requirements (current + next step).
	 */
	public synchronized List<ActiveSequence.DetectionRequirement> getDetectionRequirements() {
		if (activeSequence == null) {
			System.out.println("SequenceManager: No active sequence (requirements)");
			return List.of();
		}
		List<ActiveSequence.DetectionRequirement> requirements = activeSequence.getDetectionRequirements();
		System.out.println("SequenceManager.getDetectionRequirements: " + requirements);
		return requirements;
	}

	public synchronized void processDetection(List<DetectionResult> results) {

		System.out.println("SequenceManager.processDetection called with " + results.size() + " results");

		if (activeSequence != null) {
			// Log each detection result
			for (DetectionResult result : results) {
				String abilityKey = activeSequence.getAbilityKeyForInstance(result.templateName);
				System.out.println("  Detection: " + result.templateName +
						(abilityKey != null ? " (" + abilityKey + ")" : "") +
						" found=" + result.found +
						" confidence=" + result.confidence);
			}

			activeSequence.processDetections(results);
		} else {
			System.out.println("SequenceManager: No active sequence to process detections");
		}
	}

	public synchronized List<DetectionResult> getCurrentAbilities() {
		if (activeSequence == null) {
			System.out.println("SequenceManager.getCurrentAbilities: No active sequence");
			return List.of();
		}
		List<DetectionResult> current = activeSequence.getCurrentAbilities();
		System.out.println("SequenceManager.getCurrentAbilities: " + current.size() + " abilities");
		return current;
	}

	public synchronized List<DetectionResult> getNextAbilities() {
		if (activeSequence == null) {
			System.out.println("SequenceManager.getNextAbilities: No active sequence");
			return List.of();
		}
		List<DetectionResult> next = activeSequence.getNextAbilities();
		System.out.println("SequenceManager.getNextAbilities: " + next.size() + " abilities");
		return next;
	}

	public synchronized List<String> getActiveSequenceAbilityKeys() {
		if (activeSequence == null) {
			System.out.println("SequenceManager.getActiveSequenceAbilityKeys: No active sequence");
			return List.of();
		}
		List<String> keys = activeSequence.getAllAbilityKeys();
		System.out.println("SequenceManager.getActiveSequenceAbilityKeys: " + keys.size() + " abilities");
		return keys;
	}

	public synchronized void addNamedSequence(String name, SequenceDefinition def) {
		System.out.println("SequenceManager: Adding named sequence: " + name);
		namedSequences.put(name, def);
	}


	public synchronized void resetActiveSequence() {
		System.out.println("SequenceManager: Resetting active sequence");
		if (activeSequence != null) activeSequence.reset();
	}

	public synchronized boolean hasActiveSequence() {
		boolean hasActive = activeSequence != null;
		System.out.println("SequenceManager.hasActiveSequence: " + hasActive);
		return hasActive;
	}
}
