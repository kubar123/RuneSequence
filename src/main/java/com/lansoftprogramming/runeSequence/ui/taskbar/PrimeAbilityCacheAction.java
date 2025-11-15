package com.lansoftprogramming.runeSequence.ui.taskbar;

import com.lansoftprogramming.runeSequence.core.detection.DetectionEngine;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class PrimeAbilityCacheAction implements MenuAction {
	private static final Logger logger = LoggerFactory.getLogger(PrimeAbilityCacheAction.class);

	private final DetectionEngine detectionEngine;

	public PrimeAbilityCacheAction(DetectionEngine detectionEngine) {
		this.detectionEngine = detectionEngine;
	}

	@Override
	public void execute() {
		if (detectionEngine == null) {
			logger.warn("Detection engine not available; skipping ability cache prime.");
			return;
		}
		logger.info("Manual prime requested from taskbar.");
		detectionEngine.primeActiveSequence();
	}
}
