package com.lansoftprogramming.runeSequence.ui.theme;

import java.awt.*;
import java.util.Objects;

public final class ThemedPanelBorder extends NineSliceBorder {

	private final PanelStyle style;

	public ThemedPanelBorder(PanelStyle style) {
		super(
				component -> ThemeManager.getTheme().getPanelSpec(style),
				component -> ThemeManager.getTheme().getPanelBorderImage(style)
		);
		this.style = Objects.requireNonNull(style, "style");
	}

	public PanelStyle getStyle() {
		return style;
	}

	@Override
	public Insets getBorderInsets(Component component) {
		return ThemeManager.getTheme().getPanelPadding(style);
	}
}