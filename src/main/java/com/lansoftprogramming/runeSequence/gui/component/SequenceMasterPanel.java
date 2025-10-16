// File: gui/component/SequenceMasterPanel.java
package com.lansoftprogramming.runeSequence.gui.component;

import com.lansoftprogramming.runeSequence.config.SequenceListModel;

import javax.swing.*;
import javax.swing.border.EmptyBorder;
import javax.swing.event.ListSelectionEvent;
import javax.swing.event.ListSelectionListener;
import java.awt.*;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;

public class SequenceMasterPanel extends JPanel {
	private final SequenceListModel sequenceListModel;
	private final JList<SequenceListModel.SequenceEntry> sequenceList;
	private final JButton addButton;
	private final JButton deleteButton;
	private final JButton importButton;
	private final JButton exportButton;

	private final List<Consumer<SequenceListModel.SequenceEntry>> selectionListeners;

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
		deleteButton = new JButton("🗑");
		importButton = new JButton("Import");
		exportButton = new JButton("Export");

		layoutComponents();
	}

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

	private void notifySelectionListeners(SequenceListModel.SequenceEntry entry) {
		for (Consumer<SequenceListModel.SequenceEntry> listener : selectionListeners) {
			listener.accept(entry);
		}
	}

	private class SequenceSelectionHandler implements ListSelectionListener {
		@Override
		public void valueChanged(ListSelectionEvent e) {
			if (!e.getValueIsAdjusting()) {
				int selectedIndex = sequenceList.getSelectedIndex();
				SequenceListModel.SequenceEntry entry = selectedIndex != -1
						? sequenceListModel.getElementAt(selectedIndex)
						: null;
				notifySelectionListeners(entry);
			}
		}
	}

	private static class SequenceListCellRenderer implements ListCellRenderer<SequenceListModel.SequenceEntry> {
		private final DefaultListCellRenderer defaultRenderer = new DefaultListCellRenderer();

		@Override
		public Component getListCellRendererComponent(
				JList<? extends SequenceListModel.SequenceEntry> list,
				SequenceListModel.SequenceEntry value,
				int index,
				boolean isSelected,
				boolean cellHasFocus) {

			JLabel label = (JLabel) defaultRenderer.getListCellRendererComponent(
					list, value, index, isSelected, cellHasFocus
			);

			if (value != null) {
				label.setText(value.getPresetData().getName());
			}

			label.setBorder(new EmptyBorder(5, 10, 5, 10));
			return label;
		}
	}
}