package com.lansoftprogramming.runeSequence.config;

import java.util.HashMap;
import java.util.Map;

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
}
