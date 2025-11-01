package com.lansoftprogramming.runeSequence.ui.presetManager.detail;

import com.lansoftprogramming.runeSequence.ui.presetManager.drag.handler.AbilityDragController;
import com.lansoftprogramming.runeSequence.ui.presetManager.model.SequenceElement;
import com.lansoftprogramming.runeSequence.ui.shared.model.AbilityItem;

import javax.swing.*;
import javax.swing.border.EmptyBorder;
import java.awt.*;
import java.awt.event.MouseAdapter;

class AbilityCardFactory {
	private final AbilityDragController dragController;

	AbilityCardFactory(AbilityDragController dragController) {
		this.dragController = dragController;
	}

	JPanel createAbilityCard(AbilityItem item) {
		JPanel card = new JPanel();
		card.setName("abilityCard");
		card.setLayout(new BoxLayout(card, BoxLayout.Y_AXIS));
		card.setBackground(Color.WHITE);
		card.setBorder(BorderFactory.createLineBorder(Color.LIGHT_GRAY));
		card.setMinimumSize(new Dimension(50, 68));
		card.setPreferredSize(new Dimension(50, 68));
		card.setMaximumSize(new Dimension(50, 68));

		JLabel iconLabel = new JLabel(item.getIcon());
		iconLabel.setAlignmentX(Component.CENTER_ALIGNMENT);
		iconLabel.setMinimumSize(new Dimension(50, 50));
		iconLabel.setPreferredSize(new Dimension(50, 50));
		iconLabel.setMaximumSize(new Dimension(50, 50));

		String displayText = truncateText(item.getDisplayName(), 12);
		JLabel nameLabel = new JLabel(displayText);
		nameLabel.setFont(nameLabel.getFont().deriveFont(9f));
		nameLabel.setAlignmentX(Component.CENTER_ALIGNMENT);
		nameLabel.setMinimumSize(new Dimension(50, 16));
		nameLabel.setPreferredSize(new Dimension(50, 16));
		nameLabel.setMaximumSize(new Dimension(50, 16));

		card.add(iconLabel);
		card.add(nameLabel);
		card.putClientProperty("abilityKey", item.getKey());

		MouseAdapter dragListener = dragController.createCardDragListener(item, card, false);
		card.addMouseListener(dragListener);
		card.addMouseMotionListener(dragListener);
		return card;
	}

	JLabel createSeparatorLabel(SequenceElement element) {
		JLabel label = new JLabel(" " + element.getValue() + " ");
		label.setFont(new Font(Font.SANS_SERIF, Font.BOLD, 18));
		label.setForeground(Color.RED);
		label.setBorder(new EmptyBorder(0, 5, 0, 5));
		return label;
	}

	private String truncateText(String text, int maxLength) {
		if (text.length() <= maxLength) {
			return text;
		}
		return text.substring(0, maxLength - 3) + "...";
	}
}
