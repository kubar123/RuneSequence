package com.lansoftprogramming.runeSequence.ui.overlay;

import com.lansoftprogramming.runeSequence.core.detection.DetectionResult;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.swing.*;
import java.awt.*;
import java.awt.event.ComponentAdapter;
import java.awt.event.ComponentEvent;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

/**
 * OverlayRenderer - Creates click-through overlay windows to draw borders around detected abilities
 */
public class OverlayRenderer {
	private static final Logger logger = LoggerFactory.getLogger(OverlayRenderer.class);

	// Border types and colors - like piano key highlighting
	public enum BorderType {
		CURRENT_GREEN(new Color(0, 255, 0, 220), 4),        // Current abilities - thick bright green
		NEXT_RED(new Color(255, 0, 0, 200), 3),             // Next abilities - thin red
		CURRENT_OR_PURPLE(new Color(221, 0, 255, 220), 4),  // Current OR - thick purple
		NEXT_OR_DARK_PURPLE(new Color(140, 0, 255, 200), 3); // Next OR - thin dark purple

		public final Color color;
		public final int thickness;

		BorderType(Color color, int thickness) {
			this.color = color;
			this.thickness = thickness;
		}
	}

	private final JWindow overlayWindow;
	private final OverlayPanel overlayPanel;
	private final ConcurrentMap<String, OverlayBorder> activeBorders;
	private volatile boolean overlayVisible = false;

	public OverlayRenderer() {
		this.activeBorders = new ConcurrentHashMap<>();
		this.overlayWindow = createOverlayWindow();
		this.overlayPanel = new OverlayPanel();

		overlayWindow.add(overlayPanel);
		setupWindowProperties();

		logger.info("OverlayRenderer initialized");
	}

	private JWindow createOverlayWindow() {
		JWindow window = new JWindow();

		// Make window click-through and always on top
		window.setAlwaysOnTop(true);
		window.setFocusableWindowState(false);
		window.setAutoRequestFocus(false);

		// Set transparent background
		window.setBackground(new Color(0, 0, 0, 0));

		return window;
	}

	private void setupWindowProperties() {
		// Cover entire screen initially
		GraphicsEnvironment ge = GraphicsEnvironment.getLocalGraphicsEnvironment();
		GraphicsDevice gd = ge.getDefaultScreenDevice();
		Rectangle screenBounds = gd.getDefaultConfiguration().getBounds();

		overlayWindow.setBounds(screenBounds);

		// Handle screen changes
		overlayWindow.addComponentListener(new ComponentAdapter() {
			@Override
			public void componentResized(ComponentEvent e) {
				overlayPanel.repaint();
			}
		});

		logger.debug("Overlay window bounds: {}", screenBounds);
	}

	/**
	 * Update overlays with current and next abilities
	 * Called from DetectionEngine.updateOverlays()
	 * Borders persist until next update or clearOverlays() call
	 */
	public void updateOverlays(java.util.List<DetectionResult> currentAbilities, java.util.List<DetectionResult> nextAbilities) {
		try {
			// Clear existing borders
			activeBorders.clear();
			System.out.println("ABILITIES");
			if (currentAbilities != null) {
				for (DetectionResult result : currentAbilities) {
					System.out.println("Current ability: " + result.templateName +
							" found=" + result.found +
							" location=" + result.location +
							" boundingBox=" + result.boundingBox +
							" confidence=" + result.confidence +
							" isAlternative="+ result.isAlternative);
				}
			}
//			System.exit(0);

			// Add current ability borders (green/purple)
			if (currentAbilities != null) {
				for (DetectionResult result : currentAbilities) {
					BorderType borderType = determineCurrentBorderType(currentAbilities, result);
					addBorder(result, borderType);
				}
			}

			// Add next ability borders (red/dark purple)
			if (nextAbilities != null) {
				for (DetectionResult result : nextAbilities) {
					BorderType borderType = determineNextBorderType(nextAbilities, result);
					addBorder(result, borderType);
				}
			}

			// Show/hide overlay window based on whether we have borders
			boolean shouldShow = !activeBorders.isEmpty();
			setOverlayVisible(shouldShow);

			// Repaint to show changes
			SwingUtilities.invokeLater(overlayPanel::repaint);

		} catch (Exception e) {
			logger.error("Error updating overlays", e);
		}
	}

	private BorderType determineCurrentBorderType(java.util.List<DetectionResult> currentAbilities, DetectionResult result) {
		// If multiple current abilities, it's an OR group - use purple
		//Check if result is alternative or not
		return result.isAlternative ? BorderType.CURRENT_OR_PURPLE : BorderType.CURRENT_GREEN;
	}

	private BorderType determineNextBorderType(java.util.List<DetectionResult> nextAbilities, DetectionResult result) {
		// If multiple next abilities, it's an OR group - use dark purple
		return nextAbilities.size() > 1 ? BorderType.NEXT_OR_DARK_PURPLE : BorderType.NEXT_RED;
	}

	private void addBorder(DetectionResult result, BorderType borderType) {

		System.out.println("OverlayRenderer.addBorder: " + result.templateName +
				" found=" + result.found + " boundingBox=" + result.boundingBox);

		if (result == null || !result.found) {

			System.out.println("OverlayRenderer: Skipping border for " + result.templateName + " (not found)");
			return;
		}


		if (result.boundingBox == null) {

			System.err.println("OverlayRenderer: ERROR - found=true but boundingBox=null for " + result.templateName);

			return;

		}

		Rectangle bounds = calculateBorderBounds(result);
		OverlayBorder border = new OverlayBorder(result.templateName, bounds, borderType);

		activeBorders.put(result.templateName, border);

		System.out.println("OverlayRenderer: Successfully added " + borderType + " border for " + result.templateName);

		logger.debug("Added {} border for {} at {}", borderType, result.templateName, bounds);
	}

	private Rectangle calculateBorderBounds(DetectionResult result) {
		// Expand bounds slightly beyond the detection area for better visibility
		int padding = 3;

		// Use boundingBox from DetectionResult (should always exist for found results)
		if (result.boundingBox != null) {
			return new Rectangle(
					result.boundingBox.x - padding,
					result.boundingBox.y - padding,
					result.boundingBox.width + (padding * 2),
					result.boundingBox.height + (padding * 2)
			);
		} else {
			// Fallback: shouldn't happen with proper DetectionResult.found() usage
			logger.warn("DetectionResult missing boundingBox for {}", result.templateName);
			int defaultSize = 24; // Slightly larger default
			return new Rectangle(
					result.location.x - padding,
					result.location.y - padding,
					defaultSize + (padding * 2),
					defaultSize + (padding * 2)
			);
		}
	}

	/**
	 * Clear all overlay borders immediately
	 * Called from DetectionEngine.stop() or when manually clearing
	 */
	public void clearOverlays() {
		activeBorders.clear();
		setOverlayVisible(false);
		SwingUtilities.invokeLater(overlayPanel::repaint);
		logger.debug("Cleared all overlays");
	}

	private void setOverlayVisible(boolean visible) {
		if (overlayVisible != visible) {
			overlayVisible = visible;
			SwingUtilities.invokeLater(() -> overlayWindow.setVisible(visible));

			logger.debug("Overlay visibility: {}", visible);
		}
	}

	/**
	 * Shutdown the overlay renderer and clean up resources
	 */
	public void shutdown() {
		clearOverlays();
		SwingUtilities.invokeLater(() -> {
			overlayWindow.setVisible(false);
			overlayWindow.dispose();
		});
		logger.info("OverlayRenderer shutdown");
	}

	/**
	 * Inner class representing a single border overlay
	 */
	private static class OverlayBorder {
		final String templateName;
		final Rectangle bounds;
		final BorderType borderType;

		OverlayBorder(String templateName, Rectangle bounds, BorderType borderType) {
			this.templateName = templateName;
			this.bounds = bounds;
			this.borderType = borderType;
		}
	}

	/**
	 * Custom JPanel for rendering overlay borders
	 */
	private class OverlayPanel extends JPanel {

		public OverlayPanel() {
			setOpaque(false); // Transparent background
			setBackground(new Color(0, 0, 0, 0));
		}

		@Override
		protected void paintComponent(Graphics g) {
			super.paintComponent(g);

			Graphics2D g2d = (Graphics2D) g.create();
			try {
				// Enable antialiasing for smoother borders
				g2d.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);

				// Draw all active borders
				for (OverlayBorder border : activeBorders.values()) {
					drawBorder(g2d, border);
				}

			} finally {
				g2d.dispose();
			}
		}

		private void drawBorder(Graphics2D g2d, OverlayBorder border) {
			g2d.setColor(border.borderType.color);
			g2d.setStroke(new BasicStroke(border.borderType.thickness,
					BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));

			// Draw border rectangle
			g2d.drawRect(border.bounds.x, border.bounds.y,
					border.bounds.width, border.bounds.height);
		}
	}
}