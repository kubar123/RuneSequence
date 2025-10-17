package com.lansoftprogramming.runeSequence.core.image;

import java.nio.file.Path;
import java.io.IOException;

public interface ImageProcessor {
	void processImages(Path abilitiesRoot, int[] sizes) throws IOException;
}