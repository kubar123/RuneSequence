package com.lansoftprogramming.runeSequence.config;

import javax.swing.*;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * Model for managing the list of sequences displayed in the PresetManager.
 * Wraps sequence data with ID references for proper management.
 */
public class SequenceListModel extends AbstractListModel<String> {
	private final List<SequenceEntry> sequences;

	public SequenceListModel() {
		this.sequences = new ArrayList<>();
	}

	/**
	 * Loads sequences from RotationConfig.
	 * @param rotationConfig The configuration containing preset data
	 */
	public void loadFromConfig(RotationConfig rotationConfig) {
		sequences.clear();

		Map<String, RotationConfig.PresetData> presets = rotationConfig.getPresets();
		for (Map.Entry<String, RotationConfig.PresetData> entry : presets.entrySet()) {
			sequences.add(new SequenceEntry(entry.getKey(), entry.getValue()));
		}

		fireContentsChanged(this, 0, sequences.size() - 1);
	}

	/**
	 * Gets the sequence entry at the specified index.
	 * @param index The index
	 * @return The SequenceEntry
	 */
	public SequenceEntry getSequenceEntry(int index) {
		return sequences.get(index);
	}

	@Override
	public int getSize() {
		return sequences.size();
	}

	@Override
	public String getElementAt(int index) {
		return sequences.get(index).getPresetData().getName();
	}

	/**
	 * Inner class representing a sequence entry with ID and data.
	 */
	public static class SequenceEntry {
		private final String id;
		private final RotationConfig.PresetData presetData;

		public SequenceEntry(String id, RotationConfig.PresetData presetData) {
			this.id = id;
			this.presetData = presetData;
		}

		public String getId() {
			return id;
		}

		public RotationConfig.PresetData getPresetData() {
			return presetData;
		}
	}
}
