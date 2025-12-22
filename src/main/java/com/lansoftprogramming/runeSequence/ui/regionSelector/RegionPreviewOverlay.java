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
	private static final int LABEL_PADDING_X = 8;
	private static final int LABEL_PADDING_Y = 4;
	private static final int LABEL_MARGIN = 4;
	private static final int LABEL_ARC = 10;

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

					drawRegionLabel(g2, item, border);
				}
			} finally {
				g2.dispose();
			}
		}

		private void drawRegionLabel(Graphics2D g2, RegionOverlayItem item, Color accent) {
			String label = item != null ? item.name : null;
			if (label == null || label.trim().isEmpty() || item.region == null) {
				return;
			}

			Font previousFont = g2.getFont();
			g2.setFont(previousFont.deriveFont(Font.BOLD));
			FontMetrics metrics = g2.getFontMetrics();

			int textWidth = metrics.stringWidth(label);
			int textHeight = metrics.getHeight();
			int boxWidth = textWidth + (LABEL_PADDING_X * 2);
			int boxHeight = textHeight + (LABEL_PADDING_Y * 2);

			int preferredX = item.region.x + BORDER_THICKNESS + LABEL_MARGIN;
			int preferredY = item.region.y - boxHeight - LABEL_MARGIN;
			boolean placeAbove = preferredY >= 0;

			int x = preferredX;
			int y = placeAbove ? preferredY : (item.region.y + BORDER_THICKNESS + LABEL_MARGIN);

			int maxX = Math.max(0, getWidth() - boxWidth - LABEL_MARGIN);
			int maxY = Math.max(0, getHeight() - boxHeight - LABEL_MARGIN);
			x = Math.max(LABEL_MARGIN, Math.min(x, maxX));
			y = Math.max(LABEL_MARGIN, Math.min(y, maxY));

			g2.setColor(UiColorPalette.DROP_ZONE_LABEL_BACKGROUND);
			g2.fillRoundRect(x, y, boxWidth, boxHeight, LABEL_ARC, LABEL_ARC);

			if (accent != null) {
				g2.setColor(accent);
				g2.setStroke(new BasicStroke(1f));
				g2.drawRoundRect(x, y, boxWidth, boxHeight, LABEL_ARC, LABEL_ARC);
			}

			g2.setColor(UiColorPalette.TOOLTIP_OVERLAY_TEXT);
			int textX = x + LABEL_PADDING_X;
			int textY = y + LABEL_PADDING_Y + metrics.getAscent();
			g2.drawString(label, textX, textY);

			g2.setFont(previousFont);
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
