package com.lansoftprogramming.runeSequence.application;

import com.lansoftprogramming.runeSequence.core.detection.DetectionResult;
import com.lansoftprogramming.runeSequence.core.sequence.model.SequenceDefinition;
import com.lansoftprogramming.runeSequence.core.sequence.runtime.ActiveSequence;
import com.lansoftprogramming.runeSequence.infrastructure.config.AbilityConfig;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;
import java.util.Map;
import java.util.Objects;

public class SequenceManager {

	private static final Logger logger = LoggerFactory.getLogger(SequenceManager.class);

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
		logger.info("Activating sequence: {}", name);
		SequenceDefinition def = namedSequences.get(name);
		if (def == null) {
			logger.warn("Sequence not found: {}", name);
			logger.debug("Available sequences: {}", namedSequences.keySet());
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

		logger.info("Sequence '{}' activated successfully.", name);
		return true;
	}
	/**
	 * Return the per-occurrence detection requirements (current + next step).
	 */
	public synchronized List<ActiveSequence.DetectionRequirement> getDetectionRequirements() {
		if (activeSequence == null) {
			logger.debug("No active sequence when requesting detection requirements.");
			return List.of();
		}
		List<ActiveSequence.DetectionRequirement> requirements = activeSequence.getDetectionRequirements();
		logger.trace("Detection requirements: {}", requirements);
		return requirements;
	}

	public synchronized void processDetection(List<DetectionResult> results) {

		logger.debug("Processing {} detection results.", results.size());

		if (activeSequence != null) {
			// Log each detection result
			for (DetectionResult result : results) {
				String abilityKey = activeSequence.getAbilityKeyForInstance(result.templateName);
				logger.trace("Detection {}{} found={} confidence={}",
						result.templateName,
						abilityKey != null ? " (" + abilityKey + ")" : "",
						result.found,
						result.confidence);
			}

			activeSequence.processDetections(results);
		} else {
			logger.warn("No active sequence available to process detections.");
		}
	}

	public synchronized List<DetectionResult> getCurrentAbilities() {
		if (activeSequence == null) {
			logger.debug("getCurrentAbilities requested but no active sequence.");
			return List.of();
		}
		List<DetectionResult> current = activeSequence.getCurrentAbilities();
		logger.trace("Current abilities count: {}", current.size());
		return current;
	}

	public synchronized List<DetectionResult> getNextAbilities() {
		if (activeSequence == null) {
			logger.debug("getNextAbilities requested but no active sequence.");
			return List.of();
		}
		List<DetectionResult> next = activeSequence.getNextAbilities();
		logger.trace("Next abilities count: {}", next.size());
		return next;
	}

	public synchronized List<String> getActiveSequenceAbilityKeys() {
		if (activeSequence == null) {
			logger.debug("getActiveSequenceAbilityKeys requested but no active sequence.");
			return List.of();
		}
		List<String> keys = activeSequence.getAllAbilityKeys();
		logger.trace("Active sequence ability keys count: {}", keys.size());
		return keys;
	}

	public synchronized void addNamedSequence(String name, SequenceDefinition def) {
		logger.info("Adding named sequence: {}", name);
		namedSequences.put(name, def);
	}


	public synchronized void resetActiveSequence() {
		logger.info("Resetting active sequence.");
		if (activeSequence != null) activeSequence.reset();
	}

	public synchronized boolean hasActiveSequence() {
		boolean hasActive = activeSequence != null;
		logger.debug("hasActiveSequence -> {}", hasActive);
		return hasActive;
	}
}
