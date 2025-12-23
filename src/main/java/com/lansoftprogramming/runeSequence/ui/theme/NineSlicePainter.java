package com.lansoftprogramming.runeSequence.ui.theme;

import java.awt.*;
import java.awt.image.BufferedImage;

final class NineSlicePainter {

	private NineSlicePainter() {
	}

	static void paint(Graphics2D g, BufferedImage src, NineSliceSpec spec, int x, int y, int width, int height) {
		if (g == null || src == null || spec == null) {
			return;
		}
		if (width <= 0 || height <= 0) {
			return;
		}

		int w = src.getWidth();
		int h = src.getHeight();

		int L = spec.left();
		int R = spec.right();
		int T = spec.top();
		int B = spec.bottom();

		int left = Math.min(L, width);
		int top = Math.min(T, height);
		int right = Math.min(R, Math.max(0, width - left));
		int bottom = Math.min(B, Math.max(0, height - top));

		int centerW = Math.max(0, width - left - right);
		int centerH = Math.max(0, height - top - bottom);

		int dx0 = x;
		int dx1 = x + left;
		int dx2 = dx1 + centerW;
		int dx3 = dx2 + right;

		int dy0 = y;
		int dy1 = y + top;
		int dy2 = dy1 + centerH;
		int dy3 = dy2 + bottom;

		int sx0 = 0;
		int sx1 = L;
		int sx2 = w - R;
		int sx3 = w;

		int sy0 = 0;
		int sy1 = T;
		int sy2 = h - B;
		int sy3 = h;

		// 1. Top-left corner
		g.drawImage(src, dx0, dy0, dx1, dy1, sx0, sy0, sx1, sy1, null);
		// 2. Top edge
		g.drawImage(src, dx1, dy0, dx2, dy1, sx1, sy0, sx2, sy1, null);
		// 3. Top-right corner
		g.drawImage(src, dx2, dy0, dx3, dy1, sx2, sy0, sx3, sy1, null);
		// 4. Left edge
		g.drawImage(src, dx0, dy1, dx1, dy2, sx0, sy1, sx1, sy2, null);
		// 5. Center
		g.drawImage(src, dx1, dy1, dx2, dy2, sx1, sy1, sx2, sy2, null);
		// 6. Right edge
		g.drawImage(src, dx2, dy1, dx3, dy2, sx2, sy1, sx3, sy2, null);
		// 7. Bottom-left corner
		g.drawImage(src, dx0, dy2, dx1, dy3, sx0, sy2, sx1, sy3, null);
		// 8. Bottom edge
		g.drawImage(src, dx1, dy2, dx2, dy3, sx1, sy2, sx2, sy3, null);
		// 9. Bottom-right corner
		g.drawImage(src, dx2, dy2, dx3, dy3, sx2, sy2, sx3, sy3, null);
	}
}