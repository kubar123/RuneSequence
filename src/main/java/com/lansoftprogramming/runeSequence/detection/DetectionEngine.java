package com.lansoftprogramming.runeSequence.detection;

import com.lansoftprogramming.runeSequence.capture.ScreenCapture;
import com.lansoftprogramming.runeSequence.config.ConfigManager;
import com.lansoftprogramming.runeSequence.sequence.SequenceManager;
import org.bytedeco.opencv.opencv_core.Mat;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.awt.*;
import java.awt.image.BufferedImage;
import java.util.ArrayList;
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
		try {
			long startTime = System.nanoTime();

/
			Mat screenMat = screenCapture.captureScreen();

			detectAbilities(screenMat);

			screenMat.close(); // Prevent memory leak

			long elapsedMs = (System.nanoTime() - startTime) / 1_000_000;
			if (elapsedMs > 100) { // Log slow frames
				logger.debug("Frame processing took {}ms", elapsedMs);
			}

		} catch (Exception e) {
			logger.error("Error in detection frame", e);
		}
	}


	private void detectAbilities(Mat screenMat) {
		List<String> templatesToDetect = sequenceManager.getRequiredTemplates();
		List<DetectionResult> detectionResults = new ArrayList<>();

		for (String templateName : templatesToDetect) {
			DetectionResult result = detector.detectTemplate(screenMat, templateName);
			if (result.found) {

				System.out.printf("Found %s at location: x=%d, y=%d (confidence=%.3f)%n",
						result.templateName, result.location.x, result.location.y, result.confidence);

				detectionResults.add(result);
			}
		}

		// Process results through sequence manager
		sequenceManager.processDetection(detectionResults);

		// Update overlays
		updateOverlays();
	}


	private void updateOverlays() {
		List<DetectionResult> currentAbilities = sequenceManager.getCurrentAbilities();
		List<DetectionResult> nextAbilities = sequenceManager.getNextAbilities();

		overlay.updateOverlays(currentAbilities, nextAbilities);
	}


	public boolean isRunning() {
		return isRunning;
	}
}
