package com.lansoftprogramming.runeSequence.ui.overlay.toast;

import com.lansoftprogramming.runeSequence.ui.theme.UiColorPalette;

import java.awt.*;

/**
 * Enumerates toast categories with their default visuals and durations.
 */
public enum ToastType {
	SUCCESS(UiColorPalette.TOAST_SUCCESS_ACCENT, UiColorPalette.TOAST_SUCCESS_BACKGROUND,
			UiColorPalette.TOAST_SUCCESS_FOREGROUND, "\u2705", 2600),
	INFO(UiColorPalette.TOAST_INFO_ACCENT, UiColorPalette.TOAST_INFO_BACKGROUND,
			UiColorPalette.TOAST_INFO_FOREGROUND, "\u2139", 2600),
	WARNING(UiColorPalette.TOAST_WARNING_ACCENT, UiColorPalette.TOAST_WARNING_BACKGROUND,
			UiColorPalette.TOAST_WARNING_FOREGROUND, "\u26A0", 5600),
	ERROR(UiColorPalette.TOAST_ERROR_ACCENT, UiColorPalette.TOAST_ERROR_BACKGROUND,
			UiColorPalette.TOAST_ERROR_FOREGROUND, "\u274C", 5600);

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
