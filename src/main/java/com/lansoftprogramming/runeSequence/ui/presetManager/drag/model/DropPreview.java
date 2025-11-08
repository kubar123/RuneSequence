package com.lansoftprogramming.runeSequence.ui.presetManager.drag.model;

/**
 * Represents where and how an ability will be inserted.
 */
public class DropPreview {
    private final int insertIndex; // Index in element list where ability will be inserted
    private final DropZoneType zoneType;
    private final int targetAbilityIndex; // Which ability card were near (-1 if none)
    private final DropSide dropSide; // Which side of the card (LEFT or RIGHT)

    public DropPreview(int insertIndex, DropZoneType zoneType, int targetAbilityIndex, DropSide dropSide) {
        this.insertIndex = insertIndex;
        this.zoneType = zoneType;
        this.targetAbilityIndex = targetAbilityIndex;
        this.dropSide = dropSide;
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

    public DropSide getDropSide() {
        return dropSide;
    }

    public boolean isValid() {
        return zoneType != DropZoneType.NONE;
    }
}