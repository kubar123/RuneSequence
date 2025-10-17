package com.lansoftprogramming.runeSequence.infrastructure.config;

import java.util.HashMap;
import java.util.Map;
import java.util.Set;

public class ScalingConverter {

	// Create the scaling map
	private static final Map<Integer, Integer> scalingMap = new HashMap<>();

	static {
		scalingMap.put(100, 30);
		scalingMap.put(110, 34);
		scalingMap.put(120, 36);
		scalingMap.put(135, 42);
		scalingMap.put(150, 45);
		scalingMap.put(175, 52);
		scalingMap.put(200, 60);
		// You can add more if needed
	}

	// Method to convert input to output
	public static Integer getScaling(int input) {
		return scalingMap.get(input);
	}

	public static int[] getAllSizes() {
		return scalingMap.values().stream()
				.mapToInt(Integer::intValue)
				.sorted()
				.toArray();
	}

	public static Set<Integer> getAllScalePercentages() {
		return scalingMap.keySet();
	}
}