package com.lansoftprogramming.runeSequence.gui;

import com.lansoftprogramming.runeSequence.config.*;
import com.lansoftprogramming.runeSequence.gui.component.AbilityPalettePanel;
import com.lansoftprogramming.runeSequence.gui.service.AbilityIconLoader;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.swing.*;
import javax.swing.border.EmptyBorder;
import javax.swing.event.ListSelectionEvent;
import javax.swing.event.ListSelectionListener;
import java.awt.*;

public class PresetManagerWindow extends JFrame {
	private static final Logger logger = LoggerFactory.getLogger(PresetManagerWindow.class);

	// Dependencies
	private final ConfigManager configManager;

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
			AbilityIconLoader iconLoader = new AbilityIconLoader(
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

		// TODO: Parse and display expression as ability cards
		abilityFlowPanel.removeAll();
		JLabel expressionLabel = new JLabel("Expression: " + presetData.getExpression());
		abilityFlowPanel.add(expressionLabel);
		abilityFlowPanel.revalidate();
		abilityFlowPanel.repaint();

		logger.debug("Loaded sequence: {} ({})", presetData.getName(), entry.getId());
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

	public static void main(String[] args) {
		SwingUtilities.invokeLater(() -> {
			try {
				UIManager.setLookAndFeel(UIManager.getSystemLookAndFeelClassName());
				ConfigManager configManager = new ConfigManager();
				configManager.initialize();
				new PresetManagerWindow(configManager);
			} catch (Exception e) {
				e.printStackTrace();
			}
		});
	}
}