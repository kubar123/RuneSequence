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
}