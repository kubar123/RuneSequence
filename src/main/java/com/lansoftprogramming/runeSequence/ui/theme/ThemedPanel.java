package com.lansoftprogramming.runeSequence.ui.theme;

import javax.swing.*;
import java.awt.*;
import java.awt.image.BufferedImage;
import java.util.Objects;

public class ThemedPanel extends JPanel {

	private final PanelStyle style;

	public ThemedPanel(PanelStyle style) {
		this(style, new BorderLayout());
	}

	public ThemedPanel(PanelStyle style, LayoutManager layout) {
		super(layout);
		this.style = Objects.requireNonNull(style, "style");
		setOpaque(false);
		setBorder(new ThemedPanelBorder(style));
	}

	public PanelStyle getStyle() {
		return style;
	}

	@Override
	public void setEnabled(boolean enabled) {
		super.setEnabled(enabled);
		for (Component child : getComponents()) {
			child.setEnabled(enabled);
		}
	}

	@Override
	protected void paintComponent(Graphics graphics) {
		super.paintComponent(graphics);

		int width = getWidth();
		int height = getHeight();
		if (width <= 0 || height <= 0) {
			return;
		}

		Theme theme = ThemeManager.getTheme();
		BufferedImage image = theme.getPanelBackgroundImage(style);
		if (image == null) {
			return;
		}

		Graphics2D g2 = graphics instanceof Graphics2D ? (Graphics2D) graphics.create() : null;
		if (g2 == null) {
			return;
		}

		try {
			g2.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BILINEAR);
			Insets insets = getInsets();
			int innerX = insets.left;
			int innerY = insets.top;
			int innerWidth = width - insets.left - insets.right;
			int innerHeight = height - insets.top - insets.bottom;
			Rectangle innerRect = new Rectangle(innerX, innerY, innerWidth, innerHeight);
			BackgroundFillPainter.paintCenterCropScale(g2, image, innerRect);
		} finally {
			g2.dispose();
		}
	}
}
