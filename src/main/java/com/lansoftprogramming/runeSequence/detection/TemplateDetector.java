package com.lansoftprogramming.runeSequence.detection;

import com.lansoftprogramming.runeSequence.config.ConfigManager;
import org.bytedeco.javacpp.DoublePointer;
import org.bytedeco.opencv.global.opencv_imgproc;
import org.bytedeco.opencv.opencv_core.Mat;
import org.bytedeco.opencv.opencv_core.MatVector;
import org.bytedeco.opencv.opencv_core.Rect;
import org.slf4j.LoggerFactory;

import java.awt.*;
import java.util.logging.Logger;

public class TemplateDetector {
	private static final Logger logger = LoggerFactory.getLogger(TemplateDetector.class);

	private final ImageCache imageCache;
	private final double defaultThreshold;

	public TemplateDetector(ImageCache imageCache, ConfigManager configManager) {
		this.imageCache = imageCache;
		this.defaultThreshold = configManager.getConfidenceThreshold();
	}


	public DetectionResult detectTemplate(Mat screen, String templateName) {
		Mat template = imageCache.getTemplate(templateName);
		if (template == null) {
			logger.warn("Template not found in cache: {}", templateName);
			return DetectionResult.notFound(templateName);
		}

		return findBestMatch(screen, template, templateName, defaultThreshold);
	}


	public DetectionResult detectTemplateInRegion(Mat screen, String templateName, Rectangle roi) {
		Mat template = imageCache.getTemplate(templateName);
		if (template == null) {
			return DetectionResult.notFound(templateName);
		}

		// Extract ROI from screen
		Rect roiRect = new Rect(roi.x, roi.y, roi.width, roi.height);
		Mat roiMat = new Mat(screen, roiRect);

		try {
			DetectionResult result = findBestMatch(roiMat, template, templateName, defaultThreshold);

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


	private DetectionResult findBestMatch(Mat screen, Mat template, String templateName, double threshold) {
		Mat mask = null;
		Mat workingTemplate = template;

		try {
			// Handle alpha channel as mask
			if (template.channels() == 4) {
				MatVector channels = new MatVector(4);
				opencv_imgproc.split(template, channels);

				// Use alpha channel as mask
				mask = channels.get(3);

				// Merge BGR channels back
				MatVector bgrChannels = new MatVector(3);
				bgrChannels.put(0, channels.get(0));
				bgrChannels.put(1, channels.get(1));
				bgrChannels.put(2, channels.get(2));

				workingTemplate = new Mat();
				opencv_imgproc.merge(bgrChannels, workingTemplate);
			}

			// Ensure color consistency
			Mat workingScreen = ensureColorConsistency(screen, workingTemplate);

			// Perform template matching
			Mat result = new Mat();
			opencv_imgproc.matchTemplate(workingScreen, workingTemplate, result,
					opencv_imgproc.TM_SQDIFF_NORMED, mask);

			// Find best match
			DoublePointer minVal = new DoublePointer(1);
			DoublePointer maxVal = new DoublePointer(1);
			Point minLoc = new Point();
			Point maxLoc = new Point();

			opencv_imgproc.minMaxLoc(result, minVal, maxVal, minLoc, maxLoc, null);

			double confidence = 1.0 - minVal.get(); // Convert SQDIFF_NORMED to confidence
			boolean found = minVal.get() <= (1.0 - threshold);

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
			// Cleanup
			if (mask != null) mask.close();
			if (workingTemplate != template) workingTemplate.close();
		}
	}


	private Mat ensureColorConsistency(Mat screen, Mat template) {
		if (screen.channels() == 4 && template.channels() == 3) {
			Mat converted = new Mat();
			opencv_imgproc.cvtColor(screen, converted, opencv_imgproc.COLOR_BGRA2BGR);
			return converted;
		}
		return screen; // Return original if no conversion needed
	}
}
