package com.lansoftprogramming.runeSequence.ui.theme;

import javax.swing.border.AbstractBorder;
import java.awt.*;
import java.util.Objects;

public final class ThemedTextBoxBorder extends AbstractBorder {

	private final TextBoxStyle style;

	public ThemedTextBoxBorder(TextBoxStyle style) {
		this.style = Objects.requireNonNull(style, "style");
	}

	public TextBoxStyle getStyle() {
		return style;
	}

	@Override
	public Insets getBorderInsets(Component component) {
		return ThemeManager.getTheme().getTextBoxPadding(style);
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
