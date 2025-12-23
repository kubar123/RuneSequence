package com.lansoftprogramming.runeSequence.ui.theme;

import java.awt.*;
import java.awt.image.BufferedImage;

public interface Theme {

	String getName();

	NineSliceSpec getButtonSpec(ButtonStyle style);

	Insets getButtonPadding(ButtonStyle style);

	BufferedImage getButtonBaseImage(ButtonStyle style);

	BufferedImage getButtonImage(ButtonStyle style, ButtonVisualState state);
}