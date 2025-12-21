package com.lansoftprogramming.runeSequence.core.sequence.runtime.modification;

/**
 * Commands emitted by modifier rules to adjust runtime timing without directly mutating timers.
 */
public sealed interface TimingDirective permits TimingDirective.RestartStepAt, TimingDirective.SetStepDurationMs, TimingDirective.ForceStepSatisfiedAt, TimingDirective.PauseStepTimer, TimingDirective.ResumeStepTimer {

	record RestartStepAt(long startTimeMs) implements TimingDirective {
	}

	record SetStepDurationMs(long durationMs) implements TimingDirective {
	}

	record ForceStepSatisfiedAt(long nowMs) implements TimingDirective {
	}

	record PauseStepTimer() implements TimingDirective {
	}

	record ResumeStepTimer() implements TimingDirective {
	}
}
