package com.lansoftprogramming.runeSequence.ui.regionSelector;

import com.lansoftprogramming.runeSequence.ui.theme.UiColorPalette;

import javax.swing.*;
import java.awt.*;

public class RegionOverlay extends JPanel {
	private final Rectangle selection;

	public RegionOverlay(Rectangle selection) {
		this.selection = selection;
		setOpaque(false);
	}

	@Override
	protected void paintComponent(Graphics g) {
		super.paintComponent(g);
		Graphics2D g2 = (Graphics2D) g.create();

		// Dark transparent background
		g2.setColor(UiColorPalette.REGION_OVERLAY_SCRIM);
		g2.fillRect(0, 0, getWidth(), getHeight());

		// Draw selection box
		if (selection.width > 0 && selection.height > 0) {
			g2.setColor(UiColorPalette.REGION_SELECTION_BORDER);
			g2.setStroke(new BasicStroke(2));
			g2.draw(selection);
			g2.setColor(UiColorPalette.REGION_SELECTION_FILL);
			g2.fill(selection);
		}

		g2.dispose();
	}
}
