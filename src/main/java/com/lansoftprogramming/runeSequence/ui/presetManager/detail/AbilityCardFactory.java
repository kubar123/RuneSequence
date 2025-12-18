package com.lansoftprogramming.runeSequence.ui.presetManager.detail;

import com.lansoftprogramming.runeSequence.ui.presetManager.drag.handler.AbilityDragController;
import com.lansoftprogramming.runeSequence.ui.presetManager.model.SequenceElement;
import com.lansoftprogramming.runeSequence.ui.shared.model.AbilityItem;
import com.lansoftprogramming.runeSequence.ui.shared.model.TooltipItem;
import com.lansoftprogramming.runeSequence.ui.theme.UiColorPalette;

import javax.swing.*;
import javax.swing.border.EmptyBorder;
import java.awt.*;
import java.awt.event.MouseAdapter;
import java.awt.image.BufferedImage;

class AbilityCardFactory {
	private final AbilityDragController dragController;
	private final ImageIcon tooltipIcon;

	AbilityCardFactory(AbilityDragController dragController) {
		this.dragController = dragController;
		this.tooltipIcon = createTooltipIcon();
	}

	JPanel createAbilityCard(AbilityItem item) {
		return createAbilityCard(item, false);
	}

	JPanel createAbilityCard(AbilityItem item, boolean showModifiedIndicator) {
		JPanel card = new JPanel();
		card.setName("abilityCard");
		card.setLayout(new BoxLayout(card, BoxLayout.Y_AXIS));
		card.setBackground(UiColorPalette.UI_CARD_BACKGROUND);
		card.setBorder(UiColorPalette.CARD_BORDER);
		card.setMinimumSize(new Dimension(50, 68));
		card.setPreferredSize(new Dimension(50, 68));
		card.setMaximumSize(new Dimension(50, 68));

		JLabel iconLabel = new JLabel(item.getIcon());
		iconLabel.setLayout(new BorderLayout());
		iconLabel.setAlignmentX(Component.CENTER_ALIGNMENT);
		iconLabel.setMinimumSize(new Dimension(50, 50));
		iconLabel.setPreferredSize(new Dimension(50, 50));
		iconLabel.setMaximumSize(new Dimension(50, 50));

		if (showModifiedIndicator && !(item instanceof TooltipItem)) {
			JLabel indicator = new JLabel("*");
			indicator.setFont(indicator.getFont().deriveFont(Font.BOLD, 13f));
			indicator.setForeground(UiColorPalette.TEXT_INVERSE);
			indicator.setToolTipText("Modified");

			JPanel badge = new JPanel(new GridBagLayout()) {
				@Override
				protected void paintComponent(Graphics g) {
					Graphics2D g2d = (Graphics2D) g.create();
					g2d.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
					g2d.setColor(UiColorPalette.TEXT_DANGER);
					g2d.fillOval(0, 0, getWidth() - 1, getHeight() - 1);
					g2d.dispose();
					super.paintComponent(g);
				}
			};
			badge.setOpaque(false);
			badge.setToolTipText("Modified");
			badge.setPreferredSize(new Dimension(14, 14));
			badge.setMinimumSize(new Dimension(14, 14));
			badge.setMaximumSize(new Dimension(14, 14));
			badge.add(indicator);

			JPanel indicatorWrap = new JPanel(new FlowLayout(FlowLayout.RIGHT, 0, 0));
			indicatorWrap.setOpaque(false);
			indicatorWrap.setBorder(new EmptyBorder(2, 0, 0, 2));
			indicatorWrap.add(badge);
			iconLabel.add(indicatorWrap, BorderLayout.NORTH);
		}

		String displayText = truncateText(formatCardDisplayName(item.getDisplayName()), 12);
		JLabel nameLabel = new JLabel(displayText);
		nameLabel.setFont(nameLabel.getFont().deriveFont(9f));
		nameLabel.setAlignmentX(Component.CENTER_ALIGNMENT);
		nameLabel.setMinimumSize(new Dimension(50, 16));
		nameLabel.setPreferredSize(new Dimension(50, 16));
		nameLabel.setMaximumSize(new Dimension(50, 16));
		nameLabel.setForeground(UiColorPalette.UI_TEXT_COLOR);

		card.add(iconLabel);
		card.add(nameLabel);
		card.putClientProperty("abilityKey", item.getKey());
		card.setToolTipText(createTooltipText(item));

		MouseAdapter dragListener = dragController.createCardDragListener(item, card, false);
		card.addMouseListener(dragListener);
		card.addMouseMotionListener(dragListener);
		return card;
	}

	JLabel createSeparatorLabel(SequenceElement element) {
		JLabel label = new JLabel(" " + element.getValue() + " ");
		label.setFont(UiColorPalette.SYMBOL_LARGE);
		label.setForeground(UiColorPalette.TEXT_DANGER);
		label.setBorder(new EmptyBorder(0, 5, 0, 5));
		return label;
	}

	private String createTooltipText(AbilityItem item) {
		if (item instanceof TooltipItem tooltipItem) {
			String message = tooltipItem.getMessage();
			if (message == null || message.isEmpty()) {
				return "<html><b>Tooltip</b><br/>(Double-click to edit)</html>";
			}
			return "<html>" + escapeHtml(message) + "</html>";
		}
		return String.format("<html><b>%s</b><br/>Type: %s<br/>Level: %d</html>",
				item.getDisplayName(),
				item.getType(),
				item.getLevel());
	}

	private String formatCardDisplayName(String displayName) {
		if (displayName == null) {
			return "";
		}
		String trimmed = displayName.trim();
		if (trimmed.equals("Greater")) {
			return "G.";
		}
		if (trimmed.startsWith("Greater ")) {
			return "G. " + trimmed.substring("Greater ".length());
		}
		return displayName;
	}

	private String truncateText(String text, int maxLength) {
		if (text.length() <= maxLength) {
			return text;
		}
		return text.substring(0, maxLength - 3) + "...";
	}

	JPanel createTooltipCard(String message) {
		String normalized = message != null ? message : "";
		String displayName = normalized.isEmpty() ? "Tooltip" : truncateText(normalized, 12);
		String key = "tooltip-" + Integer.toHexString(normalized.hashCode());
		TooltipItem item = new TooltipItem(key, displayName, normalized, tooltipIcon);
		return createAbilityCard(item);
	}

	private ImageIcon createTooltipIcon() {
		int size = 18;
		int padding = 3;
		int cornerRadius = 8;
		BufferedImage image = new BufferedImage(size, size, BufferedImage.TYPE_INT_ARGB);
		Graphics2D g2d = image.createGraphics();
		g2d.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
		g2d.setColor(UiColorPalette.INSERT_ICON_FILL);
		g2d.fillRoundRect(padding, padding, size - (padding * 2), size - (padding * 2), cornerRadius, cornerRadius);
		g2d.setColor(UiColorPalette.TEXT_INVERSE);
		g2d.setFont(g2d.getFont().deriveFont(Font.BOLD, 10f));
		FontMetrics fm = g2d.getFontMetrics();
		String text = "T";
		int textWidth = fm.stringWidth(text);
		int textHeight = fm.getAscent();
		int x = (size - textWidth) / 2;
		int y = (size + textHeight) / 2 - 2;
		g2d.drawString(text, x, y);
		g2d.dispose();
		return new ImageIcon(image);
	}

	private String escapeHtml(String text) {
		StringBuilder builder = new StringBuilder(text.length());
		for (int i = 0; i < text.length(); i++) {
			char c = text.charAt(i);
			switch (c) {
				case '<' -> builder.append("&lt;");
				case '>' -> builder.append("&gt;");
				case '&' -> builder.append("&amp;");
				case '"' -> builder.append("&quot;");
				default -> builder.append(c);
			}
		}
		return builder.toString();
	}
}
