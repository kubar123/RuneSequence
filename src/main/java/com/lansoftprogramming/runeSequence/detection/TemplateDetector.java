package com.lansoftprogramming.runeSequence.detection;

import com.lansoftprogramming.runeSequence.TemplateCache;
import com.lansoftprogramming.runeSequence.config.AbilityConfig;
import com.lansoftprogramming.runeSequence.config.ConfigManager;
import org.bytedeco.javacpp.DoublePointer;
import org.bytedeco.opencv.opencv_core.Mat;
import org.bytedeco.opencv.opencv_core.MatVector;
import org.bytedeco.opencv.opencv_core.Rect;
import org.bytedeco.opencv.opencv_core.Point;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.awt.*;
import java.util.HashMap;
import java.util.Map;

/*
 * Use static imports for the OpenCV global functions provided by the JavaCPP presets.
 */
import static org.bytedeco.opencv.global.opencv_core.*;
import static org.bytedeco.opencv.global.opencv_imgproc.*;

/**
 * Template Detector using JavaCPP (bytedeco) OpenCV presets.
 * <p>
 * Notes about resource management:
 * - Many operations return native Mats. When we create Mats ourselves (new Mat(), cvtColor results, merged mats),
 * we close them when no longer needed to avoid native memory leaks.
 * - If a helper returns the same Mat object passed in (no conversion), we don't close the original here.
 */
public class TemplateDetector {
	private static final Logger logger = LoggerFactory.getLogger(TemplateDetector.class);

	private final TemplateCache templateCache;
	private final ConfigManager configManager;
	private final Map<String, Rectangle> lastKnownLocations = new HashMap<>();

	public TemplateDetector(TemplateCache templateCache, ConfigManager configManager) {
		this.templateCache = templateCache;
		this.configManager = configManager;
	}

	public DetectionResult detectTemplate(Mat screen, String templateName) {
		Mat template = templateCache.getTemplate(templateName);
		if (template == null) {
			logger.warn("Template not found in cache: {}", templateName);
			return DetectionResult.notFound(templateName);
		}

		// First, try searching in the last known location
		if (lastKnownLocations.containsKey(templateName)) {
			Rectangle lastRoi = lastKnownLocations.get(templateName);
			// Add some padding to the ROI to allow for small movements
			Rectangle searchRoi = new Rectangle(lastRoi.x - 10, lastRoi.y - 10, lastRoi.width + 20, lastRoi.height + 20);

			DetectionResult result = detectTemplateInRegion(screen, templateName, searchRoi);
			if (result.found) {
				lastKnownLocations.put(templateName, result.boundingBox);
				return result;
			}
		}

		// If not found in the last known location, or if there is no last known location, search the whole screen
		double threshold = getThresholdForTemplate(templateName);
		DetectionResult result = findBestMatch(screen, template, templateName, threshold);
		if (result.found) {
			lastKnownLocations.put(templateName, result.boundingBox);
		}
		return result;
	}

	public DetectionResult detectTemplateInRegion(Mat screen, String templateName, Rectangle roi) {
		Mat template = templateCache.getTemplate(templateName);
		if (template == null) {
			logger.warn("Template not found in cache: {}", templateName);
			return DetectionResult.notFound(templateName);
		}

		// Validate ROI bounds (avoid exceptions)
		if (roi.x < 0 || roi.y < 0 || roi.width <= 0 || roi.height <= 0
				|| roi.x + roi.width > screen.cols() || roi.y + roi.height > screen.rows()) {
			logger.warn("Requested ROI is out-of-bounds: {} on screen size {}x{}", roi, screen.cols(), screen.rows());
			return DetectionResult.notFound(templateName);
		}

		// Extract ROI from screen
		Rect roiRect = new Rect(roi.x, roi.y, roi.width, roi.height);
		Mat roiMat = new Mat(screen, roiRect);

		try {
			double threshold = getThresholdForTemplate(templateName);
			DetectionResult result = findBestMatch(roiMat, template, templateName, threshold);

			// Adjust coordinates back to screen space
			if (result.found) {
				result.location.x += roi.x;
				result.location.y += roi.y;
				result.boundingBox.x += roi.x;
				result.boundingBox.y += roi.y;
			}

			return result;
		} finally {
			roiMat.close();
		}
	}

	private double getThresholdForTemplate(String templateName) {
		AbilityConfig.AbilityData abilityData = configManager.getAbilities().getAbility(templateName);
		if (abilityData != null && abilityData.getDetectionThreshold() != null) {
			return abilityData.getDetectionThreshold();
		}
		return 0.99; // Default high threshold
	}

	/**
	 * Find best match for the template inside the screen Mat.
	 *
	 * @param screen       source image (BGR or BGRA)
	 * @param template     template image (BGR or BGRA) - if template has alpha channel, alpha will be used as mask
	 * @param templateName template identifier (for results)
	 * @param threshold    required confidence in range [0..1]
	 * @return DetectionResult
	 */
	private DetectionResult findBestMatch(Mat screen, Mat template, String templateName, double threshold) {
		Mat mask = null;
		Mat workingTemplate = template;
		Mat workingScreen = null;
		boolean workingTemplateCreated = false;

		// Basic size check: template must fit in screen (otherwise matchTemplate will fail)
		if (template.cols() > screen.cols() || template.rows() > screen.rows()) {
			logger.debug("Template {} is larger than screen; skipping match", templateName);
			return DetectionResult.notFound(templateName);
		}

		try {
			// Handle alpha channel as mask
			if (template.channels() == 4) {
				// Split into channels; channels[3] is alpha
				MatVector channels = new MatVector(4);
				split(template, channels);

				// alpha channel -> mask
				mask = channels.get(3).clone(); // clone so we can manage lifecycle independently

				// Merge BGR channels back into workingTemplate
				MatVector bgrChannels = new MatVector(3);
				bgrChannels.put(0, channels.get(0));
				bgrChannels.put(1, channels.get(1));
				bgrChannels.put(2, channels.get(2));

				workingTemplate = new Mat();
				merge(bgrChannels, workingTemplate);
				workingTemplateCreated = true;

				// Close channel Mats returned by split (we cloned mask and merged BGR into workingTemplate)
				for (int i = 0; i < channels.size(); i++) {
					Mat c = channels.get(i);
					if (c != null) c.close();
				}
			} else {
				// If template has no alpha, ensure mask remains null
				mask = null;
			}

			// Ensure color consistency: if screen has alpha and template is 3-channel, convert screen to BGR.
			workingScreen = ensureColorConsistency(screen, workingTemplate); // may return a new Mat or the original

			// Prepare result Mat (float) with correct size
			int resultCols = workingScreen.cols() - workingTemplate.cols() + 1;
			int resultRows = workingScreen.rows() - workingTemplate.rows() + 1;
			if (resultCols <= 0 || resultRows <= 0) {
				// template cannot be matched inside screen (shouldn't happen due to size check above, but safe-guard)
				logger.debug("Computed result size invalid ({}x{}); skipping template {}", resultCols, resultRows, templateName);
				return DetectionResult.notFound(templateName);
			}
			Mat result = new Mat(new org.bytedeco.opencv.opencv_core.Size(resultCols, resultRows), CV_32FC1);

			try {
				// Perform template matching with SQDIFF_NORMED (smaller = better). Use mask when provided.
				matchTemplate(workingScreen, workingTemplate, result, TM_SQDIFF_NORMED, mask);

				// minMaxLoc to find best match
				DoublePointer minVal = new DoublePointer(1);
				DoublePointer maxVal = new DoublePointer(1);
				Point minLoc = new Point();
				Point maxLoc = new Point();

				minMaxLoc(result, minVal, maxVal, minLoc, maxLoc, null);

				double minValD = minVal.get();
				// Convert to intuitive confidence: lower error -> higher confidence
				double confidence = 1.0 - minValD;
				// Found if confidence meets threshold
				boolean found = confidence >= threshold;

				if (found) {
					Rectangle boundingBox = new Rectangle(
							minLoc.x(), minLoc.y(),
							workingTemplate.cols(), workingTemplate.rows()
					);

					return DetectionResult.found(templateName,
							new java.awt.Point(minLoc.x(), minLoc.y()),
							confidence, boundingBox);
				} else {
					return DetectionResult.notFound(templateName);
				}
			} finally {
				// free result
				result.close();
			}
		} finally {
			// Cleanup created Mats
			if (mask != null) mask.close();
			if (workingTemplateCreated && workingTemplate != null) workingTemplate.close();
			// If ensureColorConsistency returned a different Mat than the original screen, close it
			if (workingScreen != null && workingScreen != screen) {
				workingScreen.close();
			}
		}
	}

	/**
	 * Ensure screen colors align with template:
	 * - If screen has 4 channels (BGRA) and template is 3-channel (BGR), convert screen to BGR and return a new Mat (caller must close).
	 * - Otherwise return the original screen Mat.
	 * <p>
	 * Important: this method may allocate a new Mat. Caller must check identity and close the returned mat
	 * when it is not the same as the input screen (we follow lifetime management in findBestMatch).
	 */
	private Mat ensureColorConsistency(Mat screen, Mat template) {
		if (screen.channels() == 4 && template.channels() == 3) {
			Mat converted = new Mat();
			cvtColor(screen, converted, COLOR_BGRA2BGR);
			return converted;
		}
		// return original reference (caller must NOT close it)
		return screen;
	}
}
