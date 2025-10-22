// File: gui/component/SequenceMasterPanel.java
package com.lansoftprogramming.runeSequence.ui.presetManager.masterRotations;

import javax.swing.*;
import javax.swing.border.EmptyBorder;
import javax.swing.event.ListSelectionEvent;
import javax.swing.event.ListSelectionListener;
import java.awt.*;
import java.net.URL;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;

/**
 * A panel that displays a list of sequences and controls for managing them.
 * This acts as the "master" view in a master-detail interface, where selecting an item
 * in this panel can update a "detail" view elsewhere.
 */
public class SequenceMasterPanel extends JPanel {
	private final SequenceListModel sequenceListModel;
	private final JList<SequenceListModel.SequenceEntry> sequenceList;
	private final JButton addButton;
	private final JButton deleteButton;
	private final JButton importButton;
	private final JButton exportButton;

	/** Listeners to be notified when the list selection changes. */
	private final List<Consumer<SequenceListModel.SequenceEntry>> selectionListeners;

	/**
	 * Constructs the master panel for sequence management.
	 * @param sequenceListModel The data model for the list of sequences.
	 */
	public SequenceMasterPanel(SequenceListModel sequenceListModel) {
		this.sequenceListModel = sequenceListModel;
		this.selectionListeners = new ArrayList<>();

		setLayout(new BorderLayout());
		setBorder(new EmptyBorder(10, 10, 10, 10));

		sequenceList = new JList<>(sequenceListModel);
		sequenceList.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
		sequenceList.setCellRenderer(new SequenceListCellRenderer());
		sequenceList.addListSelectionListener(new SequenceSelectionHandler());

		addButton = new JButton("+");

		ImageIcon trashIcon = null;
		try {
			// Load icon from resources
			URL iconUrl = getClass().getResource("/ui/trash-512.png");
			if (iconUrl != null) {
				ImageIcon originalIcon = new ImageIcon(iconUrl);
				Image image = originalIcon.getImage();
				// Scale the icon to fit the button.
				Image scaledImage = image.getScaledInstance(16, 16, Image.SCALE_SMOOTH);
				trashIcon = new ImageIcon(scaledImage);
			}
		} catch (Exception e) {
			e.printStackTrace(); // Log error for debugging
		}

		if (trashIcon != null) {
			deleteButton = new JButton(trashIcon);
		} else {
			deleteButton = new JButton("🗑"); // Fallback to text if icon fails to load
		}

		importButton = new JButton("Import");
		exportButton = new JButton("Export");

		layoutComponents();
	}

	/**
	 * Arranges the control buttons and the sequence list within the panel.
	 */
	private void layoutComponents() {
		JPanel controlsPanel = new JPanel(new GridLayout(2, 2, 5, 5));
		controlsPanel.add(addButton);
		controlsPanel.add(deleteButton);
		controlsPanel.add(importButton);
		controlsPanel.add(exportButton);

		JScrollPane listScrollPane = new JScrollPane(sequenceList);
		listScrollPane.setVerticalScrollBarPolicy(JScrollPane.VERTICAL_SCROLLBAR_ALWAYS);

		add(controlsPanel, BorderLayout.NORTH);
		add(listScrollPane, BorderLayout.CENTER);
	}

	public void addSelectionListener(Consumer<SequenceListModel.SequenceEntry> listener) {
		selectionListeners.add(listener);
	}

	/**
	 * Notifies all registered listeners of a selection change.
	 * @param entry The newly selected sequence entry, or null if selection is cleared.
	 */
	private void notifySelectionListeners(SequenceListModel.SequenceEntry entry) {
		for (Consumer<SequenceListModel.SequenceEntry> listener : selectionListeners) {
			listener.accept(entry);
		}
	}

	/**
	 * Handles selection changes in the sequence list.
	 */
	private class SequenceSelectionHandler implements ListSelectionListener {
		@Override
		public void valueChanged(ListSelectionEvent e) {
			// This check prevents firing the event twice for a single mouse click.
			if (!e.getValueIsAdjusting()) {
				int selectedIndex = sequenceList.getSelectedIndex();
				SequenceListModel.SequenceEntry entry = selectedIndex != -1
						? sequenceListModel.getElementAt(selectedIndex)
						: null;
				notifySelectionListeners(entry);
			}
		}
	}

	/**
	 * Custom renderer to control the appearance of each item in the sequence list.
	 */
	private static class SequenceListCellRenderer implements ListCellRenderer<SequenceListModel.SequenceEntry> {
		private final DefaultListCellRenderer defaultRenderer = new DefaultListCellRenderer();

		@Override
		public Component getListCellRendererComponent(
				JList<? extends SequenceListModel.SequenceEntry> list,
				SequenceListModel.SequenceEntry value,
				int index,
				boolean isSelected,
				boolean cellHasFocus) {

			// Use the default renderer to handle selection colors, etc.
			JLabel label = (JLabel) defaultRenderer.getListCellRendererComponent(
					list, value, index, isSelected, cellHasFocus
			);

			if (value != null) {
				// Display the preset name instead of the object's toString().
				label.setText(value.getPresetData().getName());
			}

			// Add padding for better visual spacing.
			label.setBorder(new EmptyBorder(5, 10, 5, 10));
			return label;
		}
	}
}