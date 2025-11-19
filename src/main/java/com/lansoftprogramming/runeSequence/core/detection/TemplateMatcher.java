package com.lansoftprogramming.runeSequence.core.detection;

import org.bytedeco.opencv.opencv_core.Mat;

import java.util.Collection;
import java.util.Map;

/**
 * Abstraction over template matching so we can plug in
 * either a CPU-based or GPU-based implementation.
 */
public interface TemplateMatcher {
	DetectionResult detectTemplate(Mat screen, String templateName);

	DetectionResult detectTemplate(Mat screen, String templateName, boolean isAlternative);

	Map<String, DetectionResult> cacheAbilityLocations(Mat screen, Collection<String> abilityKeys);
}

