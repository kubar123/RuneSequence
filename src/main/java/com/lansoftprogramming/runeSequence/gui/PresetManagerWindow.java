package com.lansoftprogramming.runeSequence.gui;

import javax.swing.*;
import javax.swing.border.EmptyBorder;
import java.awt.*;

public class PresetManagerWindow extends JFrame {

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
	private JTextField searchField;
	private JTabbedPane categoryTabs;

	// Split Panes
	private JSplitPane verticalSplit;
	private JSplitPane horizontalSplit;

	public PresetManagerWindow() {
		initializeFrame();
		initializeComponents();
		layoutComponents();
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
		sequenceList = new JList<>(new DefaultListModel<>());
		sequenceList.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);

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
		searchField = new JTextField();
		searchField.setToolTipText("Search abilities...");

		categoryTabs = new JTabbedPane();

		// Placeholder tabs for 5+ categories
		for (int i = 1; i <= 5; i++) {
			JPanel categoryPanel = new JPanel(new GridLayout(0, 3, 5, 5));
			JScrollPane scrollPane = new JScrollPane(categoryPanel);
			scrollPane.setVerticalScrollBarPolicy(JScrollPane.VERTICAL_SCROLLBAR_ALWAYS);
			categoryTabs.addTab("Category " + i, scrollPane);

			// Placeholder ability items
			for (int j = 1; j <= 50; j++) {
				JPanel abilityCard = new JPanel(new BorderLayout());
				abilityCard.setBorder(BorderFactory.createLineBorder(Color.GRAY));
				abilityCard.setPreferredSize(new Dimension(100, 80));
				JLabel abilityLabel = new JLabel("Ability " + ((i-1)*50 + j), SwingConstants.CENTER);
				abilityCard.add(abilityLabel, BorderLayout.CENTER);
				categoryPanel.add(abilityCard);
			}
		}
	}

	private void layoutComponents() {
		JPanel masterPanel = createMasterPanel();
		JPanel detailPanel = createDetailPanel();
		JPanel palettePanel = createPalettePanel();

		// Horizontal split: Master (left 25%) | Palette (right 75%)
		horizontalSplit = new JSplitPane(JSplitPane.HORIZONTAL_SPLIT, masterPanel, palettePanel);
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

	private JPanel createPalettePanel() {
		JPanel palettePanel = new JPanel(new BorderLayout());
		palettePanel.setBorder(new EmptyBorder(10, 10, 10, 10));

		// Search bar at top
		JPanel searchPanel = new JPanel(new BorderLayout(5, 0));
		searchPanel.add(new JLabel("Search:"), BorderLayout.WEST);
		searchPanel.add(searchField, BorderLayout.CENTER);
		searchPanel.setBorder(new EmptyBorder(0, 0, 10, 0));

		palettePanel.add(searchPanel, BorderLayout.NORTH);
		palettePanel.add(categoryTabs, BorderLayout.CENTER);

		return palettePanel;
	}

	public static void main(String[] args) {
		SwingUtilities.invokeLater(() -> {
			try {
				UIManager.setLookAndFeel(UIManager.getSystemLookAndFeelClassName());
			} catch (Exception e) {
				e.printStackTrace();
			}
			new PresetManagerWindow();
		});
	}
}