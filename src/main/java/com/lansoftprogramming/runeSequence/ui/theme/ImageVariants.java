package com.lansoftprogramming.runeSequence.ui.theme;

import java.awt.*;
import java.awt.image.BufferedImage;

final class ImageVariants {

	private ImageVariants() {
	}

	static BufferedImage applyOverlay(BufferedImage source, Color overlay, float alpha) {
		if (source == null) {
			throw new IllegalArgumentException("source is required.");
		}
		if (overlay == null) {
			throw new IllegalArgumentException("overlay is required.");
		}
		if (alpha <= 0f) {
			return source;
		}
		float clampedAlpha = Math.min(1f, alpha);

		int width = source.getWidth();
		int height = source.getHeight();
		BufferedImage out = new BufferedImage(width, height, BufferedImage.TYPE_INT_ARGB);

		int overlayRgb = overlay.getRGB();
		int or = (overlayRgb >> 16) & 0xFF;
		int og = (overlayRgb >> 8) & 0xFF;
		int ob = overlayRgb & 0xFF;

		for (int y = 0; y < height; y++) {
			for (int x = 0; x < width; x++) {
				int argb = source.getRGB(x, y);
				int a = (argb >> 24) & 0xFF;
				if (a == 0) {
					out.setRGB(x, y, 0);
					continue;
				}

				int r = (argb >> 16) & 0xFF;
				int g = (argb >> 8) & 0xFF;
				int b = argb & 0xFF;

				int nr = blendChannel(r, or, clampedAlpha);
				int ng = blendChannel(g, og, clampedAlpha);
				int nb = blendChannel(b, ob, clampedAlpha);
				out.setRGB(x, y, (a << 24) | (nr << 16) | (ng << 8) | nb);
			}
		}

		return out;
	}

	static BufferedImage toDisabled(BufferedImage source, float disabledAlpha) {
		if (source == null) {
			throw new IllegalArgumentException("source is required.");
		}
		float alphaMultiplier = Math.max(0f, Math.min(1f, disabledAlpha));

		int width = source.getWidth();
		int height = source.getHeight();
		BufferedImage out = new BufferedImage(width, height, BufferedImage.TYPE_INT_ARGB);

		for (int y = 0; y < height; y++) {
			for (int x = 0; x < width; x++) {
				int argb = source.getRGB(x, y);
				int a = (argb >> 24) & 0xFF;
				if (a == 0) {
					out.setRGB(x, y, 0);
					continue;
				}

				int r = (argb >> 16) & 0xFF;
				int g = (argb >> 8) & 0xFF;
				int b = argb & 0xFF;

				int gray = (int) (0.299f * r + 0.587f * g + 0.114f * b);
				int na = (int) Math.round(a * alphaMultiplier);
				out.setRGB(x, y, (na << 24) | (gray << 16) | (gray << 8) | gray);
			}
		}

		return out;
	}

	private static int blendChannel(int base, int overlay, float alpha) {
		return clampToByte(Math.round(base * (1f - alpha) + overlay * alpha));
	}

	private static int clampToByte(int value) {
		return Math.max(0, Math.min(255, value));
	}
}