package com.lansoftprogramming.runeSequence.ui.theme;

import javax.swing.border.AbstractBorder;
import java.awt.*;
import java.awt.image.BufferedImage;
import java.util.Objects;

public final class ThemedPanelBorder extends AbstractBorder {

	private final PanelStyle style;

	public ThemedPanelBorder(PanelStyle style) {
		this.style = Objects.requireNonNull(style, "style");
	}

	public PanelStyle getStyle() {
		return style;
	}

	@Override
	public Insets getBorderInsets(Component component) {
		return ThemeManager.getTheme().getPanelPadding(style);
	}

	@Override
	public Insets getBorderInsets(Component component, Insets insets) {
		Insets specInsets = getBorderInsets(component);
		insets.top = specInsets.top;
		insets.left = specInsets.left;
		insets.bottom = specInsets.bottom;
		insets.right = specInsets.right;
		return insets;
	}

	@Override
	public boolean isBorderOpaque() {
		return false;
	}

	@Override
	public void paintBorder(Component component, Graphics graphics, int x, int y, int width, int height) {
		if (width <= 0 || height <= 0) {
			return;
		}

		Theme theme = ThemeManager.getTheme();
		BufferedImage image = theme.getPanelBorderImage(style);
		if (image == null) {
			return;
		}

		NineSliceSpec spec = theme.getPanelSpec(style);
		if (spec == null) {
			return;
		}

		Graphics2D g2 = graphics instanceof Graphics2D ? (Graphics2D) graphics.create() : null;
		if (g2 == null) {
			return;
		}

		try {
			g2.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BILINEAR);
			NineSlicePainter.paint(g2, image, spec, x, y, width, height);
		} finally {
			g2.dispose();
		}
	}
}
