package com.lansoftprogramming.runeSequence.sequence;

import com.lansoftprogramming.runeSequence.config.ConfigManager;
import com.lansoftprogramming.runeSequence.detection.DetectionResult;

import java.util.List;
import java.util.Map;
import java.util.Objects;


public class SequenceManager {

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
		if (activeSequence == null) return List.of();
		return activeSequence.getRequiredTemplates();
	}

	public synchronized void processDetection(List<DetectionResult> results) {
		if (activeSequence != null) {
			activeSequence.processDetections(results);
		}
	}

	public synchronized List<DetectionResult> getCurrentAbilities() {
		if (activeSequence == null) return List.of();
		return activeSequence.getCurrentAbilities();
	}

	public synchronized List<DetectionResult> getNextAbilities() {
		if (activeSequence == null) return List.of();
		return activeSequence.getNextAbilities();
	}

	public synchronized void addNamedSequence(String name, SequenceDefinition def) {
		namedSequences.put(name, def);
	}

	public synchronized boolean activateSequence(String name) {
		SequenceDefinition def = namedSequences.get(name);
		if (def == null) return false;
		this.activeSequence = new ActiveSequence(def, configManager);
		return true;
	}

	public synchronized void resetActiveSequence() {
		if (activeSequence != null) activeSequence.reset();
	}

	public synchronized boolean hasActiveSequence() {
		return activeSequence != null;
	}
}
