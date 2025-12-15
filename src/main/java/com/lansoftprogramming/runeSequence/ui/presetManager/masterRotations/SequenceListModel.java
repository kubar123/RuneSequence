// File: gui/SequenceListModel.java
package com.lansoftprogramming.runeSequence.ui.presetManager.masterRotations;

import com.lansoftprogramming.runeSequence.infrastructure.config.RotationConfig;

import javax.swing.*;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

public class SequenceListModel extends AbstractListModel<SequenceListModel.SequenceEntry> {

    private final List<SequenceEntry> sequences = new ArrayList<>();

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

        public SequenceEntry withPresetData(RotationConfig.PresetData newPresetData) {
            return new SequenceEntry(this.id, newPresetData);
        }
    }

    public void loadFromConfig(RotationConfig rotations) {
        int oldSize = sequences.size();
        if (oldSize > 0) {
            sequences.clear();
            fireIntervalRemoved(this, 0, oldSize - 1);
        }

        if (rotations != null && rotations.getPresets() != null) {
            for (Map.Entry<String, RotationConfig.PresetData> entry : rotations.getPresets().entrySet()) {
                sequences.add(new SequenceEntry(entry.getKey(), entry.getValue()));
            }
        }

        int newSize = sequences.size();
        if (newSize > 0) {
            fireIntervalAdded(this, 0, newSize - 1);
        }
    }

    @Override
    public int getSize() {
        return sequences.size();
    }

    @Override
    public SequenceEntry getElementAt(int index) {
        return sequences.get(index);
    }

    public void upsert(String id, RotationConfig.PresetData presetData) {
        for (int i = 0; i < sequences.size(); i++) {
            SequenceEntry entry = sequences.get(i);
            if (entry.getId().equals(id)) {
                sequences.set(i, entry.withPresetData(presetData));
                fireContentsChanged(this, i, i);
                return;
            }
        }

        SequenceEntry newEntry = new SequenceEntry(id, presetData);
        sequences.add(newEntry);
        int newIndex = sequences.size() - 1;
        fireIntervalAdded(this, newIndex, newIndex);
    }

    public int indexOf(String id) {
        for (int i = 0; i < sequences.size(); i++) {
            if (sequences.get(i).getId().equals(id)) {
                return i;
            }
        }
        return -1;
    }

    public String commonNameForId(String id) {
        if (id == null || id.isBlank()) {
            return null;
        }
        for (SequenceEntry sequence : sequences) {
            if (!id.equals(sequence.getId())) {
                continue;
            }
            RotationConfig.PresetData presetData = sequence.getPresetData();
            if (presetData == null) {
                return null;
            }
            String name = presetData.getName();
            return name != null && !name.isBlank() ? name : null;
        }
        return null;
    }
}
