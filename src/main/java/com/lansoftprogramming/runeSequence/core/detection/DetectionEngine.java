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
	private SequenceController.State lastSequenceState;
	private List<String> cachedAbilityKeyOrder = List.of();
	private boolean needsAbilityPrecache = true;

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
		this.lastSequenceState = sequenceController != null ? sequenceController.getState() : SequenceController.State.READY;
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
		needsAbilityPrecache = true;
		cachedAbilityKeyOrder = List.of();
		lastSequenceState = sequenceController != null ? sequenceController.getState() : SequenceController.State.READY;
		logger.info("Detection engine stopped");
	}

	private void processFrame() {
		SequenceController.State currentState = sequenceController.getState();
		trackSequenceState(currentState);
		// SAFETY: refresh overlays before each detection pass so stale frames do not linger
		try {
			// Update overlays
			updateOverlays();
		} catch (Exception e) {
			logger.error("Overlay update failed.", e);
			throw e;
		}
		try {
			long startTime = System.nanoTime();

			Mat screenMat = screenCapture.captureScreen();
			Rectangle captureRegion = screenCapture.getRegion();
			if (screenMat == null || screenMat.empty()) {

				logger.warn("Screen capture failed; skipping frame.");
				return;
			}

			// Pre-cache locations for entire sequence so ROI searches are ready
			Map<String, DetectionResult> preloadedDetections = maybePrimeAbilityCache(screenMat);

			// Get required template occurrences
			List<ActiveSequence.DetectionRequirement> requirements = sequenceManager.getDetectionRequirements();

			if (requirements.isEmpty()) {
				screenMat.close();
				return;
			}

			// Detect all required templates
			List<DetectionResult> detectionResults = new ArrayList<>(requirements.size());
			Map<String, DetectionResult> detectionByAbility = new HashMap<>(preloadedDetections);

			for (ActiveSequence.DetectionRequirement requirement : requirements) {
				DetectionResult baseResult = detectionByAbility.get(requirement.abilityKey());
				if (baseResult == null) {
					baseResult = detector.detectTemplate(screenMat, requirement.abilityKey(), false);
					detectionByAbility.put(requirement.abilityKey(), baseResult);
				}

				DetectionResult adapted = adaptDetectionResult(requirement, baseResult, captureRegion);
				detectionResults.add(adapted);
			}

			// Process results through sequence manager
			sequenceManager.processDetection(detectionResults);

			// Update overlays
			updateOverlays();

			screenMat.close(); // Prevent memory leak

			long elapsedMs = (System.nanoTime() - startTime) / 1_000_000;
			if (elapsedMs > 100) {
				logger.warn("Frame processing exceeded budget: {}ms", elapsedMs);
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


		overlay.updateOverlays(currentAbilities, nextAbilities);
	}

	public boolean isRunning() {
		return isRunning;
	}

	private DetectionResult adaptDetectionResult(ActiveSequence.DetectionRequirement requirement,
	                                           DetectionResult base,
	                                           Rectangle captureRegion) {
		int offsetX = captureRegion != null ? captureRegion.x : 0;
		int offsetY = captureRegion != null ? captureRegion.y : 0;
		if (base != null && base.found) {
			Point locationCopy = base.location != null ? new Point(base.location) : null;
			Rectangle boundsCopy = base.boundingBox != null ? new Rectangle(base.boundingBox) : null;
			if (locationCopy != null) {
				locationCopy.translate(offsetX, offsetY);
			}
			if (boundsCopy != null) {
				boundsCopy.translate(offsetX, offsetY);
			}
			return DetectionResult.found(requirement.instanceId(), locationCopy, base.confidence, boundsCopy, requirement.isAlternative());
		}
		return DetectionResult.notFound(requirement.instanceId(), requirement.isAlternative());
	}

	private void trackSequenceState(SequenceController.State current) {
		if (sequenceController == null || current == null) {
			return;
		}
		if (current != lastSequenceState) {
			if (current == SequenceController.State.RUNNING) {
				needsAbilityPrecache = true;
			}
			lastSequenceState = current;
		}
	}

	private Map<String, DetectionResult> maybePrimeAbilityCache(Mat screenMat) {
		if (screenMat == null || screenMat.empty()) {
			return Map.of();
		}

		List<String> abilityKeys = sequenceManager.getActiveSequenceAbilityKeys();
		if (abilityKeys.isEmpty()) {
			cachedAbilityKeyOrder = List.of();
			needsAbilityPrecache = true;
			return Map.of();
		}

		if (!abilityKeys.equals(cachedAbilityKeyOrder)) {
			cachedAbilityKeyOrder = List.copyOf(abilityKeys);
			needsAbilityPrecache = true;
		}

		if (!needsAbilityPrecache) {
			return Map.of();
		}

		Map<String, DetectionResult> preloaded = detector.cacheAbilityLocations(screenMat, abilityKeys);
		needsAbilityPrecache = false;
		return preloaded;
	}
}
