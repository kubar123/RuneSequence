package com.lansoftprogramming.runeSequence.ui.theme;

import java.awt.image.BufferedImage;
import java.util.EnumMap;
import java.util.Map;
import java.util.Objects;

final class ButtonImageSet {

	private final Map<ButtonVisualState, BufferedImage> images;

	private ButtonImageSet(Map<ButtonVisualState, BufferedImage> images) {
		this.images = images;
	}

	static ButtonImageSet fromBase(BufferedImage base, ButtonStateTreatment treatment) {
		Objects.requireNonNull(base, "base");
		Objects.requireNonNull(treatment, "treatment");

		Map<ButtonVisualState, BufferedImage> out = new EnumMap<>(ButtonVisualState.class);
		out.put(ButtonVisualState.NORMAL, base);
		out.put(ButtonVisualState.HOVER, ImageVariants.applyOverlay(base, treatment.hoverOverlayColor(), treatment.hoverOverlayAlpha()));
		out.put(ButtonVisualState.PRESSED, ImageVariants.applyOverlay(base, treatment.pressedOverlayColor(), treatment.pressedOverlayAlpha()));
		out.put(ButtonVisualState.DISABLED, ImageVariants.toDisabled(base, treatment.disabledAlpha()));
		return new ButtonImageSet(out);
	}

	BufferedImage get(ButtonVisualState state) {
		return images.getOrDefault(state, images.get(ButtonVisualState.NORMAL));
	}
}

