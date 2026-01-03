package com.lansoftprogramming.runeSequence.core.detection;

public final class IconDetectionGrader {
	private static final double EPSILON = 1e-12d;
	public static final double DEFAULT_YELLOW_TOLERANCE_RATIO = 0.10d;
	public static final double DEFAULT_NOT_FOUND_TOLERANCE_RATIO = 0.20d;

	private IconDetectionGrader() {
	}

	public enum Grade {
		GREEN,
		YELLOW,
		RED,
		NOT_FOUND
	}

	public record Result(Grade grade, double deltaRatio) {
	}

	public static Result grade(double requiredThreshold, double bestConfidence) {
		return grade(requiredThreshold, bestConfidence, DEFAULT_YELLOW_TOLERANCE_RATIO, DEFAULT_NOT_FOUND_TOLERANCE_RATIO);
	}

	public static Result grade(double requiredThreshold,
	                          double bestConfidence,
	                          double yellowToleranceRatio,
	                          double notFoundToleranceRatio) {
		double threshold = requiredThreshold;
		if (!Double.isFinite(threshold) || threshold <= 0.0d) {
			threshold = 0.99d;
		}

		double best = bestConfidence;
		if (!Double.isFinite(best) || best < 0.0d) {
			best = 0.0d;
		}

		if (best >= threshold) {
			return new Result(Grade.GREEN, 0.0d);
		}

		double yellowTol = Double.isFinite(yellowToleranceRatio) && yellowToleranceRatio >= 0.0d
				? yellowToleranceRatio
				: DEFAULT_YELLOW_TOLERANCE_RATIO;
		double notFoundTol = Double.isFinite(notFoundToleranceRatio) && notFoundToleranceRatio >= 0.0d
				? notFoundToleranceRatio
				: DEFAULT_NOT_FOUND_TOLERANCE_RATIO;
		if (notFoundTol < yellowTol) {
			double swap = yellowTol;
			yellowTol = notFoundTol;
			notFoundTol = swap;
		}

		double deltaRatio = (threshold - best) / threshold;
		if (deltaRatio <= yellowTol + EPSILON) {
			return new Result(Grade.YELLOW, deltaRatio);
		}
		if (deltaRatio < notFoundTol - EPSILON) {
			return new Result(Grade.RED, deltaRatio);
		}
		return new Result(Grade.NOT_FOUND, deltaRatio);
	}
}
