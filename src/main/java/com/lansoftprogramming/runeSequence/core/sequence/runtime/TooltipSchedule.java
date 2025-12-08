package com.lansoftprogramming.runeSequence.core.sequence.runtime;

import java.util.Collections;
import java.util.List;
import java.util.Map;

/**
 * Immutable schedule mapping sequence steps to tooltip messages.
 * <p>
 * The schedule is built once per sequence and queried by {@code SequenceManager}
 * to expose tooltips for the current step without affecting detection logic.
 */
public class TooltipSchedule {

	private static final TooltipSchedule EMPTY = new TooltipSchedule(Collections.emptyMap());

	private final Map<Integer, List<SequenceTooltip>> tooltipsByStep;

	public TooltipSchedule(Map<Integer, List<SequenceTooltip>> tooltipsByStep) {
		this.tooltipsByStep = tooltipsByStep != null
				? Map.copyOf(tooltipsByStep)
				: Collections.emptyMap();
	}

	public List<SequenceTooltip> getTooltipsForStep(int stepIndex) {
		if (stepIndex < 0) {
			return List.of();
		}
		List<SequenceTooltip> list = tooltipsByStep.get(stepIndex);
		return list != null ? List.copyOf(list) : List.of();
	}

	public static TooltipSchedule empty() {
		return EMPTY;
	}
}

