package com.lansoftprogramming.runeSequence.core.image;

import com.lansoftprogramming.runeSequence.infrastructure.config.AbilityConfig;

import java.io.IOException;
import java.nio.file.Path;

public interface ImageProcessor {
	void processImages(Path abilitiesRoot, int[] sizes, AbilityConfig abilityConfig) throws IOException;
}