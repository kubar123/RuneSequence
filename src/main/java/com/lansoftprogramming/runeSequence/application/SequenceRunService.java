package com.lansoftprogramming.runeSequence.application;

import com.lansoftprogramming.runeSequence.core.detection.DetectionEngine;
import com.lansoftprogramming.runeSequence.infrastructure.hotkey.HotkeyListener;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.function.Consumer;

/**
 * Bridges UI controls to sequence state changes and detection engine lifecycle.
 */
public class SequenceRunService implements HotkeyListener {

	private static final Logger logger = LoggerFactory.getLogger(SequenceRunService.class);

	private final SequenceController sequenceController;
	private final SequenceManager sequenceManager;
	private final DetectionEngine detectionEngine;

	public SequenceRunService(SequenceController sequenceController,
	                          SequenceManager sequenceManager,
	                          DetectionEngine detectionEngine) {
		this.sequenceController = sequenceController;
		this.sequenceManager = sequenceManager;
		this.detectionEngine = detectionEngine;
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
