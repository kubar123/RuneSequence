package com.lansoftprogramming.runeSequence.core.sequence.runtime.modification;

import com.lansoftprogramming.runeSequence.core.sequence.runtime.SequenceTooltip;

import java.util.Optional;

/**
 * Rule-facing runtime API for managing windows, counters, overrides, tooltips, and timing directives.
 * <p>
 * Implementations should scope ids per rule to avoid collisions.
 */
public interface ModifierRuntime {

	TimingWindow openWindow(String windowId, long durationMs);

	Optional<TimingWindow> getWindow(String windowId);

	void closeWindow(String windowId);

	TickCounter startTickCounter(String counterId, int ticks, long tickMs);

	Optional<TickCounter> getTickCounter(String counterId);

	void stopTickCounter(String counterId);

	void putOverride(AbilityOverrideSpec spec);

	void removeOverride(String overrideId);

	void putTooltip(String tooltipId, SequenceTooltip tooltip, String activeWhileWindowId, Long expiresAtMs);

	void removeTooltip(String tooltipId);

	void emitTimingDirective(TimingDirective directive);
}

