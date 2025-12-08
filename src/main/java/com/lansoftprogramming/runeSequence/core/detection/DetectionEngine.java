package com.lansoftprogramming.runeSequence.core.detection;

import com.lansoftprogramming.runeSequence.application.SequenceManager;
import com.lansoftprogramming.runeSequence.core.sequence.runtime.ActiveSequence;
import com.lansoftprogramming.runeSequence.core.sequence.runtime.SequenceTooltip;
import com.lansoftprogramming.runeSequence.infrastructure.capture.ScreenCapture;
import com.lansoftprogramming.runeSequence.ui.notification.NotificationService;
import com.lansoftprogramming.runeSequence.ui.overlay.MouseTooltipOverlay;
import com.lansoftprogramming.runeSequence.ui.overlay.OverlayRenderer;
import org.bytedeco.opencv.opencv_core.Mat;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.awt.*;
import java.util.*;
import java.util.List;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

public class DetectionEngine {
	private static final Logger logger = LoggerFactory.getLogger(DetectionEngine.class);

	private final ScreenCapture screenCapture;
	private final TemplateDetector detector;
	private final SequenceManager sequenceManager;
	private final OverlayRenderer overlay;
	private final MouseTooltipOverlay tooltipOverlay;
	private final int detectionIntervalMs;
	private final NotificationService notificationService;

	private ScheduledExecutorService scheduler;
	private volatile boolean isRunning = false;

	public DetectionEngine(ScreenCapture screenCapture, TemplateDetector detector,
	                       SequenceManager sequenceManager, OverlayRenderer overlay,
	                       MouseTooltipOverlay tooltipOverlay,
	                       NotificationService notificationService, int detectionIntervalMs) {
		this.screenCapture = screenCapture;
		this.detector = detector;
		this.sequenceManager = sequenceManager;
		this.overlay = overlay;
		this.tooltipOverlay = tooltipOverlay;
		this.notificationService = notificationService;
		this.detectionIntervalMs = detectionIntervalMs;
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
		if (!isRunning) {
			overlay.clearOverlays();
			if (tooltipOverlay != null) {
				tooltipOverlay.clear();
			}
			return;
		}

		isRunning = false;
		if (scheduler != null) {
			scheduler.shutdown();
		}
		overlay.clearOverlays();
		if (tooltipOverlay != null) {
			tooltipOverlay.clear();
		}
		logger.info("Detection engine stopped");
	}

	private void processFrame() {
		try {
			if (!sequenceManager.shouldDetect()) {
				// Ensure overlays are cleared once sequence has finished
				updateOverlays();
				return;
			}
			updateOverlays();
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

			try {
				Rectangle captureRegion = screenCapture.getRegion();
				List<ActiveSequence.DetectionRequirement> requirements = sequenceManager.getDetectionRequirements();
				if (logger.isDebugEnabled()) {
					logger.debug("Frame requirements ({}): {}", requirements.size(), describeRequirements(requirements));
				}
				if (requirements.isEmpty()) {
					return;
				}

				List<DetectionResult> detectionResults = new ArrayList<>(requirements.size());
				Map<String, DetectionResult> detectionByAbility = new ConcurrentHashMap<>();

				Set<String> abilityKeys = new HashSet<>();
				for (ActiveSequence.DetectionRequirement requirement : requirements) {
					abilityKeys.add(requirement.abilityKey());
				}

				abilityKeys.parallelStream().forEach(abilityKey -> {
					long detectionStart = System.nanoTime();
					DetectionResult baseResult = detector.detectTemplate(screenMat, abilityKey, false);
					detectionByAbility.put(abilityKey, baseResult);
					if (logger.isDebugEnabled()) {
						long detectionElapsedMicros = (System.nanoTime() - detectionStart) / 1_000;
						logger.debug("Detection '{}' took {}µs (found={}).",
								abilityKey, detectionElapsedMicros, baseResult.found);
					}
				});

				for (ActiveSequence.DetectionRequirement requirement : requirements) {
					DetectionResult baseResult = detectionByAbility.get(requirement.abilityKey());
					DetectionResult adapted = adaptDetectionResult(requirement, baseResult, captureRegion);
					detectionResults.add(adapted);
				}

				sequenceManager.processDetection(screenMat, detectionResults);
				updateOverlays();

				long elapsedMs = (System.nanoTime() - startTime) / 1_000_000;
				if (elapsedMs > 1300) {
					logger.warn("Frame processing exceeded budget: {}ms", elapsedMs);
				}
			} finally {
				screenMat.close();
			}
		} catch (Exception e) {
			logger.error("Error in detection frame", e);
		}
	}


	private void updateOverlays() {

		List<DetectionResult> currentAbilities = sequenceManager.getCurrentAbilities();
		List<DetectionResult> nextAbilities = sequenceManager.getNextAbilities();
		List<SequenceTooltip> currentTooltips = sequenceManager.getCurrentTooltips();


		overlay.updateOverlays(currentAbilities, nextAbilities);
		if (tooltipOverlay != null) {
			tooltipOverlay.showTooltips(currentTooltips);
		}
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

	public void primeActiveSequence() {
		List<String> abilityKeys = sequenceManager.getActiveSequenceAbilityKeys();
		if (abilityKeys.isEmpty()) {
			logger.warn("Unable to prime ability cache - no active sequence.");
			return;
		}

		Mat screenMat = null;
		try {
			screenMat = screenCapture.captureScreen();
			if (screenMat == null || screenMat.empty()) {
				logger.warn("Screen capture failed while priming ability cache.");
				return;
			}

			Map<String, DetectionResult> preloaded = detector.cacheAbilityLocations(screenMat, abilityKeys);
			int totalAbilities = abilityKeys.size();
			long cachedCount = abilityKeys.stream()
					.filter(key -> detector.getCachedLocation(key) != null)
					.count();

			logger.info("Primed ability cache: {}/{} abilities cached.", cachedCount, totalAbilities);
			notificationService.showSuccess(String.format("Primed %d/%d abilities.", cachedCount, totalAbilities));
		} catch (Exception e) {
			logger.error("Failed to prime ability cache.", e);
		} finally {
			if (screenMat != null) {
				screenMat.close();
			}
		}
	}

	private String describeRequirements(List<ActiveSequence.DetectionRequirement> requirements) {
		StringBuilder builder = new StringBuilder();
		for (int i = 0; i < requirements.size(); i++) {
			ActiveSequence.DetectionRequirement req = requirements.get(i);
			builder.append(req.instanceId())
					.append("->")
					.append(req.abilityKey());
			if (req.isAlternative()) {
				builder.append("[ALT]");
			}
			if (i < requirements.size() - 1) {
				builder.append(", ");
			}
		}
		return builder.toString();
	}
}