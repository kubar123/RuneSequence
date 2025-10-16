package com.lansoftprogramming.runeSequence.gui;

import com.lansoftprogramming.runeSequence.config.*;
import com.lansoftprogramming.runeSequence.gui.component.AbilityPalettePanel;
import com.lansoftprogramming.runeSequence.gui.model.AbilityItem;
import com.lansoftprogramming.runeSequence.gui.service.AbilityIconLoader;
import com.lansoftprogramming.runeSequence.sequence.SequenceElement;
import com.lansoftprogramming.runeSequence.sequence.SequenceVisualService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.swing.*;
import javax.swing.border.EmptyBorder;
import javax.swing.event.ListSelectionEvent;
import javax.swing.event.ListSelectionListener;
import java.awt.*;
import java.util.ArrayList;
import java.util.List;

public class PresetManagerWindow extends JFrame {
	private static final Logger logger = LoggerFactory.getLogger(PresetManagerWindow.class);

	// Dependencies
	private final ConfigManager configManager;
	private AbilityIconLoader iconLoader;
	private final SequenceVisualService sequenceVisualService;

	// Models
	private final SequenceListModel sequenceListModel;

	// Master Panel Components
	private JList<String> sequenceList;
	private JButton addSequenceButton;
	private JButton deleteSequenceButton;
	private JButton importButton;
	private JButton exportButton;

	// Detail Panel Components
	private JPanel sequenceDetailPanel;
	private JButton settingsButton;
	private JButton saveButton;
	private JButton discardButton;
	private JTextField sequenceNameField;
	private JPanel abilityFlowPanel;

	// Palette Panel Components
	private AbilityPalettePanel abilityPalettePanel;

	// Split Panes
	private JSplitPane verticalSplit;
	private JSplitPane horizontalSplit;

	public PresetManagerWindow(ConfigManager configManager) {
		this.configManager = configManager;
		this.sequenceListModel = new SequenceListModel();
		this.sequenceVisualService = new SequenceVisualService();

		initializeFrame();
		initializeComponents();
		layoutComponents();
		loadSequences();
		setVisible(true);
	}

	private void initializeFrame() {
		setTitle("Preset Manager");
		setDefaultCloseOperation(JFrame.HIDE_ON_CLOSE);
		setSize(1400, 900);
		setLocationRelativeTo(null);
	}

	private void initializeComponents() {
		initializeMasterPanel();
		initializeDetailPanel();
		initializePalettePanel();
	}

	private void initializeMasterPanel() {
		sequenceList = new JList<>(sequenceListModel);
		sequenceList.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
		sequenceList.setCellRenderer(new SequenceListCellRenderer());
		sequenceList.addListSelectionListener(new SequenceSelectionListener());

		addSequenceButton = new JButton("+");
		deleteSequenceButton = new JButton("🗑");
		importButton = new JButton("Import");
		exportButton = new JButton("Export");
	}

	private void initializeDetailPanel() {
		sequenceDetailPanel = new JPanel(new BorderLayout());

		settingsButton = new JButton("Settings");
		saveButton = new JButton("Save");
		discardButton = new JButton("Discard");
		sequenceNameField = new JTextField();
		abilityFlowPanel = new JPanel(new FlowLayout(FlowLayout.LEFT, 10, 10));
	}

	private void initializePalettePanel() {
		try {
			iconLoader = new AbilityIconLoader(
					configManager.getConfigDir().resolve("Abilities")
			);

			abilityPalettePanel = new AbilityPalettePanel(
					configManager.getAbilities(),
					configManager.getAbilityCategories(),
					iconLoader
			);
		} catch (Exception e) {
			logger.error("Failed to initialize palette panel", e);
			JOptionPane.showMessageDialog(this,
					"Failed to initialize ability palette: " + e.getMessage(),
					"Error",
					JOptionPane.ERROR_MESSAGE);
		}
	}

	private void layoutComponents() {
		JPanel masterPanel = createMasterPanel();
		JPanel detailPanel = createDetailPanel();

		// Horizontal split: Master (left 25%) | Palette (right 75%)
		horizontalSplit = new JSplitPane(JSplitPane.HORIZONTAL_SPLIT, masterPanel, abilityPalettePanel);
		horizontalSplit.setResizeWeight(0.25);
		horizontalSplit.setDividerLocation(0.25);

		// Vertical split: Top region (75%) | Detail Panel (25%)
		verticalSplit = new JSplitPane(JSplitPane.VERTICAL_SPLIT, horizontalSplit, detailPanel);
		verticalSplit.setResizeWeight(0.75);
		verticalSplit.setDividerLocation(0.75);

		add(verticalSplit);
	}

	private JPanel createMasterPanel() {
		JPanel masterPanel = new JPanel(new BorderLayout());
		masterPanel.setBorder(new EmptyBorder(10, 10, 10, 10));

		// Fixed controls panel at top
		JPanel controlsPanel = new JPanel(new GridLayout(2, 2, 5, 5));
		controlsPanel.add(addSequenceButton);
		controlsPanel.add(deleteSequenceButton);
		controlsPanel.add(importButton);
		controlsPanel.add(exportButton);

		// Scrollable sequence list
		JScrollPane listScrollPane = new JScrollPane(sequenceList);
		listScrollPane.setVerticalScrollBarPolicy(JScrollPane.VERTICAL_SCROLLBAR_ALWAYS);

		masterPanel.add(controlsPanel, BorderLayout.NORTH);
		masterPanel.add(listScrollPane, BorderLayout.CENTER);

		return masterPanel;
	}

	private JPanel createDetailPanel() {
		JPanel detailPanel = new JPanel(new BorderLayout());
		detailPanel.setBorder(new EmptyBorder(10, 10, 10, 10));

		// Header with controls
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

		// Ability flow panel with scroll
		abilityFlowPanel.setBorder(BorderFactory.createLineBorder(Color.LIGHT_GRAY));
		JScrollPane flowScrollPane = new JScrollPane(abilityFlowPanel);
		flowScrollPane.setHorizontalScrollBarPolicy(JScrollPane.HORIZONTAL_SCROLLBAR_AS_NEEDED);
		flowScrollPane.setVerticalScrollBarPolicy(JScrollPane.VERTICAL_SCROLLBAR_AS_NEEDED);

		// Loading indicator placeholder
		JLabel loadingLabel = new JLabel("Loading...", SwingConstants.CENTER);
		loadingLabel.setForeground(Color.GRAY);

		JPanel contentPanel = new JPanel(new BorderLayout());
		contentPanel.add(flowScrollPane, BorderLayout.CENTER);

		detailPanel.add(headerPanel, BorderLayout.NORTH);
		detailPanel.add(contentPanel, BorderLayout.CENTER);

		return detailPanel;
	}


	/**
	 * Loads sequences from ConfigManager and populates the list.
	 */
	private void loadSequences() {
		try {
			RotationConfig rotations = configManager.getRotations();
			sequenceListModel.loadFromConfig(rotations);
			logger.info("Loaded {} sequences", sequenceListModel.getSize());
		} catch (Exception e) {
			logger.error("Failed to load sequences", e);
			JOptionPane.showMessageDialog(this,
					"Failed to load sequences: " + e.getMessage(),
					"Error",
					JOptionPane.ERROR_MESSAGE);
		}
	}

	/**
	 * Loads the selected sequence details into the detail panel.
	 */
	private void loadSequenceDetails(SequenceListModel.SequenceEntry entry) {
		if (entry == null) {
			clearDetailPanel();
			return;
		}

		RotationConfig.PresetData presetData = entry.getPresetData();
		sequenceNameField.setText(presetData.getName());

		abilityFlowPanel.removeAll();
		List<SequenceElement> elements = sequenceVisualService.parseToVisualElements(presetData.getExpression());

		for (int i = 0; i < elements.size(); i++) {
			SequenceElement currentElement = elements.get(i);

			// Check if this element can start a group (ability followed by + or /)
			if (currentElement.isAbility() && (i + 1 < elements.size())) {
				SequenceElement nextElement = elements.get(i + 1);

				if (nextElement.isPlus() || nextElement.isSlash()) {
					// Start of a new group found
					SequenceElement.Type groupType = nextElement.getType();
					Color groupColor = groupType == SequenceElement.Type.PLUS
							? new Color(220, 235, 255) // Light blue for AND
							: new Color(255, 240, 220); // Light orange for OR

					AbilityGroupPanel groupPanel = new AbilityGroupPanel(groupColor);

					// Add the first ability to the group
					AbilityItem firstItem = createAbilityItem(currentElement.getValue());
					if (firstItem != null) {
						groupPanel.add(createAbilityCard(firstItem));
					}

					// Loop forward to consume all elements belonging to this group
					int groupIndex = i + 1;
					while (groupIndex < elements.size()) {
						SequenceElement separator = elements.get(groupIndex);
						if (separator.getType() != groupType) {
							break; // Group ends here
						}

						if (groupIndex + 1 < elements.size() && elements.get(groupIndex + 1).isAbility()) {
							groupPanel.add(createSeparatorLabel(separator));

							SequenceElement abilityElement = elements.get(groupIndex + 1);
							AbilityItem item = createAbilityItem(abilityElement.getValue());
							if (item != null) {
								groupPanel.add(createAbilityCard(item));
							}
							groupIndex += 2; // Consumed separator and ability
						} else {
							break; // Malformed sequence, end group
						}
					}

					abilityFlowPanel.add(groupPanel);
					i = groupIndex - 1; // Update main loop index

				} else {
					// Standalone ability (e.g., followed by -> or at the end)
					addStandaloneAbility(currentElement);
				}
			} else {
				// Add standalone element (separator like -> or the very last ability)
				addStandaloneElement(currentElement);
			}
		}

		abilityFlowPanel.revalidate();
		abilityFlowPanel.repaint();

		long abilityCount = elements.stream().filter(SequenceElement::isAbility).count();
		logger.debug("Loaded sequence: {} ({}) with {} abilities and {} elements",
				presetData.getName(), entry.getId(), abilityCount, elements.size());
	}

	private void addStandaloneAbility(SequenceElement element) {
		AbilityItem item = createAbilityItem(element.getValue());
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


	/**
	 * Clears the detail panel.
	 */
	private void clearDetailPanel() {
		sequenceNameField.setText("");
		abilityFlowPanel.removeAll();
		abilityFlowPanel.revalidate();
		abilityFlowPanel.repaint();
	}

	/**
	 * Creates an AbilityItem from an ability key.
	 */
	private AbilityItem createAbilityItem(String abilityKey) {
		try {
			AbilityConfig.AbilityData abilityData = configManager.getAbilities().getAbility(abilityKey);

			if (abilityData == null) {
				logger.debug("Ability data not found for key '{}'", abilityKey);
				// Create a basic item with defaults
				ImageIcon icon = iconLoader.loadIcon(abilityKey);
				return new AbilityItem(abilityKey, abilityKey, 0, "Unknown", icon);
			}

			String displayName = getDisplayName(abilityData, abilityKey);
			int level = abilityData.getLevel() != null ? abilityData.getLevel() : 0;
			String type = abilityData.getType() != null ? abilityData.getType() : "Unknown";
			ImageIcon icon = iconLoader.loadIcon(abilityKey);

			return new AbilityItem(abilityKey, displayName, level, type, icon);

		} catch (Exception e) {
			logger.warn("Failed to create ability item for key: {}", abilityKey, e);
			return null;
		}
	}

	/**
	 * Gets the display name for an ability, falling back to the key if needed.
	 */
	private String getDisplayName(AbilityConfig.AbilityData abilityData, String fallbackKey) {
		String commonName = abilityData.getCommonName();
		if (commonName != null && !commonName.isEmpty()) {
			return commonName;
		}
		return fallbackKey;
	}

	/**
	 * Creates a compact visual card for an ability item, matching the palette style.
	 */
	private JPanel createAbilityCard(AbilityItem item) {
		JPanel card = new JPanel();
		card.setLayout(new BoxLayout(card, BoxLayout.Y_AXIS));
		card.setBackground(Color.WHITE);
		card.setBorder(BorderFactory.createLineBorder(Color.LIGHT_GRAY));

		// Compact dimensions: 50x50 icon + ~18px for label
		card.setMinimumSize(new Dimension(50, 68));
		card.setPreferredSize(new Dimension(50, 68));
		card.setMaximumSize(new Dimension(50, 68));

		// Icon (50x50 centered in card)
		JLabel iconLabel = new JLabel(item.getIcon());
		iconLabel.setAlignmentX(Component.CENTER_ALIGNMENT);
		iconLabel.setMinimumSize(new Dimension(50, 50));
		iconLabel.setPreferredSize(new Dimension(50, 50));
		iconLabel.setMaximumSize(new Dimension(50, 50));

		// Name label directly under the icon
		String displayText = truncateText(item.getDisplayName(), 12);
		JLabel nameLabel = new JLabel(displayText);
		nameLabel.setFont(nameLabel.getFont().deriveFont(9f));
		nameLabel.setAlignmentX(Component.CENTER_ALIGNMENT);
		nameLabel.setMinimumSize(new Dimension(50, 16));
		nameLabel.setPreferredSize(new Dimension(50, 16));
		nameLabel.setMaximumSize(new Dimension(50, 16));

		// Add components with minimal gap
		card.add(iconLabel);
		card.add(Box.createRigidArea(new Dimension(0, 2)));
		card.add(nameLabel);

		// Tooltip with ability details
		card.setToolTipText(createTooltipText(item));

		return card;
	}

	/**
	 * Truncates text to the specified length, adding ellipsis if needed.
	 */
	private String truncateText(String text, int maxLength) {
		if (text.length() <= maxLength) {
			return text;
		}
		return text.substring(0, maxLength - 3) + "...";
	}

	/**
	 * Creates tooltip text for an ability item.
	 */
	private String createTooltipText(AbilityItem item) {
		return String.format("<html><b>%s</b><br/>Type: %s<br/>Level: %d</html>",
				item.getDisplayName(),
				item.getType(),
				item.getLevel());
	}

	/**
	 * Creates a separator label (arrow, plus, or slash) to display between ability cards.
	 */
	private JLabel createSeparatorLabel(SequenceElement element) {
		JLabel separator = new JLabel(element.getValue());

		// Style based on separator type
		switch (element.getType()) {
			case ARROW -> {
				separator.setFont(separator.getFont().deriveFont(24f));
				separator.setForeground(Color.RED);
			}
			case PLUS -> {
				separator.setFont(separator.getFont().deriveFont(24f));
				separator.setForeground(new Color(70, 130, 180)); // Steel blue
			}
			case SLASH -> {
				separator.setFont(separator.getFont().deriveFont(24f));
				separator.setForeground(new Color(220, 140, 0)); // Orange for alternatives
			}
			default -> {
				separator.setFont(separator.getFont().deriveFont(16f));
				separator.setForeground(Color.GRAY);
			}
		}

		separator.setAlignmentY(Component.CENTER_ALIGNMENT);
		return separator;
	}

	/**
	 * Custom cell renderer for the sequence list with visual highlighting.
	 */
	private static class SequenceListCellRenderer extends DefaultListCellRenderer {
		@Override
		public Component getListCellRendererComponent(JList<?> list, Object value,
		                                              int index, boolean isSelected, boolean cellHasFocus) {
			JLabel label = (JLabel) super.getListCellRendererComponent(list, value, index, isSelected, cellHasFocus);

			if (isSelected) {
				label.setBackground(new Color(100, 149, 237)); // Cornflower blue
				label.setForeground(Color.WHITE);
				label.setFont(label.getFont().deriveFont(Font.BOLD));
			} else {
				label.setBackground(Color.WHITE);
				label.setForeground(Color.BLACK);
				label.setFont(label.getFont().deriveFont(Font.PLAIN));
			}

			label.setBorder(BorderFactory.createCompoundBorder(
					BorderFactory.createMatteBorder(0, 0, 1, 0, Color.LIGHT_GRAY),
					BorderFactory.createEmptyBorder(5, 10, 5, 10)
			));

			return label;
		}
	}

	/**
	 * Listener for sequence selection events.
	 */
	private class SequenceSelectionListener implements ListSelectionListener {
		@Override
		public void valueChanged(ListSelectionEvent e) {
			if (e.getValueIsAdjusting()) {
				return;
			}

			int selectedIndex = sequenceList.getSelectedIndex();
			if (selectedIndex >= 0) {
				SequenceListModel.SequenceEntry entry = sequenceListModel.getSequenceEntry(selectedIndex);
				loadSequenceDetails(entry);
			} else {
				clearDetailPanel();
			}
		}
	}

	/**
	 * A custom panel that draws a rounded rectangle background to group abilities.
	 */
	private static class AbilityGroupPanel extends JPanel {
		private final Color backgroundColor;
		private final int cornerRadius = 15;

		public AbilityGroupPanel(Color backgroundColor) {
			this.backgroundColor = backgroundColor;
			setLayout(new FlowLayout(FlowLayout.LEFT, 5, 5));
			setOpaque(false); // We are painting our own background
			setBorder(new EmptyBorder(5, 5, 5, 5)); // Add some padding
		}

		@Override
		protected void paintComponent(Graphics g) {
			super.paintComponent(g);
			Graphics2D g2d = (Graphics2D) g.create();
			g2d.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
			g2d.setColor(backgroundColor);
			g2d.fillRoundRect(0, 0, getWidth(), getHeight(), cornerRadius, cornerRadius);
			g2d.dispose();
		}
	}

}