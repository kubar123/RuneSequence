package com.lansoftprogramming.runeSequence.ui.theme;

import java.awt.*;
import java.awt.image.BufferedImage;

public final class BackgroundFillPainter {

	private BackgroundFillPainter() {
	}

	public static void paintCenterCropScale(Graphics2D g2, BufferedImage image, Rectangle target) {
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

		int sx1;
		int sy1;
		int sx2;
		int sy2;

		if (width <= imageWidth && height <= imageHeight) {
			sx1 = (imageWidth - width) / 2;
			sy1 = (imageHeight - height) / 2;
			sx2 = sx1 + width;
			sy2 = sy1 + height;
		} else if (width > imageWidth && height <= imageHeight) {
			sx1 = 0;
			sx2 = imageWidth;
			sy1 = (imageHeight - height) / 2;
			sy2 = sy1 + height;
		} else if (width <= imageWidth && height > imageHeight) {
			sx1 = (imageWidth - width) / 2;
			sx2 = sx1 + width;
			sy1 = 0;
			sy2 = imageHeight;
		} else {
			sx1 = 0;
			sy1 = 0;
			sx2 = imageWidth;
			sy2 = imageHeight;
		}

		g2.drawImage(image, dx1, dy1, dx2, dy2, sx1, sy1, sx2, sy2, null);
	}
}
