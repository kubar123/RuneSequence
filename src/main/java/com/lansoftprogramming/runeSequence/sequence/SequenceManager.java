package com.lansoftprogramming.runeSequence.sequence;

import com.lansoftprogramming.runeSequence.config.ConfigManager;
import com.lansoftprogramming.runeSequence.detection.DetectionResult;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;
import java.util.Map;
import java.util.Objects;

public class SequenceManager {
	private static final Logger logger = LoggerFactory.getLogger(SequenceManager.class);
	private final ConfigManager configManager;
	private final Map<String, SequenceDefinition> namedSequences;
	private ActiveSequence activeSequence;

	public SequenceManager(ConfigManager configManager, Map<String, SequenceDefinition> namedSequences) {
		this.configManager = Objects.requireNonNull(configManager);
		this.namedSequences = Objects.requireNonNull(namedSequences);
	}

	// -------------------------
	// Public API
	// -------------------------
	public synchronized List<String> getRequiredTemplates() {
		if (activeSequence == null) {
			logger.debug("SequenceManager: No active sequence");
			return List.of();
		}
		List<String> required = activeSequence.getRequiredTemplates();
		logger.debug("SequenceManager.getRequiredTemplates: {}", required);
		return required;
	}

	public synchronized void processDetection(List<DetectionResult> results) {
		logger.debug("SequenceManager.processDetection called with {} results", results.size());

		if (activeSequence != null) {
			// Log each detection result
			for (DetectionResult result : results) {
				logger.debug("  Detection: {} found={} confidence={}", result.templateName, result.found, result.confidence);
			}

			activeSequence.processDetections(results);
		} else {
			logger.debug("SequenceManager: No active sequence to process detections");
		}
	}

	public synchronized List<DetectionResult> getCurrentAbilities() {
		if (activeSequence == null) {
			logger.debug("SequenceManager.getCurrentAbilities: No active sequence");
			return List.of();
		}
		List<DetectionResult> current = activeSequence.getCurrentAbilities();
		logger.debug("SequenceManager.getCurrentAbilities: {} abilities", current.size());
		return current;
	}

	public synchronized List<DetectionResult> getNextAbilities() {
		if (activeSequence == null) {
			logger.debug("SequenceManager.getNextAbilities: No active sequence");
			return List.of();
		}
		List<DetectionResult> next = activeSequence.getNextAbilities();
		logger.debug("SequenceManager.getNextAbilities: {} abilities", next.size());
		return next;
	}

	public synchronized void addNamedSequence(String name, SequenceDefinition def) {
		logger.debug("SequenceManager: Adding named sequence: {}", name);
		namedSequences.put(name, def);
	}

	public synchronized boolean activateSequence(String name) {
		logger.debug("SequenceManager: Activating sequence: {}", name);

		SequenceDefinition def = namedSequences.get(name);
		if (def == null) {
			logger.warn("SequenceManager: Sequence not found: {}", name);
			logger.warn("Available sequences: {}", namedSequences.keySet());
			return false;
		}

		this.activeSequence = new ActiveSequence(def, configManager);
		logger.debug("SequenceManager: Sequence activated successfully");
		return true;
	}

	public synchronized void resetActiveSequence() {
		logger.debug("SequenceManager: Resetting active sequence");
		if (activeSequence != null) activeSequence.reset();
	}

	public synchronized boolean hasActiveSequence() {
		boolean hasActive = activeSequence != null;
		logger.debug("SequenceManager.hasActiveSequence: {}", hasActive);
		return hasActive;
	}
}