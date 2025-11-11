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
		logger.debug("Processing frame in state={}", sequenceController.getState());
		// SAFETY: Check overlay data before rendering so stale overlays are avoided
		List<DetectionResult> currentAbilities = sequenceManager.getCurrentAbilities();
		List<DetectionResult> nextAbilities = sequenceManager.getNextAbilities();

		logger.trace("Current abilities count: {}", currentAbilities.size());
		for (DetectionResult r : currentAbilities) {
			logger.trace("Current ability {} found={} boundingBox={}", r.templateName, r.found, r.boundingBox);
		}

		logger.trace("Next abilities count: {}", nextAbilities.size());
		for (DetectionResult r : nextAbilities) {
			logger.trace("Next ability {} found={} boundingBox={}", r.templateName, r.found, r.boundingBox);
		}

		try {
			// Update overlays
			updateOverlays();
			logger.trace("Overlays updated successfully.");
		} catch (Exception e) {
			logger.error("Overlay update failed.", e);
			throw e;
		}
		try {
			long startTime = System.nanoTime();

			Mat screenMat = screenCapture.captureScreen();
			if (screenMat == null || screenMat.empty()) {

				logger.warn("Screen capture failed; skipping frame.");
				return;
			}

			// Pre-cache locations for entire sequence so ROI searches are ready
			List<String> abilitiesToCache = sequenceManager.getActiveSequenceAbilityKeys();
			Map<String, DetectionResult> preloadedDetections = detector.cacheAbilityLocations(screenMat, abilitiesToCache);

			// Get required template occurrences
			List<ActiveSequence.DetectionRequirement> requirements = sequenceManager.getDetectionRequirements();

			logger.trace("Detection requirements: {}", requirements);

			if (requirements.isEmpty()) {
				screenMat.close();
				return;
			}

			// Detect all required templates

			logger.debug("Starting template detection for {} requirements.", requirements.size());
			List<DetectionResult> detectionResults = new ArrayList<>(requirements.size());
			Map<String, DetectionResult> detectionByAbility = new HashMap<>(preloadedDetections);

			for (ActiveSequence.DetectionRequirement requirement : requirements) {
				DetectionResult baseResult = detectionByAbility.get(requirement.abilityKey());
				if (baseResult == null) {
					logger.trace("Detecting ability {}", requirement.abilityKey());
					baseResult = detector.detectTemplate(screenMat, requirement.abilityKey(), false);
					detectionByAbility.put(requirement.abilityKey(), baseResult);
					logger.trace("Base result: found={} confidence={}", baseResult.found, baseResult.confidence);
				} else {
					logger.trace("Reusing cached detection for ability {}", requirement.abilityKey());
				}

				DetectionResult adapted = adaptDetectionResult(requirement, baseResult);
				detectionResults.add(adapted);
				logger.trace("Occurrence {} found={} confidence={} isAlternative={}", requirement.instanceId(),
						adapted.found, adapted.confidence, adapted.isAlternative);
			}


			logger.debug("Detection complete with {} results.", detectionResults.size());

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


		logger.trace("Updating overlays: current={} next={}", currentAbilities.size(), nextAbilities.size());

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