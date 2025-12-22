package com.lansoftprogramming.runeSequence.ui.regionSelector;

import com.lansoftprogramming.runeSequence.ui.overlay.ClickThroughWindowSupport;
import com.lansoftprogramming.runeSequence.ui.theme.UiColorPalette;

import javax.swing.*;
import java.awt.*;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/**
 * Displays a semi-transparent overlay on the desktop for saved regions.
 */
final class RegionPreviewOverlay implements AutoCloseable {
	private static final int BORDER_THICKNESS = 2;
	private static final int FILL_ALPHA = 70;

	private final boolean headless;
	private final JWindow overlayWindow;
	private final OverlayPanel overlayPanel;
	private volatile boolean clickThroughApplied = false;

	RegionPreviewOverlay() {
		this.headless = GraphicsEnvironment.isHeadless();
		if (headless) {
			this.overlayWindow = null;
			this.overlayPanel = null;
			return;
		}

		this.overlayWindow = new JWindow();
		this.overlayPanel = new OverlayPanel();

		overlayWindow.setAlwaysOnTop(true);
		overlayWindow.setFocusableWindowState(false);
		overlayWindow.setAutoRequestFocus(false);
		overlayWindow.setBackground(UiColorPalette.TRANSPARENT);
		overlayWindow.setContentPane(overlayPanel);

		Rectangle bounds = GraphicsEnvironment.getLocalGraphicsEnvironment().getMaximumWindowBounds();
		overlayWindow.setBounds(bounds);
	}

	void showOverlay() {
		if (headless || overlayWindow == null) {
			return;
		}
		overlayWindow.setVisible(true);
		SwingUtilities.invokeLater(this::applyClickThroughIfNeeded);
	}

	void hideOverlay() {
		if (headless || overlayWindow == null) {
			return;
		}
		overlayWindow.setVisible(false);
	}

	void setRegions(List<RegionOverlayItem> regions) {
		if (headless || overlayPanel == null) {
			return;
		}
		overlayPanel.setRegions(regions);
	}

	@Override
	public void close() {
		if (headless || overlayWindow == null) {
			return;
		}
		overlayWindow.dispose();
	}

	static final class RegionOverlayItem {
		final int index;
		final String name;
		final Rectangle region;

		RegionOverlayItem(int index, String name, Rectangle region) {
			this.index = index;
			this.name = name;
			this.region = Objects.requireNonNull(region, "region");
		}
	}

	private void applyClickThroughIfNeeded() {
		if (clickThroughApplied || overlayWindow == null) {
			return;
		}
		clickThroughApplied = ClickThroughWindowSupport.enable(overlayWindow);
	}

	private static final class OverlayPanel extends JPanel {
		private List<RegionOverlayItem> regions = List.of();

		OverlayPanel() {
			setOpaque(false);
			setCursor(Cursor.getPredefinedCursor(Cursor.DEFAULT_CURSOR));
		}

		void setRegions(List<RegionOverlayItem> regions) {
			this.regions = regions != null ? new ArrayList<>(regions) : List.of();
			repaint();
		}

		@Override
		protected void paintComponent(Graphics g) {
			super.paintComponent(g);
			if (regions == null || regions.isEmpty()) {
				return;
			}

			Graphics2D g2 = (Graphics2D) g.create();
			try {
				g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);

				for (RegionOverlayItem item : regions) {
					if (item == null || item.region == null || item.region.width <= 0 || item.region.height <= 0) {
						continue;
					}

					Color border = borderColorForIndex(item.index);
					Color fill = fillFromBorder(border);

					g2.setColor(fill);
					g2.fill(item.region);

					g2.setColor(border);
					g2.setStroke(new BasicStroke(BORDER_THICKNESS));
					g2.draw(item.region);
				}
			} finally {
				g2.dispose();
			}
		}

		private static Color borderColorForIndex(int index) {
			int normalized = Math.floorMod(index, 4);
			return switch (normalized) {
				case 0 -> UiColorPalette.REGION_SELECTION_BORDER;
				case 1 -> UiColorPalette.OVERLAY_CURRENT_AND;
				case 2 -> UiColorPalette.OVERLAY_NEXT_AND;
				default -> UiColorPalette.OVERLAY_CURRENT_OR;
			};
		}

		private static Color fillFromBorder(Color border) {
			if (border == null) {
				return UiColorPalette.REGION_SELECTION_FILL;
			}
			return new Color(border.getRed(), border.getGreen(), border.getBlue(), FILL_ALPHA);
		}
	}
}
