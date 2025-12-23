package com.lansoftprogramming.runeSequence.ui.theme;

import javax.swing.*;
import java.awt.*;
import java.util.Objects;

public final class ThemedButtons {

	private ThemedButtons() {
	}

	public static JButton create(String text) {
		JButton button = new JButton(text);
		apply(button, ButtonStyle.DEFAULT);
		return button;
	}

	public static JButton create(Icon icon) {
		JButton button = new JButton(icon);
		apply(button, ButtonStyle.DEFAULT);
		return button;
	}

	public static JButton create(String text, Icon icon) {
		JButton button = new JButton(text, icon);
		apply(button, ButtonStyle.DEFAULT);
		return button;
	}

	public static void apply(JButton button, ButtonStyle style) {
		Objects.requireNonNull(button, "button");
		Objects.requireNonNull(style, "style");

		button.setHorizontalAlignment(SwingConstants.CENTER);
		button.setVerticalAlignment(SwingConstants.CENTER);
		button.setMargin(new Insets(0, 0, 0, 0));
		button.setUI(new ThemedButtonUI(style));
		button.setBorder(new ThemedButtonBorder(style));
		button.setContentAreaFilled(false);
		button.setOpaque(false);
		button.setRolloverEnabled(true);
	}
}