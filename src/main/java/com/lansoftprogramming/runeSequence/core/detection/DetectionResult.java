package com.lansoftprogramming.runeSequence.core.detection;

import java.awt.*;

public class DetectionResult {
	public final String templateName;
	public final Point location;
	public final double confidence;
	public final Rectangle boundingBox;
	public final boolean found;
	public final boolean isAlternative;

	private DetectionResult(String templateName, Point location, double confidence,
	                        Rectangle boundingBox, boolean found,boolean isAlternative) {
		this.templateName = templateName;
		this.location = location;
		this.confidence = confidence;
		this.boundingBox = boundingBox;
		this.found = found;
		this.isAlternative = isAlternative;
	}


	public static DetectionResult found(String templateName, Point location,
	                                    double confidence, Rectangle boundingBox,boolean isAlternative) {
		return new DetectionResult(templateName, location, confidence, boundingBox, true,isAlternative);
	}


	/**
	 * Create a not-found DetectionResult with default isAlternative = false for backward compatibility.
	 */
	public static DetectionResult notFound(String templateName) {
		return notFound(templateName, false);
	}

	/**
	 * Create a not-found DetectionResult and allow specifying whether it is part of an alternative (OR).
	 */
	public static DetectionResult notFound(String templateName, boolean isAlternative) {
		return new DetectionResult(templateName, null, 0.0, null, false, isAlternative);
	}

	/**
	 * Create a not-found DetectionResult that still records the best-match location/bounds and confidence.
	 * Useful for diagnostics/logging when a match exists but does not meet the required threshold.
	 */
	public static DetectionResult notFound(String templateName, Point bestLocation, double bestConfidence,
	                                      Rectangle bestBoundingBox, boolean isAlternative) {
		return new DetectionResult(templateName, bestLocation, bestConfidence, bestBoundingBox, false, isAlternative);
	}
}
