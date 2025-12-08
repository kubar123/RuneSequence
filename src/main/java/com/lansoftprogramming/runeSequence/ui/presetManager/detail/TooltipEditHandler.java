package com.lansoftprogramming.runeSequence.ui.presetManager.detail;

/**
 * Callback used by the flow view to request tooltip text editing.
 */
@FunctionalInterface
public interface TooltipEditHandler {

	void editTooltipAt(int elementIndex);
}

