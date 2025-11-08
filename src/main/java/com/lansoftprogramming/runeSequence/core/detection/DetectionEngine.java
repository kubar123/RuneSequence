package com.lansoftprogramming.runeSequence.core.detection;

import com.lansoftprogramming.runeSequence.application.SequenceController;
import com.lansoftprogramming.runeSequence.application.SequenceManager;
import com.lansoftprogramming.runeSequence.core.sequence.runtime.ActiveSequence;
import com.lansoftprogramming.runeSequence.infrastructure.capture.ScreenCapture;
import com.lansoftprogramming.runeSequence.ui.overlay.OverlayRenderer;
import org.bytedeco.opencv.opencv_core.Mat;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.awt.*;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

public class DetectionEngine {
	private static final Logger logger = LoggerFactory.getLogger(DetectionEngine.class);

	private final ScreenCapture screenCapture;
	private final TemplateDetector detector;
	private final SequenceManager sequenceManager;
	private final OverlayRenderer overlay;
	private final int detectionIntervalMs;
	private final SequenceController sequenceController;

	private ScheduledExecutorService scheduler;
	private volatile boolean isRunning = false;

	public DetectionEngine(ScreenCapture screenCapture, TemplateDetector detector,
	                       SequenceManager sequenceManager, OverlayRenderer overlay,
	                       int detectionIntervalMs, SequenceController sequenceController) {
		this.screenCapture = screenCapture;
		this.detector = detector;
		this.sequenceManager = sequenceManager;
		this.overlay = overlay;
		this.detectionIntervalMs = detectionIntervalMs;
		this.sequenceController = sequenceController;
	}

	public void start() {
		if (isRunning) return;

		isRunning = true;

		scheduler = Executors.newSingleThreadScheduledExecutor(r -> {
			Thread t = new Thread(r, "DetectionEngine");
			t.setDaemon(true);
			return t;
		});

		scheduler.scheduleAtFixedRate(this::processFrame, 0, detectionIntervalMs, TimeUnit.MILLISECONDS);
		logger.info("Detection engine started ({}ms interval)", detectionIntervalMs);
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
		System.out.println("DetectionEngine: State=" + sequenceController.getState());
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

			// Get required template occurrences
			List<ActiveSequence.DetectionRequirement> requirements = sequenceManager.getDetectionRequirements();

			System.out.println("DetectionEngine: Detection requirements: " + requirements);

			if (requirements.isEmpty()) {

				System.out.println("DetectionEngine: No templates required");
				screenMat.close();
				return;
			}

			// Detect all required templates

			System.out.println("DetectionEngine: Starting template detection");
			List<DetectionResult> detectionResults = new ArrayList<>(requirements.size());
			Map<String, DetectionResult> detectionByAbility = new HashMap<>();

			for (ActiveSequence.DetectionRequirement requirement : requirements) {
				DetectionResult baseResult = detectionByAbility.get(requirement.abilityKey());
				if (baseResult == null) {
					System.out.println("  Detecting ability: " + requirement.abilityKey());
					baseResult = detector.detectTemplate(screenMat, requirement.abilityKey(), false);
					detectionByAbility.put(requirement.abilityKey(), baseResult);
					System.out.println("    Base result: found=" + baseResult.found +
							" confidence=" + baseResult.confidence);
				} else {
					System.out.println("  Reusing detection for ability: " + requirement.abilityKey());
				}

				DetectionResult adapted = adaptDetectionResult(requirement, baseResult);
				detectionResults.add(adapted);
				System.out.println("    Occurrence: " + requirement.instanceId() +
						" found=" + adapted.found +
						" confidence=" + adapted.confidence +
						" isAlternative=" + adapted.isAlternative);
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

	private DetectionResult adaptDetectionResult(ActiveSequence.DetectionRequirement requirement, DetectionResult base) {
		if (base != null && base.found) {
			Point locationCopy = base.location != null ? new Point(base.location) : null;
			Rectangle boundsCopy = base.boundingBox != null ? new Rectangle(base.boundingBox) : null;
			return DetectionResult.found(requirement.instanceId(), locationCopy, base.confidence, boundsCopy, requirement.isAlternative());
		}
		return DetectionResult.notFound(requirement.instanceId(), requirement.isAlternative());
	}
}