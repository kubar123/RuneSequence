package com.lansoftprogramming.runeSequence.core.sequence.runtime.modification;

import com.lansoftprogramming.runeSequence.core.sequence.runtime.timing.AbilityTimingProfile;

/**
 * Optional overrides applied to an {@link AbilityTimingProfile}.
 * <p>
 * Null fields mean "leave unchanged".
 */
public record AbilityOverridePatch(Boolean triggersGcd,
                                  Short castDurationTicks,
                                  Short cooldownTicks,
                                  Short gcdTicksOverride) {

	public AbilityTimingProfile applyTo(AbilityTimingProfile base) {
		if (base == null) {
			return null;
		}
		boolean nextTriggersGcd = triggersGcd != null ? triggersGcd.booleanValue() : base.triggersGcd();
		short nextCastDuration = castDurationTicks != null ? sanitizeTicks(castDurationTicks) : base.castDurationTicks();
		short nextCooldown = cooldownTicks != null ? sanitizeTicks(cooldownTicks) : base.cooldownTicks();
		Short nextGcdOverride = gcdTicksOverride != null ? sanitizeTicksBoxed(gcdTicksOverride) : base.gcdTicksOverride();
		return new AbilityTimingProfile(base.abilityKey(), nextTriggersGcd, nextCastDuration, nextCooldown, nextGcdOverride);
	}

	private static short sanitizeTicks(Short ticks) {
		if (ticks == null) {
			return 0;
		}
		return (short) Math.max(0, ticks.shortValue());
	}

	private static Short sanitizeTicksBoxed(Short ticks) {
		if (ticks == null) {
			return null;
		}
		return (short) Math.max(0, ticks.shortValue());
	}
}

