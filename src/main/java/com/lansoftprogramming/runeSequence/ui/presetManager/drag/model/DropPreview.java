package com.lansoftprogramming.runeSequence.ui.presetManager.drag.model;

/**
 * Represents where and how an ability will be inserted.
 */
public class DropPreview {
    private final int insertIndex; // Index in element list where ability will be inserted
    private final DropZoneType zoneType;
    private final int targetAbilityIndex; // Which ability card were near (-1 if none)

    public DropPreview(int insertIndex, DropZoneType zoneType, int targetAbilityIndex) {
        this.insertIndex = insertIndex;
        this.zoneType = zoneType;
        this.targetAbilityIndex = targetAbilityIndex;
    }

    public int getInsertIndex() {
        return insertIndex;
    }

    public DropZoneType getZoneType() {
        return zoneType;
    }

    public int getTargetAbilityIndex() {
        return targetAbilityIndex;
    }

    public boolean isValid() {
        return zoneType != DropZoneType.NONE;
    }
}