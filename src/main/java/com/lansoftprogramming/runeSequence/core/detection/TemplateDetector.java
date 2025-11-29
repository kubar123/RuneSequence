package com.lansoftprogramming.runeSequence.core.detection;

import com.lansoftprogramming.runeSequence.application.TemplateCache;
import com.lansoftprogramming.runeSequence.infrastructure.config.AbilityConfig;
import org.bytedeco.javacpp.DoublePointer;
import org.bytedeco.opencv.opencv_core.Mat;
import org.bytedeco.opencv.opencv_core.MatVector;
import org.bytedeco.opencv.opencv_core.Point;
import org.bytedeco.opencv.opencv_core.Rect;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.awt.*;
import java.util.Collection;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.Map;

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
	private final AbilityConfig abilityConfig;
	private final Map<String, Rectangle> lastKnownLocations = new java.util.concurrent.ConcurrentHashMap<>();

	public TemplateDetector(TemplateCache templateCache, AbilityConfig abilityConfig) {
		this.templateCache = templateCache;
		this.abilityConfig = abilityConfig;
	}

	public DetectionResult detectTemplate(Mat screen, String templateName) {
		// Backwards-compatible entrypoint: default isAlternative to false
		return detectTemplate(screen, templateName, false);
	}

	public Map<String, DetectionResult> cacheAbilityLocations(Mat screen, Collection<String> abilityKeys) {
		if (screen == null || screen.empty() || abilityKeys == null || abilityKeys.isEmpty()) {
			return Collections.emptyMap();
		}

		LinkedHashSet<String> uniqueKeys = new LinkedHashSet<>(abilityKeys);
		Map<String, DetectionResult> results = new java.util.concurrent.ConcurrentHashMap<>();

		uniqueKeys.parallelStream().forEach(abilityKey -> {
			if (abilityKey == null || abilityKey.isEmpty()) {
				return;
			}
			if (!templateCache.hasTemplate(abilityKey)) {
				logger.debug("Skipping pre-cache for {} because no template is loaded", abilityKey);
				return;
			}
			if (lastKnownLocations.containsKey(abilityKey)) {
				return;
			}

			DetectionResult result = detectTemplate(screen, abilityKey);
			results.put(abilityKey, result);
		});

		return results;
	}

	/**
	 * New overload that accepts isAlternative which will be propagated into DetectionResult.
	 */
	public DetectionResult detectTemplate(Mat screen, String templateName, boolean isAlternative) {
		Mat template = templateCache.getTemplate(templateName);
		if (template == null) {
			logger.error("Template not found in cache: {}", templateName);

			System.out.println("TemplateDetector: Template not found in cache: " + templateName);
			return DetectionResult.notFound(templateName);
		}

		// First, try searching in the last known location
		if (lastKnownLocations.containsKey(templateName)) {
			Rectangle lastRoi = lastKnownLocations.get(templateName);
			// Add some padding to the ROI to allow for small movements
			Rectangle searchRoi = new Rectangle(lastRoi.x - 10, lastRoi.y - 10, lastRoi.width + 20, lastRoi.height + 20);

			DetectionResult result = detectTemplateInRegion(screen, templateName, searchRoi, isAlternative);
			if (result.found) {
				lastKnownLocations.put(templateName, result.boundingBox);
				return result;
			}
		}

		// If not found in the last known location, or if there is no last known location, search the whole screen
		double threshold = getThresholdForTemplate(templateName);
		DetectionResult result = findBestMatch(screen, template, templateName, threshold, isAlternative);
		if (result.found) {
			lastKnownLocations.put(templateName, result.boundingBox);
		}
		return result;
	}

	/**
	 * Expose the cached last-known bounding box for a template, if available.
	 * Returned rectangles are defensive copies so callers cannot mutate the cache.
	 */
	public Rectangle getCachedLocation(String templateName) {
		Rectangle cached = lastKnownLocations.get(templateName);
		return cached != null ? new Rectangle(cached) : null;
	}

	/**
	 * Update (or seed) the cached last-known bounding box for a template.
	 * A defensive copy is stored to keep the cache immutable from outside callers.
	 */
	public void updateCachedLocation(String templateName, Rectangle boundingBox) {
		if (templateName == null || boundingBox == null) {
			return;
		}
		lastKnownLocations.put(templateName, new Rectangle(boundingBox));
	}

	public DetectionResult detectTemplateInRegion(Mat screen, String templateName, Rectangle roi) {
		// Backwards-compatible entrypoint: default isAlternative to false
		return detectTemplateInRegion(screen, templateName, roi, false);
	}

	/**
	 * New overload that accepts isAlternative which will be propagated into DetectionResult.
	 */
	public DetectionResult detectTemplateInRegion(Mat screen, String templateName, Rectangle roi, boolean isAlternative) {
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
			DetectionResult result = findBestMatch(roiMat, template, templateName, threshold, isAlternative);

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

	/**
	 * Resolve a stable ROI for an ability key, using cached location if valid or falling back to template detection.
	 * The returned rectangle is guaranteed to fit inside the given frame or {@code null} is returned.
	 */
	public Rectangle resolveAbilityRoi(Mat frame, String abilityKey) {
		if (frame == null || frame.empty() || abilityKey == null) {
			return null;
		}

		Rectangle cached = getCachedLocation(abilityKey);
		if (cached != null && isValidRoi(cached, frame)) {
			return cached;
		}

		DetectionResult detection = detectTemplate(frame, abilityKey, false);
		if (detection != null && detection.found && detection.boundingBox != null && isValidRoi(detection.boundingBox, frame)) {
			updateCachedLocation(abilityKey, detection.boundingBox);
			return new Rectangle(detection.boundingBox);
		}

		return null;
	}

	/**
	 * Measure mean brightness of the given ROI in the frame as a grayscale value.
	 * Returns a negative value if sampling fails.
	 */
	public double measureBrightness(Mat frame, Rectangle roi) {
		if (frame == null || frame.empty() || roi == null) {
			return -1;
		}

		Rectangle clamped = clampToFrame(roi, frame);
		if (clamped == null) {
			return -1;
		}

		Rect rect = new Rect(clamped.x, clamped.y, clamped.width, clamped.height);
		Mat roiMat = null;
		Mat gray = null;
		try {
			roiMat = new Mat(frame, rect);
			gray = new Mat();
			int channels = roiMat.channels();
			if (channels == 4) {
				cvtColor(roiMat, gray, COLOR_BGRA2GRAY);
			} else {
				cvtColor(roiMat, gray, COLOR_BGR2GRAY);
			}
			return mean(gray).get(0);
		} catch (Exception e) {
			logger.warn("Failed to sample brightness for ROI {}", clamped, e);
			return -1;
		} finally {
			if (gray != null) {
				gray.close();
			}
			if (roiMat != null) {
				roiMat.close();
			}
		}
	}

	private boolean isValidRoi(Rectangle roi, Mat frame) {
		if (roi == null || frame == null || frame.empty()) {
			return false;
		}
		if (roi.width <= 1 || roi.height <= 1) {
			return false;
		}
		int maxX = frame.cols();
		int maxY = frame.rows();
		return roi.x >= 0 && roi.y >= 0 && roi.x + roi.width <= maxX && roi.y + roi.height <= maxY;
	}

	private Rectangle clampToFrame(Rectangle roi, Mat frame) {
		if (roi == null || frame == null || frame.empty()) {
			return null;
		}
		int maxX = frame.cols();
		int maxY = frame.rows();
		int x = Math.max(0, roi.x);
		int y = Math.max(0, roi.y);
		int w = Math.min(roi.width, maxX - x);
		int h = Math.min(roi.height, maxY - y);
		if (w <= 1 || h <= 1) {
			return null;
		}
		return new Rectangle(x, y, w, h);
	}

	private double getThresholdForTemplate(String templateName) {
		AbilityConfig.AbilityData abilityData = abilityConfig.getAbility(templateName);
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
	private DetectionResult findBestMatch(Mat screen, Mat template, String templateName, double threshold,boolean isAlternative) {
		// Basic size check: template must fit in screen (otherwise matchTemplate will fail)
		if (template.cols() > screen.cols() || template.rows() > screen.rows()) {
			logger.debug("Template {} is larger than screen; skipping match", templateName);
			return DetectionResult.notFound(templateName);
		}

		// Improved resource management with explicit cleanup
		Mat mask = null;
		Mat workingTemplate = null;
		Mat workingScreen = null;
		MatVector channels = null;
		MatVector bgrChannels = null;
		Mat result = null;
		DoublePointer minVal = null;
		DoublePointer maxVal = null;
		Point minLoc = null;
		Point maxLoc = null;

		try {
			// Handle alpha channel as mask
			if (template.channels() == 4) {
				// More careful channel management
				channels = new MatVector(4);
				split(template, channels);

				// alpha channel -> mask
				mask = channels.get(3).clone(); // clone so we can manage lifecycle independently

				// Merge BGR channels back into workingTemplate
				bgrChannels = new MatVector(3);
				bgrChannels.put(0, channels.get(0));
				bgrChannels.put(1, channels.get(1));
				bgrChannels.put(2, channels.get(2));

				workingTemplate = new Mat();
				merge(bgrChannels, workingTemplate);
			} else {
				// If template has no alpha, use original template
				workingTemplate = template;
				mask = null;
			}

			// Ensure color consistency
			workingScreen = ensureColorConsistency(screen, workingTemplate);

			// Prepare result Mat (float) with correct size
			int resultCols = workingScreen.cols() - workingTemplate.cols() + 1;
			int resultRows = workingScreen.rows() - workingTemplate.rows() + 1;
			if (resultCols <= 0 || resultRows <= 0) {
				logger.debug("Computed result size invalid ({}x{}); skipping template {}", resultCols, resultRows, templateName);
				return DetectionResult.notFound(templateName);
			}

			result = new Mat(new org.bytedeco.opencv.opencv_core.Size(resultCols, resultRows), CV_32FC1);

			// Perform template matching with SQDIFF_NORMED (smaller = better)
			matchTemplate(workingScreen, workingTemplate, result, TM_SQDIFF_NORMED, mask);

			// minMaxLoc to find best match
			minVal = new DoublePointer(1);
			maxVal = new DoublePointer(1);
			minLoc = new Point();
			maxLoc = new Point();

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
						confidence, boundingBox,isAlternative);
			} else {
				return DetectionResult.notFound(templateName);
			}

		} catch (Exception e) {

			System.err.println("TemplateDetector.findBestMatch ERROR for " + templateName + ": " + e.getMessage());

			e.printStackTrace();

			logger.error("Template matching failed for {}", templateName, e);

			return DetectionResult.notFound(templateName);
		} finally {
			// Comprehensive cleanup - close all created resources

			if (mask != null) mask.close();

			if (workingTemplate != null && workingTemplate != template) workingTemplate.close();

			if (workingScreen != null && workingScreen != screen) workingScreen.close();

			if (result != null) result.close();

			if (minVal != null) minVal.close();

			if (maxVal != null) maxVal.close();

			if (minLoc != null) minLoc.close();

			if (maxLoc != null) maxLoc.close();

			// Close channel vectors (MatVector releases contained Mats)
			if (channels != null) {
				channels.close();
			}

			if (bgrChannels != null) {
				bgrChannels.close();
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
