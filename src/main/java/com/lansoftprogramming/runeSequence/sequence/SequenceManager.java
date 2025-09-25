package com.lansoftprogramming.runeSequence.sequence;

import com.lansoftprogramming.runeSequence.config.AbilityConfig;
import com.lansoftprogramming.runeSequence.detection.DetectionResult;

import java.util.List;
import java.util.Map;
import java.util.Objects;

public class SequenceManager {

	private final AbilityConfig abilityConfig;
	private final Map<String, SequenceDefinition> namedSequences;
	private ActiveSequence activeSequence;

	public SequenceManager(Map<String, SequenceDefinition> namedSequences, AbilityConfig abilityConfig) {
		this.abilityConfig = Objects.requireNonNull(abilityConfig);
		this.namedSequences = Objects.requireNonNull(namedSequences);
	}

	// -------------------------
	// Public API
	// -------------------------
	public synchronized List<String> getRequiredTemplates() {
		if (activeSequence == null) {

			System.out.println("SequenceManager: No active sequence");
			return List.of();
		}
		List<String> required = activeSequence.getRequiredTemplates();

		System.out.println("SequenceManager.getRequiredTemplates: " + required);
		return required;
	}

	/**
	 * Return required templates mapped to whether they are part of an OR (alternative) term.
	 * Key = template name, Value = true if the template belongs to an OR term (i.e. alternatives), false otherwise.
	 */
	public synchronized java.util.Map<String, Boolean> getRequiredTemplateFlags() {
		if (activeSequence == null) {
			System.out.println("SequenceManager: No active sequence (flags)");
			return java.util.Map.of();
		}
		java.util.Map<String, Boolean> flags = activeSequence.getRequiredTemplateFlags();
		System.out.println("SequenceManager.getRequiredTemplateFlags: " + flags);
		return flags;
	}

	public synchronized void processDetection(List<DetectionResult> results) {

		System.out.println("SequenceManager.processDetection called with " + results.size() + " results");

		if (activeSequence != null) {
			// Log each detection result
			for (DetectionResult result : results) {
				System.out.println("  Detection: " + result.templateName +
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

	public synchronized void addNamedSequence(String name, SequenceDefinition def) {

		System.out.println("SequenceManager: Adding named sequence: " + name);
		namedSequences.put(name, def);
	}

	public synchronized boolean activateSequence(String name) {

		System.out.println("SequenceManager: Activating sequence: " + name);

		SequenceDefinition def = namedSequences.get(name);
		if (def == null) {

			System.out.println("SequenceManager: Sequence not found: " + name);

			System.out.println("Available sequences: " + namedSequences.keySet());
			return false;
		}

		this.activeSequence = new ActiveSequence(def, abilityConfig);

		System.out.println("SequenceManager: Sequence activated successfully");
		return true;
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