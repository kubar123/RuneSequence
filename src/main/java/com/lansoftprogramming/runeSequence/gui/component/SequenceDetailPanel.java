package com.lansoftprogramming.runeSequence.gui.component;

import com.lansoftprogramming.runeSequence.config.RotationConfig;
import com.lansoftprogramming.runeSequence.gui.WrapLayout;
import com.lansoftprogramming.runeSequence.gui.model.AbilityItem;
import com.lansoftprogramming.runeSequence.gui.service.SequenceDetailService;
import com.lansoftprogramming.runeSequence.sequence.SequenceElement;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.swing.*;
import javax.swing.border.EmptyBorder;
import java.awt.*;
import java.util.List;

public class SequenceDetailPanel extends JPanel {
	private static final Logger logger = LoggerFactory.getLogger(SequenceDetailPanel.class);

	private final SequenceDetailService detailService;

	private final JTextField sequenceNameField;
	private final JButton settingsButton;
	private final JButton saveButton;
	private final JButton discardButton;
	private final JPanel abilityFlowPanel;

	public SequenceDetailPanel(SequenceDetailService detailService) {
		this.detailService = detailService;

		setLayout(new BorderLayout());
		setBorder(new EmptyBorder(10, 10, 10, 10));

		sequenceNameField = new JTextField();
		settingsButton = new JButton("Settings");
		saveButton = new JButton("Save");
		discardButton = new JButton("Discard");
		abilityFlowPanel = new WrappingAwarePanel(new WrapLayout(FlowLayout.LEFT, 10, 10));

		layoutComponents();
	}

	private void layoutComponents() {
		JPanel headerPanel = createHeaderPanel();
		JScrollPane contentScrollPane = createContentScrollPane();

		add(headerPanel, BorderLayout.NORTH);
		add(contentScrollPane, BorderLayout.CENTER);
	}

	private JPanel createHeaderPanel() {
		JPanel headerPanel = new JPanel(new BorderLayout(10, 0));

		JPanel namePanel = new JPanel(new BorderLayout(5, 0));
		namePanel.add(new JLabel("Sequence Name:"), BorderLayout.WEST);
		namePanel.add(sequenceNameField, BorderLayout.CENTER);

		JPanel buttonPanel = new JPanel(new FlowLayout(FlowLayout.RIGHT, 5, 0));
		buttonPanel.add(settingsButton);
		buttonPanel.add(discardButton);
		buttonPanel.add(saveButton);

		headerPanel.add(namePanel, BorderLayout.CENTER);
		headerPanel.add(buttonPanel, BorderLayout.EAST);

		return headerPanel;
	}

	private JScrollPane createContentScrollPane() {
		JScrollPane scrollPane = new JScrollPane(abilityFlowPanel);
		scrollPane.setBorder(BorderFactory.createLineBorder(Color.LIGHT_GRAY));
		scrollPane.setHorizontalScrollBarPolicy(JScrollPane.HORIZONTAL_SCROLLBAR_NEVER);
		scrollPane.setVerticalScrollBarPolicy(JScrollPane.VERTICAL_SCROLLBAR_AS_NEEDED);
		return scrollPane;
	}

	public void loadSequence(RotationConfig.PresetData presetData) {
		if (presetData == null) {
			clear();
			return;
		}

		sequenceNameField.setText(presetData.getName());
		abilityFlowPanel.removeAll();

		List<SequenceElement> elements = detailService.parseSequenceExpression(presetData.getExpression());
		renderSequenceElements(elements);

		abilityFlowPanel.revalidate();
		abilityFlowPanel.repaint();

		long abilityCount = elements.stream().filter(SequenceElement::isAbility).count();
		logger.debug("Loaded sequence: {} with {} abilities and {} elements",
				presetData.getName(), abilityCount, elements.size());
	}

	public void clear() {
		sequenceNameField.setText("");
		abilityFlowPanel.removeAll();
		abilityFlowPanel.revalidate();
		abilityFlowPanel.repaint();
	}

	private void renderSequenceElements(List<SequenceElement> elements) {
		for (int i = 0; i < elements.size(); i++) {
			SequenceElement currentElement = elements.get(i);

			if (currentElement.isAbility() && (i + 1 < elements.size())) {
				SequenceElement nextElement = elements.get(i + 1);

				if (nextElement.isPlus() || nextElement.isSlash()) {
					i = renderAbilityGroup(elements, i, nextElement.getType());
				} else {
					addStandaloneAbility(currentElement);
				}
			} else {
				addStandaloneElement(currentElement);
			}
		}
	}

	private int renderAbilityGroup(List<SequenceElement> elements, int startIndex, SequenceElement.Type groupType) {
		Color groupColor = groupType == SequenceElement.Type.PLUS
				? new Color(220, 235, 255) // Light blue for AND
				: new Color(255, 240, 220); // Light orange for OR

		AbilityGroupPanel groupPanel = new AbilityGroupPanel(groupColor);

		// Add first ability
		SequenceElement firstElement = elements.get(startIndex);
		AbilityItem firstItem = detailService.createAbilityItem(firstElement.getValue());
		if (firstItem != null) {
			groupPanel.add(createAbilityCard(firstItem));
		}

		// Consume group elements
		int groupIndex = startIndex + 1;
		while (groupIndex < elements.size()) {
			SequenceElement separator = elements.get(groupIndex);
			if (separator.getType() != groupType) {
				break;
			}

			if (groupIndex + 1 < elements.size() && elements.get(groupIndex + 1).isAbility()) {
				groupPanel.add(createSeparatorLabel(separator));

				SequenceElement abilityElement = elements.get(groupIndex + 1);
				AbilityItem item = detailService.createAbilityItem(abilityElement.getValue());
				if (item != null) {
					groupPanel.add(createAbilityCard(item));
				}
				groupIndex += 2;
			} else {
				break;
			}
		}

		abilityFlowPanel.add(groupPanel);
		return groupIndex - 1;
	}

	private void addStandaloneAbility(SequenceElement element) {
		AbilityItem item = detailService.createAbilityItem(element.getValue());
		if (item != null) {
			abilityFlowPanel.add(createAbilityCard(item));
		}
	}

	private void addStandaloneElement(SequenceElement element) {
		if (element.isAbility()) {
			addStandaloneAbility(element);
		} else if (element.isSeparator()) {
			abilityFlowPanel.add(createSeparatorLabel(element));
		}
	}

	private JPanel createAbilityCard(AbilityItem item) {
		JPanel card = new JPanel();
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
		card.add(Box.createRigidArea(new Dimension(0, 2)));
		card.add(nameLabel);

		card.setToolTipText(createTooltipText(item));

		return card;
	}

	private JLabel createSeparatorLabel(SequenceElement element) {
		String labelText = " " + element.getValue() + " ";
		JLabel label = new JLabel(labelText);
		label.setFont(new Font("Arial", Font.BOLD, 16));
		label.setAlignmentY(Component.CENTER_ALIGNMENT);
		return label;
	}

	private String createTooltipText(AbilityItem item) {
		return String.format("<html><b>%s</b><br>Type: %s<br>Level: %d</html>",
				item.getDisplayName(), item.getType(), item.getLevel());
	}

	private String truncateText(String text, int maxLength) {
		if (text == null || text.length() <= maxLength) {
			return text;
		}
		return text.substring(0, maxLength - 3) + "...";
	}

	private static class AbilityGroupPanel extends JPanel {
		public AbilityGroupPanel(Color backgroundColor) {
			setLayout(new FlowLayout(FlowLayout.LEFT, 5, 5));
			setBackground(backgroundColor);
			setBorder(BorderFactory.createCompoundBorder(
					BorderFactory.createLineBorder(Color.GRAY),
					new EmptyBorder(5, 5, 5, 5)
			));
		}
	}

	private static class WrappingAwarePanel extends JPanel implements Scrollable {
		public WrappingAwarePanel(LayoutManager layout) {
			super(layout);
		}

		@Override
		public Dimension getPreferredScrollableViewportSize() {
			return getPreferredSize();
		}

		@Override
		public int getScrollableUnitIncrement(Rectangle visibleRect, int orientation, int direction) {
			return 16;
		}

		@Override
		public int getScrollableBlockIncrement(Rectangle visibleRect, int orientation, int direction) {
			return (orientation == SwingConstants.VERTICAL) ? visibleRect.height : visibleRect.width;
		}

		@Override
		public boolean getScrollableTracksViewportWidth() {
			return true;
		}

		@Override
		public boolean getScrollableTracksViewportHeight() {
			return false;
		}
	}
}