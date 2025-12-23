package com.lansoftprogramming.runeSequence.ui.theme;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.swing.*;
import javax.swing.plaf.basic.BasicButtonUI;
import java.awt.*;
import java.awt.image.BufferedImage;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

final class ThemedButtonUI extends BasicButtonUI {

	private static final Logger logger = LoggerFactory.getLogger(ThemedButtonUI.class);
	private static final Set<String> loggedWarnings = ConcurrentHashMap.newKeySet();

	private final ButtonStyle style;

	ThemedButtonUI(ButtonStyle style) {
		this.style = Objects.requireNonNull(style, "style");
	}

	@Override
	public void update(Graphics graphics, JComponent component) {
		if (!(component instanceof AbstractButton button)) {
			super.update(graphics, component);
			return;
		}

		Graphics2D backgroundGraphics = graphics instanceof Graphics2D ? (Graphics2D) graphics.create() : null;
		if (backgroundGraphics != null) {
			try {
				backgroundGraphics.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BILINEAR);
				paintBackground(backgroundGraphics, button);
			} finally {
				backgroundGraphics.dispose();
			}
		}

		Graphics2D contentGraphics = graphics instanceof Graphics2D ? (Graphics2D) graphics.create() : null;
		if (contentGraphics == null) {
			super.update(graphics, component);
			return;
		}

		try {
			if (shouldOffsetPressedContent(button)) {
				contentGraphics.translate(0, 1);
			}
			paint(contentGraphics, component);
		} finally {
			contentGraphics.dispose();
		}
	}

	private void paintBackground(Graphics2D g2, AbstractButton button) {
		Theme theme = ThemeManager.getTheme();
		ButtonVisualState state = resolveVisualState(button);

		BufferedImage image;
		NineSliceSpec spec;
		try {
			image = theme.getButtonImage(style, state);
			spec = theme.getButtonSpec(style);
		} catch (RuntimeException e) {
			logOnce("theme-failure-" + theme.getName() + "-" + style, "Failed to resolve themed button background.", e);
			return;
		}

		if (image == null || spec == null) {
			logOnce("theme-null-" + theme.getName() + "-" + style, "Theme returned null button image/spec: theme={}, style={}", theme.getName(), style);
			return;
		}

		NineSlicePainter.paint(g2, image, spec, 0, 0, button.getWidth(), button.getHeight());
	}

	private static ButtonVisualState resolveVisualState(AbstractButton button) {
		if (!button.isEnabled()) {
			return ButtonVisualState.DISABLED;
		}

		ButtonModel model = button.getModel();
		if (model != null && model.isPressed() && model.isArmed()) {
			return ButtonVisualState.PRESSED;
		}
		if (model != null && model.isRollover()) {
			return ButtonVisualState.HOVER;
		}
		return ButtonVisualState.NORMAL;
	}

	private static boolean shouldOffsetPressedContent(AbstractButton button) {
		ButtonModel model = button.getModel();
		return model != null && button.isEnabled() && model.isPressed() && model.isArmed();
	}

	private static void logOnce(String key, String message, Object... args) {
		if (!loggedWarnings.add(key)) {
			return;
		}
		logger.warn(message, args);
	}
}