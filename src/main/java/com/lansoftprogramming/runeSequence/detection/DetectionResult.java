package com.lansoftprogramming.runeSequence.detection;

import java.awt.*;

public class DetectionResult {
	public final String templateName;
	public final Point location;
	public final double confidence;
	public final Rectangle boundingBox;
	public final boolean found;

	private DetectionResult(String templateName, Point location, double confidence,
	                        Rectangle boundingBox, boolean found) {
		this.templateName = templateName;
		this.location = location;
		this.confidence = confidence;
		this.boundingBox = boundingBox;
		this.found = found;
	}

	/*++*/
	public static DetectionResult found(String templateName, Point location,
	                                    double confidence, Rectangle boundingBox) {
		return new DetectionResult(templateName, location, confidence, boundingBox, true);
	}

	/*++*/
	public static DetectionResult notFound(String templateName) {
		return new DetectionResult(templateName, null, 0.0, null, false);
	}
}