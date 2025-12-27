package com.lansoftprogramming.runeSequence.ui.theme;

import java.awt.*;
import java.awt.image.BufferedImage;

public interface Theme {

	String getName();

	NineSliceSpec getButtonSpec(ButtonStyle style);

	Insets getButtonPadding(ButtonStyle style);

	BufferedImage getButtonBaseImage(ButtonStyle style);

	BufferedImage getButtonImage(ButtonStyle style, ButtonVisualState state);

	NineSliceSpec getTextBoxSpec(TextBoxStyle style);

	Insets getTextBoxPadding(TextBoxStyle style);

	BufferedImage getTextBoxImage(TextBoxStyle style);

	NineSliceSpec getPanelSpec(PanelStyle style);

	Insets getPanelPadding(PanelStyle style);

	BufferedImage getPanelBorderImage(PanelStyle style);

	BufferedImage getPanelBackgroundImage(PanelStyle style);

	BufferedImage getDialogDividerImage(DialogStyle style);

	Font getDialogTitleFont(float size);

	BufferedImage getTabBaseImage();

	BufferedImage getTabActiveImage();

	BufferedImage getTabContentBackgroundImage();

	NineSliceSpec getTabSpec();

	int getTabInterTabOverlapPx();

	default BufferedImage getTabOpenedMarkerImage() {
		return null;
	}

	default int getTabOpenedMarkerAnchorFromBottomPx() {
		return 0;
	}

	/**
	 * Theme-provided colors for custom-painted widgets (lists, overlays, accents).
	 * Prefer these over hard-coded colors in UI components.
	 */
	default Color getTextPrimaryColor() {
		return UiColorPalette.UI_TEXT_COLOR;
	}

	default Color getTextMutedColor() {
		return UiColorPalette.DIALOG_MESSAGE_TEXT;
	}

	default Color getAccentPrimaryColor() {
		return UiColorPalette.TOAST_INFO_ACCENT;
	}

	default Color getAccentHoverColor() {
		return getAccentPrimaryColor();
	}

	/**
	 * Single-pixel inset line color for list/field containers.
	 */
	default Color getInsetBorderColor() {
		return UiColorPalette.UI_DIVIDER_FAINT;
	}

	/**
	 * Window title bar colors (used with FlatLaf custom window decorations).
	 * These are best-effort and may be ignored by the platform/LAF.
	 */
	default Color getWindowTitleBarBackground() {
		return UiColorPalette.UI_CARD_BACKGROUND;
	}

	default Color getWindowTitleBarForeground() {
		return getTextPrimaryColor();
	}

	default Color getWindowTitleBarInactiveBackground() {
		return UiColorPalette.UI_CARD_BACKGROUND.darker();
	}

	default Color getWindowTitleBarInactiveForeground() {
		return getTextMutedColor();
	}
}
