package com.lansoftprogramming.runeSequence.ui.theme;

import java.awt.*;
import java.awt.image.BufferedImage;

public final class ThemeRenderers {
	private ThemeRenderers() {
	}

	public static BufferedImage applyOverlay(BufferedImage source, Color overlay, float alpha) {
		if (source == null || overlay == null) {
			return source;
		}
		if (alpha <= 0f) {
			return source;
		}
		try {
			return ImageVariants.applyOverlay(source, overlay, alpha);
		} catch (IllegalArgumentException ignored) {
			return source;
		}
	}

	public static BufferedImage toDisabled(BufferedImage source, float disabledAlpha) {
		if (source == null) {
			return null;
		}
		try {
			return ImageVariants.toDisabled(source, disabledAlpha);
		} catch (IllegalArgumentException ignored) {
			return source;
		}
	}

	public static void paintNineSlice(Graphics2D g, BufferedImage src, NineSliceSpec spec, int x, int y, int width, int height) {
		if (g == null || src == null || spec == null) {
			return;
		}
		NineSlicePainter.paint(g, src, spec, x, y, width, height);
	}
}
