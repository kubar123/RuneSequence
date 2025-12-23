package com.lansoftprogramming.runeSequence.ui.theme;

import java.awt.*;
import java.awt.image.BufferedImage;
import java.util.EnumMap;
import java.util.Map;
import java.util.Objects;

public final class MetalTheme implements Theme {

	private static final String NAME = "metal";

	private static final String BUTTON_DEFAULT_IMAGE = "/ui/metal/Btn_Default.png";
	private static final NineSliceSpec BUTTON_DEFAULT_SPEC = new NineSliceSpec(4, 5, 10, 5);
	private static final Insets BUTTON_DEFAULT_PADDING = new Insets(5, 12, 5, 12);

	private static final ButtonStateTreatment BUTTON_TREATMENT = new ButtonStateTreatment(
			UiColorPalette.BASE_WHITE, 0.08f,
			UiColorPalette.BASE_BLACK, 0.14f,
			0.55f
	);

	private final ThemeImageLoader imageLoader = new ThemeImageLoader(MetalTheme.class);
	private final Map<ButtonStyle, ButtonImageSet> buttonImages = new EnumMap<>(ButtonStyle.class);

	@Override
	public String getName() {
		return NAME;
	}

	@Override
	public NineSliceSpec getButtonSpec(ButtonStyle style) {
		Objects.requireNonNull(style, "style");
		return switch (style) {
			case DEFAULT -> BUTTON_DEFAULT_SPEC;
		};
	}

	@Override
	public Insets getButtonPadding(ButtonStyle style) {
		Objects.requireNonNull(style, "style");
		Insets padding = switch (style) {
			case DEFAULT -> BUTTON_DEFAULT_PADDING;
		};
		return new Insets(padding.top, padding.left, padding.bottom, padding.right);
	}

	@Override
	public BufferedImage getButtonBaseImage(ButtonStyle style) {
		Objects.requireNonNull(style, "style");
		return getButtonImageSet(style).get(ButtonVisualState.NORMAL);
	}

	@Override
	public BufferedImage getButtonImage(ButtonStyle style, ButtonVisualState state) {
		Objects.requireNonNull(style, "style");
		Objects.requireNonNull(state, "state");
		return getButtonImageSet(style).get(state);
	}

	private ButtonImageSet getButtonImageSet(ButtonStyle style) {
		return buttonImages.computeIfAbsent(style, this::createButtonImageSet);
	}

	private ButtonImageSet createButtonImageSet(ButtonStyle style) {
		BufferedImage base = switch (style) {
			case DEFAULT -> imageLoader.loadImage(BUTTON_DEFAULT_IMAGE);
		};
		return ButtonImageSet.fromBase(base, BUTTON_TREATMENT);
	}
}