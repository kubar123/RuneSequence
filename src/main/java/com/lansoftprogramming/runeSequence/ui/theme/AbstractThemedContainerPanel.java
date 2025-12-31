package com.lansoftprogramming.runeSequence.ui.theme;

import javax.swing.*;
import java.awt.*;
import java.util.Objects;

abstract class AbstractThemedContainerPanel extends JPanel {

	@FunctionalInterface
	protected interface ThemedPainter {
		void paint(Graphics2D graphics);
	}

	protected AbstractThemedContainerPanel(LayoutManager layout) {
		super(layout);
		setOpaque(false);
	}

	@Override
	public void setEnabled(boolean enabled) {
		super.setEnabled(enabled);
		for (Component child : getComponents()) {
			child.setEnabled(enabled);
		}
	}

	protected final void paintWithBilinearInterpolation(Graphics graphics, ThemedPainter painter) {
		Objects.requireNonNull(painter, "painter");

		Graphics2D g2 = graphics instanceof Graphics2D ? (Graphics2D) graphics.create() : null;
		if (g2 == null) {
			return;
		}

		try {
			g2.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BILINEAR);
			painter.paint(g2);
		} finally {
			g2.dispose();
		}
	}
}

