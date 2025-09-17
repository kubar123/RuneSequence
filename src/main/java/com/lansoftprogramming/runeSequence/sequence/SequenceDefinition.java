package com.lansoftprogramming.runeSequence.sequence;

import java.util.List;

/**
 * Immutable AST representing a parsed sequence
 */
public class SequenceDefinition {
	private final List<Step> steps;

	public SequenceDefinition(List<Step> steps) {
		this.steps = List.copyOf(steps);
	}

	public List<Step> getSteps() {
		return steps;
	}

	public Step getStep(int index) {
		if (index < 0 || index >= steps.size()) return null;
		return steps.get(index);
	}

	public int size() {
		return steps.size();
	}
}
