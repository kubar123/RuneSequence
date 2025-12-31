package com.lansoftprogramming.runeSequence.ui.presetManager.model;

import java.util.List;

import static com.lansoftprogramming.runeSequence.ui.presetManager.model.SequenceElementNavigation.nextNonTooltipIndex;
import static com.lansoftprogramming.runeSequence.ui.presetManager.model.SequenceElementNavigation.previousNonTooltipIndex;

public final class SequenceGrouping {
	private SequenceGrouping() {
	}

	public record AbilityRange(int start, int end) {
		public boolean isValid() {
			return start >= 0 && end >= start;
		}
	}

	public static AbilityRange computeGroupAbilityRange(List<SequenceElement> elements,
	                                                   int targetElementIndex,
	                                                   SequenceElement.Type separatorType) {
		if (separatorType == null || elements == null || targetElementIndex < 0 || targetElementIndex >= elements.size()) {
			return new AbilityRange(-1, -1);
		}

		int start = targetElementIndex;
		int end = targetElementIndex;

		int cursor = previousNonTooltipIndex(elements, targetElementIndex - 1);
		while (cursor != -1) {
			SequenceElement element = elements.get(cursor);
			if (element.getType() != separatorType) {
				break;
			}
			int abilityIndex = previousNonTooltipIndex(elements, cursor - 1);
			if (abilityIndex == -1 || !elements.get(abilityIndex).isAbility()) {
				break;
			}
			start = abilityIndex;
			cursor = previousNonTooltipIndex(elements, abilityIndex - 1);
		}

		cursor = nextNonTooltipIndex(elements, targetElementIndex + 1);
		while (cursor != -1) {
			SequenceElement element = elements.get(cursor);
			if (element.getType() != separatorType) {
				break;
			}
			int abilityIndex = nextNonTooltipIndex(elements, cursor + 1);
			if (abilityIndex == -1 || !elements.get(abilityIndex).isAbility()) {
				break;
			}
			end = abilityIndex;
			cursor = nextNonTooltipIndex(elements, abilityIndex + 1);
		}

		return new AbilityRange(start, end);
	}
}
