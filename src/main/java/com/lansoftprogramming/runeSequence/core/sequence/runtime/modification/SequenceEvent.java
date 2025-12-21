package com.lansoftprogramming.runeSequence.core.sequence.runtime.modification;

/**
 * Events emitted by the sequence runtime that rules can respond to.
 */
public sealed interface SequenceEvent permits SequenceEvent.Heartbeat,
		SequenceEvent.SequenceInitialized,
		SequenceEvent.SequenceReset,
		SequenceEvent.SequencePaused,
		SequenceEvent.SequenceResumed,
		SequenceEvent.StepStarted,
		SequenceEvent.StepAdvanced,
		SequenceEvent.LatchStarted,
		SequenceEvent.AbilityDetected,
		SequenceEvent.AbilityUsed {

	record Heartbeat() implements SequenceEvent {
	}

	record SequenceInitialized() implements SequenceEvent {
	}

	record SequenceReset() implements SequenceEvent {
	}

	record SequencePaused() implements SequenceEvent {
	}

	record SequenceResumed() implements SequenceEvent {
	}

	record StepStarted(int stepIndex) implements SequenceEvent {
	}

	record StepAdvanced(int fromStepIndex, int toStepIndex) implements SequenceEvent {
	}

	record LatchStarted(long latchTimeMs) implements SequenceEvent {
	}

	record AbilityDetected(String abilityKey, String instanceId, StepPosition position) implements SequenceEvent {
	}

	/**
	 * Represents an "ability used" signal (e.g. cooldown darken / latch-style detection),
	 * distinct from {@link AbilityDetected} which is based on template-found edges.
	 */
	record AbilityUsed(String abilityKey, String instanceId, StepPosition position) implements SequenceEvent {
	}
}
