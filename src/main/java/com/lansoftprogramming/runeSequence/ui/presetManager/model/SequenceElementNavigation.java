package com.lansoftprogramming.runeSequence.ui.presetManager.model;

import java.util.List;

public final class SequenceElementNavigation {
	private SequenceElementNavigation() {
	}

	public static int previousNonTooltipIndex(List<SequenceElement> elements, int fromIndexInclusive) {
		if (elements == null || elements.isEmpty()) {
			return -1;
		}
		int index = Math.min(fromIndexInclusive, elements.size() - 1);
		while (index >= 0) {
			if (!elements.get(index).isTooltip()) {
				return index;
			}
			index--;
		}
		return -1;
	}

	public static int nextNonTooltipIndex(List<SequenceElement> elements, int fromIndexInclusive) {
		if (elements == null || elements.isEmpty()) {
			return -1;
		}
		int index = Math.max(0, fromIndexInclusive);
		while (index < elements.size()) {
			if (!elements.get(index).isTooltip()) {
				return index;
			}
			index++;
		}
		return -1;
	}
}

