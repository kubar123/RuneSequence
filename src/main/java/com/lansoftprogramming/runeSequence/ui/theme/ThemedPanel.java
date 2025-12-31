package com.lansoftprogramming.runeSequence.ui.theme;

import java.awt.*;
import java.awt.image.BufferedImage;
import java.util.Objects;

public class ThemedPanel extends AbstractThemedContainerPanel {

	private final PanelStyle style;

	public ThemedPanel(PanelStyle style) {
		this(style, new BorderLayout());
	}

	public ThemedPanel(PanelStyle style, LayoutManager layout) {
		super(layout);
		this.style = Objects.requireNonNull(style, "style");
		setBorder(new ThemedPanelBorder(style));
	}

	public PanelStyle getStyle() {
		return style;
	}

	@Override
	protected void paintComponent(Graphics graphics) {
		super.paintComponent(graphics);

		Theme theme = ThemeManager.getTheme();
		BufferedImage image = theme.getPanelBackgroundImage(style);
		if (image == null) {
			return;
		}

		ThemedBackgroundPainter.paintBackground(graphics, this, image, BackgroundFillPainter::paintCenterCropScale);
	}
}
