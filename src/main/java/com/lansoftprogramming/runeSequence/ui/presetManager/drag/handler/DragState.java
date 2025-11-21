package com.lansoftprogramming.runeSequence.ui.presetManager.drag.handler;

import com.lansoftprogramming.runeSequence.ui.presetManager.model.SequenceElement;
import com.lansoftprogramming.runeSequence.ui.shared.model.AbilityItem;

import javax.swing.*;
import java.util.List;

/**
 * Tracks the current drag operation state.
 */
public class DragState {
    private final AbilityItem draggedItem;
    private final JPanel draggedCard;
    private final boolean isFromPalette;
    private final int originalIndex; // -1 if from palette
    private final List<SequenceElement> originalElements;
    private final int startButton;

    public DragState(AbilityItem item,
                     JPanel card,
                     boolean isFromPalette,
                     int originalIndex,
                     List<SequenceElement> originalElements,
                     int startButton) {
        this.draggedItem = item;
        this.draggedCard = card;
        this.isFromPalette = isFromPalette;
        this.originalIndex = originalIndex;
        this.originalElements = List.copyOf(originalElements);
        this.startButton = startButton;
    }

    public AbilityItem getDraggedItem() {
        return draggedItem;
    }

    public JPanel getDraggedCard() {
        return draggedCard;
    }

    public boolean isFromPalette() {
        return isFromPalette;
    }

    public int getOriginalIndex() {
        return originalIndex;
    }

    public List<SequenceElement> getOriginalElements() {
        return originalElements;
    }

    public int getStartButton() {
        return startButton;
    }
}
