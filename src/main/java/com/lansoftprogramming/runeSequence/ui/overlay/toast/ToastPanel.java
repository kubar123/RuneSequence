package com.lansoftprogramming.runeSequence.ui.overlay.toast;

import com.lansoftprogramming.runeSequence.ui.theme.UiColorPalette;
import com.lansoftprogramming.runeSequence.ui.shared.util.ClipboardStrings;

import javax.swing.*;
import javax.swing.border.EmptyBorder;
import java.awt.*;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.util.Objects;

/**
 * Visual component for a toast card. Handles non-intrusive UI affordances and contextual actions.
 */
class ToastPanel extends JPanel {
	private static final int CORNER_RADIUS = 8;
	private static final int SHADOW_OFFSET_Y = 2;
	private static final int ACCENT_WIDTH = 6;
	private static final int ICON_FONT_SIZE = 16;

	private final ToastType type;
	private final String message;
	private final String hiddenMessage;
	private final JLabel iconLabel;
	private final JLabel messageLabel;
	private float opacity;
	private Runnable dismissRequest;

	ToastPanel(ToastType type, String message, String hiddenMessage) {
		this.type = Objects.requireNonNull(type, "type");
		this.message = Objects.requireNonNull(message, "message");
		this.hiddenMessage = hiddenMessage;
		this.iconLabel = new JLabel(type.getIcon());
		this.messageLabel = new JLabel(message);
		this.opacity = 0f;

		setOpaque(false);
		setFocusable(false);
		setRequestFocusEnabled(false);
		setBorder(new EmptyBorder(12, 26, 12, 16));
		setLayout(new BoxLayout(this, BoxLayout.X_AXIS));

		configureContent();
		registerInteractions();
	}

	void setDismissRequest(Runnable dismissRequest) {
		this.dismissRequest = dismissRequest;
	}

	void setOpacity(float opacity) {
		this.opacity = Math.max(0f, Math.min(opacity, 1f));
		repaint();
	}

	float getOpacity() {
		return opacity;
	}

	String getMessage() {
		return message;
	}

	String getHiddenMessage() {
		return hiddenMessage;
	}

	@Override
	public Dimension getPreferredSize() {
		Dimension base = super.getPreferredSize();
		base.width = Math.max(base.width, 320);
		return base;
	}

	@Override
	protected void paintComponent(Graphics g) {
		Graphics2D g2 = (Graphics2D) g.create();
		try {
			g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);

			float alpha = opacity;
			if (alpha <= 0f) {
				return;
			}

			int width = getWidth();
			int height = getHeight();

			// Soft shadow
			g2.setComposite(AlphaComposite.SrcOver.derive(alpha * 0.3f));
			g2.setColor(UiColorPalette.TOAST_SHADOW);
			g2.fillRoundRect(4, SHADOW_OFFSET_Y + 4, width - 8, height - 8, CORNER_RADIUS, CORNER_RADIUS);

			// Card background
			g2.setComposite(AlphaComposite.SrcOver.derive(alpha));
			g2.setColor(type.getBackgroundColor());
			g2.fillRoundRect(0, 0, width, height, CORNER_RADIUS, CORNER_RADIUS);

			// Accent strip
			g2.setColor(type.getAccentColor());
			g2.fillRoundRect(0, 0, ACCENT_WIDTH + CORNER_RADIUS, height, CORNER_RADIUS, CORNER_RADIUS);
		} finally {
			g2.dispose();
		}
		super.paintComponent(g);
	}

	private void configureContent() {
		iconLabel.setForeground(type.getForegroundColor());
		iconLabel.setFont(UiColorPalette.boldSans(ICON_FONT_SIZE));
		iconLabel.setBorder(new EmptyBorder(0, 0, 0, 6));
		iconLabel.setAlignmentY(0.5f);

		messageLabel.setForeground(UiColorPalette.TOAST_MESSAGE_FOREGROUND);
		messageLabel.setAlignmentY(0.5f);

		add(Box.createHorizontalStrut(ACCENT_WIDTH + 8));
		add(iconLabel);
		add(Box.createHorizontalStrut(8));
		add(messageLabel);
	}

	private void registerInteractions() {
		JPopupMenu contextMenu = new JPopupMenu();
		JMenuItem copyItem = new JMenuItem("Copy message");
		copyItem.addActionListener(e -> copyToClipboard());
		contextMenu.add(copyItem);

		MouseAdapter adapter = new MouseAdapter() {
			@Override
			public void mouseClicked(MouseEvent e) {
				if (SwingUtilities.isLeftMouseButton(e)) {
					triggerDismiss();
				}
			}

			@Override
			public void mousePressed(MouseEvent e) {
				handlePopup(e);
			}

			@Override
			public void mouseReleased(MouseEvent e) {
				handlePopup(e);
			}

			private void handlePopup(MouseEvent e) {
				if (e.isPopupTrigger()) {
					contextMenu.show(ToastPanel.this, e.getX(), e.getY());
				}
			}
		};

		addMouseListener(adapter);
		iconLabel.addMouseListener(adapter);
		messageLabel.addMouseListener(adapter);
	}

	private void triggerDismiss() {
		if (dismissRequest != null) {
			dismissRequest.run();
		}
	}

	private void copyToClipboard() {
		StringBuilder builder = new StringBuilder(message);
		if (hiddenMessage != null && !hiddenMessage.isBlank()) {
			builder.append(System.lineSeparator()).append(hiddenMessage);
		}
		ClipboardStrings.WriteResult result = ClipboardStrings.writeSystemClipboardString(builder.toString());
		if (result.status() != ClipboardStrings.WriteStatus.SUCCESS) {
			Toolkit.getDefaultToolkit().beep();
		}
	}
}
