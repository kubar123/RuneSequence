package com.lansoftprogramming.runeSequence.ui.presetManager.masterRotations;

import com.lansoftprogramming.runeSequence.infrastructure.config.AppSettings;
import com.lansoftprogramming.runeSequence.ui.theme.UiColorPalette;

import javax.swing.*;
import java.awt.*;
import java.awt.image.BufferedImage;
import java.util.Objects;
import java.util.function.Supplier;

/**
 * Provides an icon-based indicator for the rotation that is currently selected
 * in the user's settings. Encapsulating this logic keeps the panel renderer
 * focused on presentation while this class deals with settings access and
 * icon creation.
 */
public class SelectedSequenceIndicator {
	private final Supplier<String> selectedIdSupplier;
	private final Icon selectedIcon;

	public static SelectedSequenceIndicator forSettings(AppSettings settings) {
		Objects.requireNonNull(settings, "settings cannot be null");
		return new SelectedSequenceIndicator(() -> {
			AppSettings.RotationSettings rotation = settings.getRotation();
			return rotation != null ? rotation.getSelectedId() : null;
		});
	}

	public SelectedSequenceIndicator(Supplier<String> selectedIdSupplier) {
		this(selectedIdSupplier, createDefaultIcon());
	}

	public SelectedSequenceIndicator(Supplier<String> selectedIdSupplier, Icon selectedIcon) {
		this.selectedIdSupplier = Objects.requireNonNull(selectedIdSupplier, "selectedIdSupplier cannot be null");
		this.selectedIcon = Objects.requireNonNull(selectedIcon, "selectedIcon cannot be null");
	}

	public Icon iconFor(String sequenceId) {
		return isSelected(sequenceId) ? selectedIcon : null;
	}

	public boolean isSelected(String sequenceId) {
		if (sequenceId == null) {
			return false;
		}
		String selectedId = selectedIdSupplier.get();
		return selectedId != null && sequenceId.equals(selectedId);
	}

	public Icon getSelectedIcon() {
		return selectedIcon;
	}

	private static Icon createDefaultIcon() {
		int size = 14;
		BufferedImage img = new BufferedImage(size, size, BufferedImage.TYPE_INT_ARGB);
		Graphics2D g2d = img.createGraphics();
		g2d.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);

		g2d.setColor(UiColorPalette.SELECTION_ACTIVE_FILL);
		g2d.fillOval(0, 0, size - 1, size - 1);

		g2d.setColor(UiColorPalette.SELECTION_ACTIVE_BORDER);
		g2d.drawOval(0, 0, size - 1, size - 1);

		g2d.setStroke(new BasicStroke(2f));
		g2d.setColor(UiColorPalette.TEXT_INVERSE);
		g2d.drawLine(size / 4, size / 2, size / 2, size - size / 4);
		g2d.drawLine(size / 2, size - size / 4, size - size / 5, size / 4);

		g2d.dispose();
		return new ImageIcon(img);
	}
}
