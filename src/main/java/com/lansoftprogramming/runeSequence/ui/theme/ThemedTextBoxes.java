package com.lansoftprogramming.runeSequence.ui.theme;

import javax.swing.*;
import java.awt.*;
import java.util.Objects;

public final class ThemedTextBoxes {

	private ThemedTextBoxes() {
	}

	public static ThemedTextBoxPanel wrap(JTextField field) {
		return wrap(field, TextBoxStyle.DEFAULT);
	}

	public static ThemedTextBoxPanel wrap(JTextField field, TextBoxStyle style) {
		Objects.requireNonNull(field, "field");
		Objects.requireNonNull(style, "style");

		field.setBorder(BorderFactory.createEmptyBorder(0, 0, 0, 0));
		field.setOpaque(false);

		ThemedTextBoxPanel panel = new ThemedTextBoxPanel(style, new BorderLayout());
		panel.add(field, BorderLayout.CENTER);
		return panel;
	}
}
