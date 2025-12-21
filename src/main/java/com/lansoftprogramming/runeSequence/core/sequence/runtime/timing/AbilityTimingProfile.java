package com.lansoftprogramming.runeSequence.core.sequence.runtime.timing;

import com.lansoftprogramming.runeSequence.core.sequence.model.EffectiveAbilityConfig;

import java.util.Objects;

/**
 * Timing-only view of an ability used for runtime step/GCD calculations.
 * <p>
 * This intentionally models only timing-relevant properties so that runtime modifiers can
 * apply temporary changes without mutating persisted ability configs.
 */
public record AbilityTimingProfile(String abilityKey,
                                   boolean triggersGcd,
                                   short castDurationTicks,
                                   short cooldownTicks,
                                   Short gcdTicksOverride) {

	public AbilityTimingProfile {
		Objects.requireNonNull(abilityKey, "abilityKey");
		castDurationTicks = sanitizeTicks(castDurationTicks);
		cooldownTicks = sanitizeTicks(cooldownTicks);
		if (gcdTicksOverride != null) {
			gcdTicksOverride = sanitizeTicks(gcdTicksOverride);
		}
	}

	public static AbilityTimingProfile from(EffectiveAbilityConfig effectiveConfig) {
		Objects.requireNonNull(effectiveConfig, "effectiveConfig");
		return new AbilityTimingProfile(
				effectiveConfig.getAbilityKey(),
				effectiveConfig.isTriggersGcd(),
				effectiveConfig.getCastDuration(),
				effectiveConfig.getCooldown(),
				null
		);
	}

	private static short sanitizeTicks(short ticks) {
		return (short) Math.max(0, ticks);
	}

	private static short sanitizeTicks(Short ticks) {
		if (ticks == null) {
			return 0;
		}
		return (short) Math.max(0, ticks.shortValue());
	}
}

