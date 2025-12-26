package com.lansoftprogramming.runeSequence.ui.theme;

import java.awt.*;
import java.awt.image.BufferedImage;

public final class BackgroundFillPainter {

	private BackgroundFillPainter() {
	}

	public static void paintCenterCropScale(Graphics2D g2, BufferedImage image, Rectangle target) {
		paintCropScale(g2, image, target, 0.5f, 0.5f);
	}

	public static void paintTopLeftCropScale(Graphics2D g2, BufferedImage image, Rectangle target) {
		paintCropScale(g2, image, target, 0f, 0f);
	}

	private static void paintCropScale(Graphics2D g2, BufferedImage image, Rectangle target, float anchorX, float anchorY) {
		if (g2 == null || image == null || target == null) {
			return;
		}
		int width = target.width;
		int height = target.height;
		if (width <= 0 || height <= 0) {
			return;
		}

		int imageWidth = image.getWidth();
		int imageHeight = image.getHeight();
		if (imageWidth <= 0 || imageHeight <= 0) {
			return;
		}

		int dx1 = target.x;
		int dy1 = target.y;
		int dx2 = target.x + width;
		int dy2 = target.y + height;

		float clampedAnchorX = Math.max(0f, Math.min(1f, anchorX));
		float clampedAnchorY = Math.max(0f, Math.min(1f, anchorY));

		int sx1;
		int sy1;
		int sx2;
		int sy2;

		if (width <= imageWidth) {
			sx1 = (int) ((imageWidth - width) * clampedAnchorX);
			sx2 = sx1 + width;
		} else {
			sx1 = 0;
			sx2 = imageWidth;
		}

		if (height <= imageHeight) {
			sy1 = (int) ((imageHeight - height) * clampedAnchorY);
			sy2 = sy1 + height;
		} else {
			sy1 = 0;
			sy2 = imageHeight;
		}

		sx1 = Math.max(0, Math.min(sx1, imageWidth));
		sx2 = Math.max(sx1, Math.min(sx2, imageWidth));
		sy1 = Math.max(0, Math.min(sy1, imageHeight));
		sy2 = Math.max(sy1, Math.min(sy2, imageHeight));

		g2.drawImage(image, dx1, dy1, dx2, dy2, sx1, sy1, sx2, sy2, null);
	}
}
