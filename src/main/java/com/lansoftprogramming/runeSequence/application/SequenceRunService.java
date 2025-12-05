package com.lansoftprogramming.runeSequence.application;

import com.lansoftprogramming.runeSequence.core.detection.DetectionEngine;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Bridges UI controls to sequence state changes and detection engine lifecycle.
 */
public class SequenceRunService {

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

	public SequenceController.State getCurrentState() {
		return sequenceController.getState();
	}

	private void ensureDetectionRunning() {
		if (!detectionEngine.isRunning()) {
			detectionEngine.start();
		}
	}
}
