package com.lansoftprogramming.runeSequence.ui.theme;

import javax.swing.border.AbstractBorder;
import java.awt.*;
import java.awt.image.BufferedImage;
import java.util.Objects;

public class NineSliceBorder extends AbstractBorder {

	@FunctionalInterface
	public interface NineSliceSpecProvider {
		NineSliceSpec getSpec(Component component);
	}

	@FunctionalInterface
	public interface ImageProvider {
		BufferedImage getImage(Component component);
	}

	private final NineSliceSpecProvider specProvider;
	private final ImageProvider imageProvider;

	public NineSliceBorder(NineSliceSpecProvider specProvider, ImageProvider imageProvider) {
		this.specProvider = Objects.requireNonNull(specProvider, "specProvider");
		this.imageProvider = Objects.requireNonNull(imageProvider, "imageProvider");
	}

	@Override
	public Insets getBorderInsets(Component component) {
		return specProvider.getSpec(component).toInsetsCopy();
	}

	@Override
	public Insets getBorderInsets(Component component, Insets insets) {
		Insets specInsets = getBorderInsets(component);
		insets.top = specInsets.top;
		insets.left = specInsets.left;
		insets.bottom = specInsets.bottom;
		insets.right = specInsets.right;
		return insets;
	}

	@Override
	public boolean isBorderOpaque() {
		return false;
	}

	@Override
	public void paintBorder(Component component, Graphics graphics, int x, int y, int width, int height) {
		if (width <= 0 || height <= 0) {
			return;
		}

		BufferedImage image = imageProvider.getImage(component);
		if (image == null) {
			return;
		}

		NineSliceSpec spec = specProvider.getSpec(component);
		if (spec == null) {
			return;
		}

		Graphics2D g2 = graphics instanceof Graphics2D ? (Graphics2D) graphics.create() : null;
		if (g2 == null) {
			return;
		}

		try {
			g2.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BILINEAR);
			NineSlicePainter.paint(g2, image, spec, x, y, width, height);
		} finally {
			g2.dispose();
		}
	}
}