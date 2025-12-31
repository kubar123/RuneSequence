package com.lansoftprogramming.runeSequence.ui.theme;

import com.formdev.flatlaf.FlatLaf;

import javax.swing.*;
import java.util.Locale;

public final class ThemedWindowDecorations {

	private ThemedWindowDecorations() {
	}

	public static void applyTitleBar(RootPaneContainer container) {
		if (container == null) {
			return;
		}
		applyTitleBar(container.getRootPane());
	}

	public static void applyTitleBar(JRootPane root) {
		if (!(UIManager.getLookAndFeel() instanceof FlatLaf)) {
			return;
		}

		if (root == null) {
			return;
		}

		Theme theme = ThemeManager.getTheme();
		if (theme == null) {
			return;
		}

		// Use property keys directly to stay compatible across FlatLaf versions.
		String osName = System.getProperty("os.name", "").toLowerCase(Locale.ROOT);
		boolean isMac = osName.contains("mac");
		if (!isMac) {
			root.putClientProperty("JRootPane.useWindowDecorations", Boolean.TRUE);
		}
		root.putClientProperty("JRootPane.titleBarBackground", theme.getWindowTitleBarBackground());
		root.putClientProperty("JRootPane.titleBarForeground", theme.getWindowTitleBarForeground());
		root.putClientProperty("JRootPane.titleBarInactiveBackground", theme.getWindowTitleBarInactiveBackground());
		root.putClientProperty("JRootPane.titleBarInactiveForeground", theme.getWindowTitleBarInactiveForeground());
	}
}