package com.lansoftprogramming.runeSequence.detection;

import com.lansoftprogramming.runeSequence.capture.ScreenCapture;
import com.lansoftprogramming.runeSequence.config.ConfigManager;
import com.lansoftprogramming.runeSequence.sequence.SequenceManager;
import org.bytedeco.opencv.opencv_core.Mat;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

public class DetectionEngine {
	private static final Logger logger = LoggerFactory.getLogger(DetectionEngine.class);

	private final ScreenCapture screenCapture;
	private final TemplateDetector detector;
	private final SequenceManager sequenceManager;
	private final OverlayRenderer overlay;
	private final ConfigManager configManager;

	private ScheduledExecutorService scheduler;
	private volatile boolean isRunning = false;

	public DetectionEngine(ScreenCapture screenCapture, TemplateDetector detector,
	                       SequenceManager sequenceManager, OverlayRenderer overlay,
	                       ConfigManager configManager) {
		this.screenCapture = screenCapture;
		this.detector = detector;
		this.sequenceManager = sequenceManager;
		this.overlay = overlay;
		this.configManager = configManager;
	}

	public void start() {
		if (isRunning) return;

		isRunning = true;
		int intervalMs = configManager.getDetectionInterval();

		scheduler = Executors.newSingleThreadScheduledExecutor(r -> {
			Thread t = new Thread(r, "DetectionEngine");
			t.setDaemon(true);
			return t;
		});

		scheduler.scheduleAtFixedRate(this::processFrame, 0, intervalMs, TimeUnit.MILLISECONDS);
		logger.info("Detection engine started ({}ms interval)", intervalMs);
	}

	public void stop() {
		if (!isRunning) return;

		isRunning = false;
		if (scheduler != null) {
			scheduler.shutdown();
		}
		overlay.clearOverlays();
		logger.info("Detection engine stopped");
	}

	private void processFrame() {
		logger.debug("DetectionEngine.processFrame: Starting");

		try {
			long startTime = System.nanoTime();

			Mat screenMat = screenCapture.captureScreen();
			if (screenMat == null || screenMat.empty()) {
				logger.warn("DetectionEngine: Screen capture failed");
				return;
			}
			logger.debug("DetectionEngine: Screen captured successfully");

			// Get required templates
			List<String> requiredTemplates = sequenceManager.getRequiredTemplates();
			logger.debug("DetectionEngine: Required templates: {}", requiredTemplates);

			if (requiredTemplates.isEmpty()) {
				logger.debug("DetectionEngine: No templates required");
				screenMat.close();
				return;
			}

			// Detect all required templates
			logger.debug("DetectionEngine: Starting template detection");
			List<DetectionResult> detectionResults = new ArrayList<>();
			for (String templateName : requiredTemplates) {
				logger.debug("  Detecting: {}", templateName);
				DetectionResult result = detector.detectTemplate(screenMat, templateName);
				detectionResults.add(result);
				logger.debug("    Result: found={} confidence={}", result.found, result.confidence);
			}
			logger.debug("DetectionEngine: Detection complete, {} results", detectionResults.size());

			// Process results through sequence manager
			sequenceManager.processDetection(detectionResults);

			// Update overlays
			updateOverlays();

			screenMat.close(); // Prevent memory leak

			long elapsedMs = (System.nanoTime() - startTime) / 1_000_000;
			if (elapsedMs > 100) {
				logger.debug("Frame processing took {}ms", elapsedMs);
			}

		} catch (Exception e) {
			logger.error("Error in detection frame", e);
		}
	}

	/*##*/ // Remove old detectAbilities method - it was causing the bug
	// The old method only passed results if found=true, but we need ALL results

	private void updateOverlays() {
		List<DetectionResult> currentAbilities = sequenceManager.getCurrentAbilities();
		List<DetectionResult> nextAbilities = sequenceManager.getNextAbilities();
		logger.debug("DetectionEngine.updateOverlays: current={} next={}", currentAbilities.size(), nextAbilities.size());
		overlay.updateOverlays(currentAbilities, nextAbilities);
	}

	public boolean isRunning() {
		return isRunning;
	}
}