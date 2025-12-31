package com.lansoftprogramming.runeSequence.ui.theme;

import javax.swing.*;
import java.awt.*;
import java.awt.image.BufferedImage;
import java.util.Objects;

public final class ThemedBackgroundPainter {

	@FunctionalInterface
	public interface BackgroundFill {
		void paint(Graphics2D graphics, BufferedImage image, Rectangle target);
	}

	private ThemedBackgroundPainter() {
	}

	public static void paintBackground(Graphics graphics, JComponent component, BufferedImage image, BackgroundFill backgroundFill) {
		Objects.requireNonNull(component, "component");
		Objects.requireNonNull(backgroundFill, "backgroundFill");
		if (graphics == null || image == null) {
			return;
		}

		int width = component.getWidth();
		int height = component.getHeight();
		if (width <= 0 || height <= 0) {
			return;
		}

		Graphics2D g2 = graphics instanceof Graphics2D ? (Graphics2D) graphics.create() : null;
		if (g2 == null) {
			return;
		}

		try {
			g2.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BILINEAR);
			Insets insets = component.getInsets();
			int innerX = insets.left;
			int innerY = insets.top;
			int innerWidth = width - insets.left - insets.right;
			int innerHeight = height - insets.top - insets.bottom;
			Rectangle innerRect = new Rectangle(innerX, innerY, innerWidth, innerHeight);
			backgroundFill.paint(g2, image, innerRect);
		} finally {
			g2.dispose();
		}
	}
}
