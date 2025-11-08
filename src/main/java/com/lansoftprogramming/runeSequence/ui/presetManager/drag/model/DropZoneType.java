package com.lansoftprogramming.runeSequence.ui.presetManager.drag.model;

/**
 * Defines the type of drop zone when hovering near an ability.
 */
public enum DropZoneType {
    AND,    // Top zone - creates/appends to + group
    OR,     // Bottom zone - creates/appends to / group
    NEXT,   // Middle/far zone - inserts as next step with ->
    NONE    // No valid drop zone
}