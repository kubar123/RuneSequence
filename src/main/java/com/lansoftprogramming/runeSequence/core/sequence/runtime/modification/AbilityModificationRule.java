package com.lansoftprogramming.runeSequence.core.sequence.runtime.modification;

import java.util.Objects;

/**
 * A stateful rule that can open timing windows, manage counters, and apply ability modifications.
 * <p>
 * Rules are invoked for every {@link SequenceEvent} and may emit overrides and timing directives
 * when their conditions are met.
 */
public interface AbilityModificationRule {

	String id();

	void onEvent(SequenceRuntimeContext context, SequenceEvent event, ModifierRuntime runtime);

	default void validate() {
		Objects.requireNonNull(id(), "id");
	}
}

