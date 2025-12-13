package com.lansoftprogramming.runeSequence.core.sequence.model;

public final class AbilityValueSanitizers {
	private AbilityValueSanitizers() {
	}

	public static Double sanitizeDetectionThreshold(Double value) {
		if (value == null) {
			return null;
		}
		if (value.isNaN() || value.isInfinite()) {
			return null;
		}
		return clamp(value, 0.0d, 1.0d);
	}

	public static double clampFiniteOrDefault(double value, double min, double max, double defaultValue) {
		if (Double.isNaN(value) || Double.isInfinite(value)) {
			return defaultValue;
		}
		return clamp(value, min, max);
	}

	private static double clamp(double value, double min, double max) {
		return Math.max(min, Math.min(max, value));
	}
}

