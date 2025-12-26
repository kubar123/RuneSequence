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
}
