package com.lansoftprogramming.runeSequence.core.sequence.runtime;

/**
 * Runtime representation of a tooltip message attached to a sequence.
 * <p>
 * Tooltips are display-only annotations. They do not participate in detection,
 * timing, or GCD logic and are only used by UI overlays.
 */
public record SequenceTooltip(int stepIndex, String abilityInstanceId, String message) {
}

