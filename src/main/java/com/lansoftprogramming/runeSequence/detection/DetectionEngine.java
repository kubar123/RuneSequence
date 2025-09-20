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

		// SAFETY: Check overlay data before rendering
		System.out.println("DetectionEngine: About to update overlays...");
		List<DetectionResult> currentAbilities = sequenceManager.getCurrentAbilities();
		List<DetectionResult> nextAbilities = sequenceManager.getNextAbilities();

		System.out.println("Current abilities count: " + currentAbilities.size());
		for (DetectionResult r : currentAbilities) {
			System.out.println("  Current: " + r.templateName + " found=" + r.found +
					" boundingBox=" + r.boundingBox);
		}

		System.out.println("Next abilities count: " + nextAbilities.size());
		for (DetectionResult r : nextAbilities) {
			System.out.println("  Next: " + r.templateName + " found=" + r.found +
					" boundingBox=" + r.boundingBox);
		}

		try {
			// Update overlays
			updateOverlays();
			System.out.println("DetectionEngine: Overlays updated successfully");
		} catch (Exception e) {
			System.err.println("DetectionEngine: OVERLAY CRASH: " + e.getMessage());
			e.printStackTrace();
			throw e;
		}
		System.out.println("DetectionEngine.processFrame: Starting");

		try {
			long startTime = System.nanoTime();

			Mat screenMat = screenCapture.captureScreen();
			if (screenMat == null || screenMat.empty()) {

				System.out.println("DetectionEngine: Screen capture failed");
				return;
			}


			System.out.println("DetectionEngine: Screen captured successfully");

			// Get required templates
			List<String> requiredTemplates = sequenceManager.getRequiredTemplates();

			System.out.println("DetectionEngine: Required templates: " + requiredTemplates);

			if (requiredTemplates.isEmpty()) {

				System.out.println("DetectionEngine: No templates required");
				screenMat.close();
				return;
			}

			// Detect all required templates

			System.out.println("DetectionEngine: Starting template detection");
			List<DetectionResult> detectionResults = new ArrayList<>();

			for (String templateName : requiredTemplates) {

				System.out.println("  Detecting: " + templateName);
				DetectionResult result = detector.detectTemplate(screenMat, templateName);
				detectionResults.add(result);

				System.out.println("    Result: found=" + result.found + " confidence=" + result.confidence);
			}


			System.out.println("DetectionEngine: Detection complete, " + detectionResults.size() + " results");

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

			System.err.println("DetectionEngine.processFrame ERROR: " + e.getMessage());
			e.printStackTrace();
			logger.error("Error in detection frame", e);
		}
	}

	/*##*/ // Remove old detectAbilities method - it was causing the bug
	// The old method only passed results if found=true, but we need ALL results

	private void updateOverlays() {

		List<DetectionResult> currentAbilities = sequenceManager.getCurrentAbilities();
		List<DetectionResult> nextAbilities = sequenceManager.getNextAbilities();


		System.out.println("DetectionEngine.updateOverlays: current=" + currentAbilities.size() + " next=" + nextAbilities.size());

		overlay.updateOverlays(currentAbilities, nextAbilities);
	}

	public boolean isRunning() {
		return isRunning;
	}
}