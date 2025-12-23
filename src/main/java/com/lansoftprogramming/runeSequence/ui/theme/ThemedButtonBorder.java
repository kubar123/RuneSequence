package com.lansoftprogramming.runeSequence.ui.theme;

import javax.swing.border.AbstractBorder;
import java.awt.*;
import java.util.Objects;

public final class ThemedButtonBorder extends AbstractBorder {

	private final ButtonStyle style;

	public ThemedButtonBorder(ButtonStyle style) {
		this.style = Objects.requireNonNull(style, "style");
	}

	public ButtonStyle getStyle() {
		return style;
	}

	@Override
	public Insets getBorderInsets(Component component) {
		return ThemeManager.getTheme().getButtonPadding(style);
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
}