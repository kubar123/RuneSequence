package com.lansoftprogramming.runeSequence.core.detection;

import com.lansoftprogramming.runeSequence.application.SequenceManager;
import com.lansoftprogramming.runeSequence.core.sequence.model.EffectiveAbilityConfig;
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
import java.util.concurrent.atomic.AtomicBoolean;

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
	private long frameCounter = 0L;
	private long overlayUpdateCounter = 0L;
	private final AtomicBoolean fatalErrorNotified = new AtomicBoolean(false);
	private int consecutiveCaptureFailures = 0;
	private final AtomicBoolean captureFailureNotified = new AtomicBoolean(false);
	private final Map<String, MissingDetectionStats> missingDetectionsByAbility = new HashMap<>();

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
		consecutiveCaptureFailures = 0;
		captureFailureNotified.set(false);

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
		try {
			// Avoid leaving the screen-grabber in a stale state across pause/resume cycles.
			screenCapture.stopCapture();
		} catch (Exception e) {
			logger.debug("Ignoring failure stopping screen capture", e);
		}
		overlay.clearOverlays();
		if (tooltipOverlay != null) {
			tooltipOverlay.clear();
		}
		logger.info("Detection engine stopped");
	}

	private void processFrame() {
		try {
		long frameId = ++frameCounter;
		long frameStartNanos = System.nanoTime();
		if (logger.isDebugEnabled()) {
			logger.debug("DetectionEngine.processFrame #{} start", frameId);
		}

		try {
			if (!sequenceManager.shouldDetect()) {
				// Ensure overlays are cleared once sequence has finished
				updateOverlays();
				if (logger.isDebugEnabled()) {
					long frameTotalMs = (System.nanoTime() - frameStartNanos) / 1_000_000;
					logger.debug("DetectionEngine.processFrame #{} completed (no detection) in {}ms", frameId, frameTotalMs);
				}
				return;
			}
			updateOverlays();
		} catch (Exception e) {
			logger.error("Overlay update failed.", e);
			throw e;
		}

		try {
			long detectStartNanos = System.nanoTime();
				Mat screenMat = screenCapture.captureScreen();
				if (screenMat == null || screenMat.empty()) {
					consecutiveCaptureFailures++;
					if (consecutiveCaptureFailures == 1 || consecutiveCaptureFailures % 30 == 0) {
						logger.warn("Screen capture failed; skipping frame (consecutiveFailures={}).", consecutiveCaptureFailures);
					}
					if (consecutiveCaptureFailures >= 10 && captureFailureNotified.compareAndSet(false, true)) {
						stop();
						notificationService.showError(
								"Screen capture failed repeatedly, so detection was paused.\n\n" +
										"If you're on a VM/RDP: keep the session unlocked and the desktop visible, then resume detection.\n\n" +
										"See logs for the full error details."
						);
					}
					if (logger.isDebugEnabled()) {
						long frameTotalMs = (System.nanoTime() - frameStartNanos) / 1_000_000;
						logger.debug("DetectionEngine.processFrame #{} aborted after failed capture ({}ms)", frameId, frameTotalMs);
					}
					return;
				}
				consecutiveCaptureFailures = 0;
				captureFailureNotified.set(false);

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
				Map<DetectionRequestKey, DetectionResult> detectionByAbility = new ConcurrentHashMap<>();

				LinkedHashSet<DetectionRequestKey> detectionRequests = new LinkedHashSet<>();
				for (ActiveSequence.DetectionRequirement requirement : requirements) {
					detectionRequests.add(new DetectionRequestKey(requirement.abilityKey(),
							resolveDetectionThreshold(requirement)));
				}

				detectionRequests.parallelStream().forEach(request -> {
					long detectionStart = System.nanoTime();
					DetectionResult baseResult = detector.detectTemplate(screenMat, request.abilityKey(), false,
							request.detectionThreshold());
					detectionByAbility.put(request, baseResult);
					if (logger.isDebugEnabled()) {
						long detectionElapsedMicros = (System.nanoTime() - detectionStart) / 1_000;
						logger.debug("Detection '{}' took {}µs (found={}).",
								request.abilityKey(), detectionElapsedMicros, baseResult.found);
					}
				});

					logMissingDetections(detectionRequests, detectionByAbility, captureRegion);

				for (ActiveSequence.DetectionRequirement requirement : requirements) {
					DetectionRequestKey key = new DetectionRequestKey(requirement.abilityKey(),
							resolveDetectionThreshold(requirement));
					DetectionResult baseResult = detectionByAbility.get(key);
					DetectionResult adapted = adaptDetectionResult(requirement, baseResult, captureRegion);
					detectionResults.add(adapted);
				}

				sequenceManager.processDetection(screenMat, detectionResults);
				updateOverlays();

				long detectElapsedMs = (System.nanoTime() - detectStartNanos) / 1_000_000;
				if (detectElapsedMs > 1300) {
					logger.warn("Frame processing exceeded budget: {}ms", detectElapsedMs);
				}
				if (logger.isDebugEnabled()) {
					long frameTotalMs = (System.nanoTime() - frameStartNanos) / 1_000_000;
					logger.debug("DetectionEngine.processFrame #{} completed in {}ms (detect={}ms)", frameId, frameTotalMs, detectElapsedMs);
				}
			} finally {
				screenMat.close();
			}
		} catch (Exception e) {
			logger.error("Error in detection frame", e);
		}
		} catch (Throwable t) {
			// ScheduledExecutorService tasks stop running if errors escape; keep things robust.
			logger.error("Fatal error in detection engine; stopping detection.", t);
			try {
				stop();
			} catch (Exception ignored) {
				// Best-effort shutdown.
			}

			if (notificationService != null && fatalErrorNotified.compareAndSet(false, true)) {
				String message;
				if (t instanceof UnsatisfiedLinkError || t instanceof LinkageError) {
					message = "Detection is unavailable because OpenCV native libraries failed to load.\n\n" +
							"On Windows this is often fixed by installing the Microsoft Visual C++ Redistributable (x64).\n\n" +
							"See logs for the full error details.";
				} else {
					message = "Detection stopped due to an unexpected error.\n\nSee logs for details.";
				}
				notificationService.showError(message);
			}
		}
	}


	void updateOverlays() {

		long callId = ++overlayUpdateCounter;

		List<DetectionResult> currentAbilities = sequenceManager.getCurrentAbilities();
		List<DetectionResult> nextAbilities = sequenceManager.getNextAbilities();
		List<SequenceTooltip> currentTooltips = sequenceManager.getCurrentTooltips();

		if (logger.isDebugEnabled()) {
			int currentSize = currentAbilities != null ? currentAbilities.size() : 0;
			int nextSize = nextAbilities != null ? nextAbilities.size() : 0;
			int tooltipSize = currentTooltips != null ? currentTooltips.size() : 0;
			logger.debug(
					"DetectionEngine.updateOverlays #{} current={}, next={}, tooltips={}",
					callId,
					currentSize,
					nextSize,
					tooltipSize
			);
		}

		overlay.updateOverlays(currentAbilities, nextAbilities);
		if (tooltipOverlay != null) {
			tooltipOverlay.showTooltips(currentTooltips);
		}
	}

	public boolean isRunning() {
		return isRunning;
	}

	private Double resolveDetectionThreshold(ActiveSequence.DetectionRequirement requirement) {
		EffectiveAbilityConfig effectiveConfig = requirement.effectiveAbilityConfig();
		return effectiveConfig != null ? effectiveConfig.getDetectionThreshold().orElse(null) : null;
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

	private record DetectionRequestKey(String abilityKey, Double detectionThreshold) {
	}

	private void logMissingDetections(LinkedHashSet<DetectionRequestKey> detectionRequests,
	                                  Map<DetectionRequestKey, DetectionResult> detectionByAbility,
	                                  Rectangle captureRegion) {
		if (detectionRequests == null || detectionRequests.isEmpty()) {
			missingDetectionsByAbility.clear();
			return;
		}

		int offsetX = captureRegion != null ? captureRegion.x : 0;
		int offsetY = captureRegion != null ? captureRegion.y : 0;

		LinkedHashSet<String> activeAbilityKeys = new LinkedHashSet<>();
		for (DetectionRequestKey request : detectionRequests) {
			activeAbilityKeys.add(request.abilityKey());

			DetectionResult result = detectionByAbility.get(request);
			if (result != null && result.found) {
				MissingDetectionStats stats = missingDetectionsByAbility.get(request.abilityKey());
				if (stats != null) {
					stats.consecutiveMisses = 0;
				}
				continue;
			}

			MissingDetectionStats stats = missingDetectionsByAbility.computeIfAbsent(
					request.abilityKey(),
					ignored -> new MissingDetectionStats()
			);
			stats.consecutiveMisses++;

			if (stats.consecutiveMisses == 1 || stats.consecutiveMisses % 30 == 0) {
				String normalizedKey = TemplateDetector.normalizeAbilityKeyForLookup(request.abilityKey());
				double requiredThreshold = detector.getThresholdForTemplate(normalizedKey, request.detectionThreshold());

				String bestAt;
				if (result != null && result.location != null) {
					bestAt = String.format("(%d,%d)", result.location.x + offsetX, result.location.y + offsetY);
				} else {
					bestAt = "<unknown>";
				}

				String bestBounds;
				if (result != null && result.boundingBox != null) {
					Rectangle bb = result.boundingBox;
					bestBounds = String.format("(%d,%d %dx%d)", bb.x + offsetX, bb.y + offsetY, bb.width, bb.height);
				} else {
					bestBounds = "<unknown>";
				}

				double bestConfidence = result != null ? result.confidence : 0.0;
				logger.info(
						"Ability not detected: {} (bestMatch={}, required={}, bestAt={}, bestBounds={}, consecutiveMisses={})",
						request.abilityKey(),
						String.format(Locale.ROOT, "%.2f%%", bestConfidence * 100.0),
						String.format(Locale.ROOT, "%.2f%%", requiredThreshold * 100.0),
						bestAt,
						bestBounds,
						stats.consecutiveMisses
				);
			}
		}

		missingDetectionsByAbility.keySet().retainAll(activeAbilityKeys);
	}

	private static final class MissingDetectionStats {
		private int consecutiveMisses = 0;
	}
}