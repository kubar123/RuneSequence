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

	private final JWindow window;
	private final TooltipPanel panel;

	private volatile List<String> messages = List.of();

	public MouseTooltipOverlay() {
		this.window = createWindow();
		this.panel = new TooltipPanel();
		window.add(panel);
		setupBounds();
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
		GraphicsDevice gd = ge.getDefaultScreenDevice();
		Rectangle bounds = gd.getDefaultConfiguration().getBounds();
		window.setBounds(bounds);
	}

	public void showTooltips(List<SequenceTooltip> tooltips) {
		List<String> nextMessages = normalizeMessages(tooltips);
		SwingUtilities.invokeLater(() -> updateMessages(nextMessages));
	}

	public void clear() {
		SwingUtilities.invokeLater(() -> updateMessages(List.of()));
	}

	public void shutdown() {
		clear();
		SwingUtilities.invokeLater(() -> {
			window.setVisible(false);
			window.dispose();
		});
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

	private void updateMessages(List<String> nextMessages) {
		List<String> current = this.messages;
		if (current.equals(nextMessages)) {
			return;
		}
		this.messages = nextMessages;
		boolean shouldShow = !nextMessages.isEmpty();
		window.setVisible(shouldShow);
		panel.repaint();
	}

	private final class TooltipPanel extends JPanel {

		TooltipPanel() {
			setOpaque(false);
			setBackground(UiColorPalette.TRANSPARENT);
		}

		@Override
		protected void paintComponent(Graphics g) {
			super.paintComponent(g);

			List<String> snapshot = messages;
			if (snapshot == null || snapshot.isEmpty()) {
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

			int mouseX = mouse.x - window.getX();
			int mouseY = mouse.y - window.getY();

			Graphics2D g2 = (Graphics2D) g.create();
			try {
				g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
				g2.setFont(UiColorPalette.boldSans(13));
				FontMetrics fm = g2.getFontMetrics();

				int lineHeight = fm.getHeight();
				int maxTextWidth = 0;
				for (String msg : snapshot) {
					if (msg == null) {
						continue;
					}
					maxTextWidth = Math.max(maxTextWidth, fm.stringWidth(msg));
				}

				if (maxTextWidth <= 0) {
					return;
				}

				int bubbleWidth = maxTextWidth + PADDING * 2;
				int bubbleHeight = lineHeight * snapshot.size() + PADDING * 2;

				int x = mouseX + OFFSET_X;
				int y = mouseY + OFFSET_Y;

				int panelWidth = getWidth();
				int panelHeight = getHeight();

				if (x + bubbleWidth > panelWidth - MARGIN) {
					x = panelWidth - bubbleWidth - MARGIN;
				}
				if (y + bubbleHeight > panelHeight - MARGIN) {
					y = panelHeight - bubbleHeight - MARGIN;
				}
				if (x < MARGIN) {
					x = MARGIN;
				}
				if (y < MARGIN) {
					y = MARGIN;
				}

				g2.setColor(UiColorPalette.TOOLTIP_OVERLAY_BACKGROUND);
				g2.fillRoundRect(x, y, bubbleWidth, bubbleHeight, CORNER_RADIUS, CORNER_RADIUS);

				g2.setColor(UiColorPalette.TOOLTIP_OVERLAY_BORDER);
				g2.drawRoundRect(x, y, bubbleWidth, bubbleHeight, CORNER_RADIUS, CORNER_RADIUS);

				g2.setColor(UiColorPalette.TOOLTIP_OVERLAY_TEXT);
				int textX = x + PADDING;
				int textY = y + PADDING + fm.getAscent();
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