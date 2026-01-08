package com.lansoftprogramming.runeSequence.ui.overlay;

import com.lansoftprogramming.runeSequence.core.sequence.runtime.SequenceTooltip;
import com.lansoftprogramming.runeSequence.ui.theme.UiColorPalette;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.swing.*;
import java.awt.*;
import java.util.ArrayList;
import java.util.List;

/**
 * Dedicated overlay window for showing mouse-follow tooltip messages for the active step.
 * <p>
 * This overlay is click-through, non-focusable, and driven from the detection cadence.
 * It does not interact with detection or timing logic; it only renders messages from
 * {@link com.lansoftprogramming.runeSequence.application.SequenceManager#getCurrentTooltips()}.
 */
public class MouseTooltipOverlay {

	private static final Logger logger = LoggerFactory.getLogger(MouseTooltipOverlay.class);

	private static final int PADDING = 8;
	private static final int CORNER_RADIUS = 8;
	private static final int OFFSET_X = 16;
	private static final int OFFSET_Y = 20;
	private static final int MARGIN = 4;
	private static final int HIDE_GRACE_MS = 300;

	private final boolean headless;
	private final JWindow window;
	private final TooltipPanel panel;
	private final javax.swing.Timer followTimer;

	private volatile List<String> messages = List.of();
	private volatile long hideAtMillis = 0L;
	private Rectangle desktopBounds;
	private Point lastMouseScreenLocation;

	public MouseTooltipOverlay() {
		this.headless = GraphicsEnvironment.isHeadless();
		if (headless) {
			this.window = null;
			this.panel = null;
			this.followTimer = null;
			this.desktopBounds = new Rectangle(0, 0, 1, 1);
			logger.info("MouseTooltipOverlay initialized in headless mode");
			return;
		}

		this.window = createWindow();
		this.panel = new TooltipPanel();
		window.add(panel);
		setupBounds();
		this.followTimer = createFollowTimer();
		this.followTimer.start();
		logger.info("MouseTooltipOverlay initialized");
	}

	private JWindow createWindow() {
		JWindow w = new JWindow();
		w.setAlwaysOnTop(true);
		w.setFocusableWindowState(false);
		w.setAutoRequestFocus(false);
		w.setBackground(UiColorPalette.TRANSPARENT);
		return w;
	}

	private void setupBounds() {
		GraphicsEnvironment ge = GraphicsEnvironment.getLocalGraphicsEnvironment();
		GraphicsDevice[] devices = ge.getScreenDevices();

		Rectangle unionBounds = null;
		if (devices != null && devices.length > 0) {
			for (GraphicsDevice device : devices) {
				if (device == null || device.getDefaultConfiguration() == null) {
					continue;
				}
				Rectangle deviceBounds = device.getDefaultConfiguration().getBounds();
				if (unionBounds == null) {
					unionBounds = new Rectangle(deviceBounds);
				} else {
					unionBounds = unionBounds.union(deviceBounds);
				}
			}
		}

		if (unionBounds == null) {
			GraphicsDevice fallback = ge.getDefaultScreenDevice();
			if (fallback != null && fallback.getDefaultConfiguration() != null) {
				unionBounds = fallback.getDefaultConfiguration().getBounds();
			}
		}

		if (unionBounds == null) {
			unionBounds = new Rectangle(0, 0, 1, 1);
		}

		desktopBounds = unionBounds;
	}

	private javax.swing.Timer createFollowTimer() {
		// Poll the mouse at ~60 FPS but only reposition and repaint
		// when the cursor actually moves. This behaves like a
		// mouse-move-driven tooltip while still working over non-Java
		// windows via MouseInfo.
		javax.swing.Timer timer = new javax.swing.Timer(16, e -> {
			long hideAt = hideAtMillis;
			if (hideAt > 0 && System.currentTimeMillis() >= hideAt) {
				hideAtMillis = 0L;
				updateMessages(List.of());
				return;
			}

			List<String> snapshot = messages;
			if (snapshot == null || snapshot.isEmpty() || !window.isVisible()) {
				return;
			}

			PointerInfo pointerInfo = MouseInfo.getPointerInfo();
			if (pointerInfo == null) {
				return;
			}
			Point mouse = pointerInfo.getLocation();
			if (mouse == null) {
				return;
			}

			if (lastMouseScreenLocation != null && lastMouseScreenLocation.equals(mouse)) {
				return;
			}

			lastMouseScreenLocation = new Point(mouse);
			positionWindowAtMouse(mouse);
			panel.repaint();
		});
		timer.setRepeats(true);
		return timer;
	}

	public void showTooltips(List<SequenceTooltip> tooltips) {
		if (headless) {
			this.messages = normalizeMessages(tooltips);
			return;
		}
		List<String> nextMessages = normalizeMessages(tooltips);
		SwingUtilities.invokeLater(() -> {
			if (!nextMessages.isEmpty()) {
				// Show immediately and cancel any pending hide.
				if (hideAtMillis == 0L && window.isVisible() && nextMessages.equals(this.messages)) {
					return;
				}
				hideAtMillis = 0L;
				updateMessages(nextMessages);
			} else {
				// Only schedule a hide when transitioning from visible->empty.
				List<String> current = this.messages;
				if (current == null || current.isEmpty()) {
					return;
				}
				scheduleHide();
			}
		});
	}

	public void clear() {
		if (headless) {
			messages = List.of();
			hideAtMillis = 0L;
			return;
		}
		hideAtMillis = 0L;
		SwingUtilities.invokeLater(() -> updateMessages(List.of()));
	}

	public void shutdown() {
		if (headless) {
			messages = List.of();
			hideAtMillis = 0L;
			return;
		}
		clear();
		SwingUtilities.invokeLater(() -> {
			window.setVisible(false);
			window.dispose();
		});
		followTimer.stop();
	}

	private List<String> normalizeMessages(List<SequenceTooltip> tooltips) {
		if (tooltips == null || tooltips.isEmpty()) {
			return List.of();
		}
		List<String> out = new ArrayList<>();
		for (SequenceTooltip tooltip : tooltips) {
			if (tooltip == null) {
				continue;
			}
			String msg = tooltip.message();
			if (msg != null) {
				String trimmed = msg.trim();
				if (!trimmed.isEmpty()) {
					out.add(trimmed);
				}
			}
		}
		return out.isEmpty() ? List.of() : List.copyOf(out);
	}

	private void scheduleHide() {
		hideAtMillis = System.currentTimeMillis() + HIDE_GRACE_MS;
	}

	private void updateMessages(List<String> nextMessages) {
		this.messages = nextMessages;
		panel.updateLayout(nextMessages);
		if (nextMessages != null && !nextMessages.isEmpty()) {
			positionWindowAtMouse(null);
			window.pack();
			window.setVisible(true);
			ClickThroughWindowSupport.enable(window);
			panel.repaint();
		} else {
			window.setVisible(false);
			lastMouseScreenLocation = null;
		}
	}

	private void positionWindowAtMouse(Point mouseOverride) {
		if (messages == null || messages.isEmpty()) {
			return;
		}

		Point mouse = mouseOverride;
		if (mouse == null) {
			PointerInfo pointerInfo = MouseInfo.getPointerInfo();
			if (pointerInfo == null) {
				return;
			}
			mouse = pointerInfo.getLocation();
			if (mouse == null) {
				return;
			}
		}

		Dimension size = panel.getPreferredSize();
		if (size == null || size.width <= 0 || size.height <= 0) {
			return;
		}

		int x = mouse.x + OFFSET_X;
		int y = mouse.y + OFFSET_Y;

		if (desktopBounds != null) {
			int minX = desktopBounds.x + MARGIN;
			int minY = desktopBounds.y + MARGIN;
			int maxX = desktopBounds.x + desktopBounds.width - size.width - MARGIN;
			int maxY = desktopBounds.y + desktopBounds.height - size.height - MARGIN;

			if (x < minX) {
				x = minX;
			}
			if (y < minY) {
				y = minY;
			}
			if (x > maxX) {
				x = maxX;
			}
			if (y > maxY) {
				y = maxY;
			}
		}

		window.setLocation(x, y);
	}

	private final class TooltipPanel extends JPanel {

		private int bubbleWidth;
		private int bubbleHeight;
		private int lineHeight;
		private List<String> cachedMessages = List.of();

		TooltipPanel() {
			setOpaque(false);
			setBackground(UiColorPalette.TRANSPARENT);
		}

		void updateLayout(List<String> messages) {
			if (messages == null || messages.isEmpty()) {
				cachedMessages = List.of();
				bubbleWidth = 0;
				bubbleHeight = 0;
				lineHeight = 0;
				return;
			}

			setFont(UiColorPalette.boldSans(13));
			FontMetrics fm = getFontMetrics(getFont());
			lineHeight = fm.getHeight();
			int maxTextWidth = 0;
			for (String msg : messages) {
				if (msg == null) {
					continue;
				}
				maxTextWidth = Math.max(maxTextWidth, fm.stringWidth(msg));
			}

			if (maxTextWidth <= 0) {
				cachedMessages = List.of();
				bubbleWidth = 0;
				bubbleHeight = 0;
				return;
			}

			bubbleWidth = maxTextWidth + PADDING * 2;
			bubbleHeight = lineHeight * messages.size() + PADDING * 2;
			cachedMessages = List.copyOf(messages);
		}

		@Override
		public Dimension getPreferredSize() {
			if (bubbleWidth <= 0 || bubbleHeight <= 0) {
				return new Dimension(0, 0);
			}
			return new Dimension(bubbleWidth, bubbleHeight);
		}

		@Override
		protected void paintComponent(Graphics g) {
			super.paintComponent(g);

			List<String> snapshot = cachedMessages;
			if (snapshot == null || snapshot.isEmpty()) {
				return;
			}

			Graphics2D g2 = (Graphics2D) g.create();
			try {
				g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
				g2.setFont(UiColorPalette.boldSans(13));

				if (bubbleWidth <= 0 || bubbleHeight <= 0 || lineHeight <= 0) {
					return;
				}

				g2.setColor(UiColorPalette.TOOLTIP_OVERLAY_BACKGROUND);
				g2.fillRoundRect(0, 0, bubbleWidth, bubbleHeight, CORNER_RADIUS, CORNER_RADIUS);

				g2.setColor(UiColorPalette.TOOLTIP_OVERLAY_BORDER);
				g2.drawRoundRect(0, 0, bubbleWidth, bubbleHeight, CORNER_RADIUS, CORNER_RADIUS);

				g2.setColor(UiColorPalette.TOOLTIP_OVERLAY_TEXT);
				int textX = PADDING;
				FontMetrics fm = g2.getFontMetrics();
				int textY = PADDING + fm.getAscent();
				for (String msg : snapshot) {
					if (msg != null && !msg.isEmpty()) {
						g2.drawString(msg, textX, textY);
					}
					textY += lineHeight;
				}
			} finally {
				g2.dispose();
			}
		}
	}
}
