package com.lansoftprogramming.runeSequence.ui.theme;

import java.awt.*;
import java.awt.image.BufferedImage;
import java.util.Objects;

public class ThemedTextBoxPanel extends AbstractThemedContainerPanel {

	private final TextBoxStyle style;

	public ThemedTextBoxPanel(TextBoxStyle style) {
		this(style, new BorderLayout());
	}

	public ThemedTextBoxPanel(TextBoxStyle style, LayoutManager layout) {
		super(layout);
		this.style = Objects.requireNonNull(style, "style");
		setBorder(new ThemedTextBoxBorder(style));
	}

	public TextBoxStyle getStyle() {
		return style;
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

		paintWithBilinearInterpolation(graphics, g2 ->
			NineSlicePainter.paint(g2, image, spec, 0, 0, width, height)
		);
	}
}
