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

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof DropPreview that)) return false;
        return insertIndex == that.insertIndex
            && targetAbilityIndex == that.targetAbilityIndex
            && zoneType == that.zoneType
            && dropSide == that.dropSide;
    }

    @Override
    public int hashCode() {
        int result = Integer.hashCode(insertIndex);
        result = 31 * result + (zoneType != null ? zoneType.hashCode() : 0);
        result = 31 * result + Integer.hashCode(targetAbilityIndex);
        result = 31 * result + (dropSide != null ? dropSide.hashCode() : 0);
        return result;
    }
}
