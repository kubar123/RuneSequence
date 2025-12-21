package com.lansoftprogramming.runeSequence.core.sequence.runtime.timing;

/**
 * Read-only snapshot view of the current step timing state.
 * <p>
 * Rules should use this for decisions and emit timing directives rather than mutating timers directly.
 */
public interface StepTimingView {
	long getStepStartTimeMs();
	long getStepDurationMs();
	long getEffectiveElapsedMs(long nowMs);
	boolean isPaused();
}

