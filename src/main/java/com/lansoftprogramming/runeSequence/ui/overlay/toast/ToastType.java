package com.lansoftprogramming.runeSequence.ui.overlay.toast;

import java.awt.*;

/**
 * Enumerates toast categories with their default visuals and durations.
 */
public enum ToastType {
	SUCCESS(new Color(46, 170, 112), new Color(233, 247, 238), new Color(23, 111, 74), "\u2705", 2600),
	INFO(new Color(64, 140, 236), new Color(233, 240, 250), new Color(41, 92, 168), "\u2139", 2600),
	WARNING(new Color(245, 189, 65), new Color(253, 246, 229), new Color(151, 111, 24), "\u26A0", 5600),
	ERROR(new Color(220, 82, 70), new Color(250, 234, 232), new Color(160, 36, 28), "\u274C", 5600);

	private final Color accentColor;
	private final Color backgroundColor;
	private final Color foregroundColor;
	private final String icon;
	private final long displayMillis;

	ToastType(Color accentColor, Color backgroundColor, Color foregroundColor, String icon, long displayMillis) {
		this.accentColor = accentColor;
		this.backgroundColor = backgroundColor;
		this.foregroundColor = foregroundColor;
		this.icon = icon;
		this.displayMillis = displayMillis;
	}

	public Color getAccentColor() {
		return accentColor;
	}

	public Color getBackgroundColor() {
		return backgroundColor;
	}

	public Color getForegroundColor() {
		return foregroundColor;
	}

	public String getIcon() {
		return icon;
	}

	public long getDisplayMillis() {
		return displayMillis;
	}
}