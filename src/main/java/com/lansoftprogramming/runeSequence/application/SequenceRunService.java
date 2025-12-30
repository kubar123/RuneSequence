package com.lansoftprogramming.runeSequence.application;

import com.lansoftprogramming.runeSequence.application.TooltipScheduleBuilder.BuildResult;
import com.lansoftprogramming.runeSequence.core.detection.DetectionEngine;
import com.lansoftprogramming.runeSequence.core.sequence.model.AbilitySettingsOverrides;
import com.lansoftprogramming.runeSequence.core.sequence.model.SequenceDefinition;
import com.lansoftprogramming.runeSequence.infrastructure.hotkey.HotkeyListener;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Map;
import java.util.function.Consumer;

/**
 * Bridges UI controls to sequence state changes and detection engine lifecycle.
 */
public class SequenceRunService implements HotkeyListener {

	private static final Logger logger = LoggerFactory.getLogger(SequenceRunService.class);

	private final SequenceController sequenceController;
	private final SequenceManager sequenceManager;
	private final DetectionEngine detectionEngine;
	private final TooltipScheduleBuilder tooltipScheduleBuilder;

	public SequenceRunService(SequenceController sequenceController,
	                          SequenceManager sequenceManager,
	                          DetectionEngine detectionEngine,
	                          TooltipScheduleBuilder tooltipScheduleBuilder) {
		this.sequenceController = sequenceController;
		this.sequenceManager = sequenceManager;
		this.detectionEngine = detectionEngine;
		this.tooltipScheduleBuilder = tooltipScheduleBuilder != null
				? tooltipScheduleBuilder
				: new TooltipScheduleBuilder();
	}

	public TooltipScheduleBuilder.BuildResult buildSchedule(String expression) {
		return buildSchedule(expression, null, null);
	}

	public TooltipScheduleBuilder.BuildResult buildSchedule(String expression,
	                                                       Map<String, AbilitySettingsOverrides> perInstanceOverrides,
	                                                       Map<String, AbilitySettingsOverrides> perAbilityOverrides) {
		return tooltipScheduleBuilder.build(expression != null ? expression : "", perInstanceOverrides, perAbilityOverrides);
	}

	public boolean canBuildSequence(String expression) {
		if (expression == null || expression.isBlank()) {
			return false;
		}
		try {
			return buildSchedule(expression).definition() != null;
		} catch (Exception e) {
			return false;
		}
	}

	public synchronized void start() {
		if (sequenceController.getState() == SequenceController.State.PAUSED) {
			sequenceController.resetToReady();
		}
		ensureDetectionRunning();
		sequenceController.onStartSequence();
		logger.info("Start requested via UI controls.");
	}

	public synchronized void restart() {
		ensureDetectionRunning();
		sequenceController.onRestartSequence();
		logger.info("Restart requested via UI controls.");
	}

	public synchronized void pause() {
		sequenceController.setPaused();
		sequenceManager.resetActiveSequence();
		detectionEngine.stop();
		logger.info("Detection paused via UI controls.");
	}

	public boolean isDetectionRunning() {
		return detectionEngine.isRunning();
	}

	public void addStateChangeListener(SequenceController.StateChangeListener listener) {
		sequenceController.addStateChangeListener(listener);
	}

	public void addProgressListener(Consumer<SequenceManager.SequenceProgress> listener) {
		sequenceManager.addProgressListener(listener);
	}

	public SequenceController.State getCurrentState() {
		return sequenceController.getState();
	}

	public SequenceManager.SequenceProgress getProgressSnapshot() {
		return sequenceManager.snapshotProgress();
	}

	public synchronized void prepareReadyState() {
		sequenceController.resetToReady();
		sequenceManager.resetActiveSequence(false);
		ensureDetectionRunning();
		logger.info("Ready requested via UI controls.");
	}

	/**
	 * Rebuilds and updates the in-memory sequence definition and tooltip schedule
	 * for the given preset id based on the provided expression.
	 *
	 * @param presetId   identifier of the preset/rotation
	 * @param expression raw rotation expression (may contain tooltip markup)
	 * @return true if a usable sequence definition was produced and applied
	 */
	public synchronized boolean refreshSequenceFromExpression(String presetId, String expression) {
		return refreshSequenceFromExpression(presetId, expression, null, null);
	}

	public synchronized boolean refreshSequenceFromExpression(String presetId,
	                                                          String expression,
	                                                          Map<String, AbilitySettingsOverrides> perInstanceOverrides,
	                                                          Map<String, AbilitySettingsOverrides> perAbilityOverrides) {
		if (presetId == null || presetId.isBlank()) {
			logger.warn("Ignoring sequence refresh with blank preset id.");
			return false;
		}

		BuildResult result = buildSchedule(expression, perInstanceOverrides, perAbilityOverrides);
		SequenceDefinition definition = result.definition();
		if (definition == null) {
			logger.warn("Parsed sequence for preset '{}' is unavailable; definition is null.", presetId);
			sequenceManager.upsertSequence(presetId, null, result.schedule());
			return false;
		}

		sequenceManager.upsertSequence(presetId, definition, result.schedule());
		logger.info("Refreshed in-memory sequence definition for preset '{}'.", presetId);
		return true;
	}

	/**
	 * Switches the active sequence used by detection to the given identifier.
	 * A null or blank identifier clears the active sequence, halting detections
	 * until a new rotation is selected.
	 *
	 * @param sequenceId the identifier of the sequence to activate, or null to clear
	 * @return true if the selection was applied or cleared; false if the id could not be resolved
	 */
	public synchronized boolean switchActiveSequence(String sequenceId) {
		if (sequenceId == null || sequenceId.isBlank()) {
			logger.info("Clearing active sequence selection.");
			sequenceManager.clearActiveSequence();
			sequenceController.resetToReady();
			return true;
		}

		boolean activated = sequenceManager.activateSequence(sequenceId);
		if (!activated) {
			logger.warn("Sequence '{}' not found; unable to switch active sequence.", sequenceId);
			sequenceController.resetToReady();
			return false;
		}

		sequenceManager.resetActiveSequence(false);
		sequenceController.resetToReady();
		logger.info("Switched active sequence to '{}'.", sequenceId);
		return true;
	}

	@Override
	public void onStartSequence() {
		start();
	}

	@Override
	public void onRestartSequence() {
		restart();
	}

	private void ensureDetectionRunning() {
		if (!detectionEngine.isRunning()) {
			detectionEngine.start();
		}
	}
}
