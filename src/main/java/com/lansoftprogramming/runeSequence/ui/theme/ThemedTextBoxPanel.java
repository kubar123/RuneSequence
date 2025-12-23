package com.lansoftprogramming.runeSequence.ui.theme;

import javax.swing.*;
import java.awt.*;
import java.awt.image.BufferedImage;
import java.util.Objects;

public class ThemedTextBoxPanel extends JPanel {

	private final TextBoxStyle style;

	public ThemedTextBoxPanel(TextBoxStyle style) {
		this(style, new BorderLayout());
	}

	public ThemedTextBoxPanel(TextBoxStyle style, LayoutManager layout) {
		super(layout);
		this.style = Objects.requireNonNull(style, "style");
		setOpaque(false);
		setBorder(new ThemedTextBoxBorder(style));
	}

	public TextBoxStyle getStyle() {
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
		BufferedImage image = theme.getTextBoxImage(style);
		if (image == null) {
			return;
		}

		NineSliceSpec spec = theme.getTextBoxSpec(style);
		if (spec == null) {
			return;
		}

		Graphics2D g2 = graphics instanceof Graphics2D ? (Graphics2D) graphics.create() : null;
		if (g2 == null) {
			return;
		}

		try {
			g2.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BILINEAR);
			NineSlicePainter.paint(g2, image, spec, 0, 0, width, height);
		} finally {
			g2.dispose();
		}
	}
}
