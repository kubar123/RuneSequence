package com.lansoftprogramming.runeSequence.core.sequence.model;

import com.lansoftprogramming.runeSequence.infrastructure.config.AbilityConfig;

import java.util.Objects;
import java.util.Optional;

/**
 * Read-only view of the merged base ability config plus any per-instance overrides.
 */
public class EffectiveAbilityConfig {

	private final String abilityKey;
	private final String type;
	private final Integer level;
	private final boolean triggersGcd;
	private final short castDuration;
	private final short cooldown;
	private final Double detectionThreshold;
	private final String mask;

	private EffectiveAbilityConfig(String abilityKey,
	                               String type,
	                               Integer level,
	                               boolean triggersGcd,
	                               short castDuration,
	                               short cooldown,
	                               Double detectionThreshold,
	                               String mask) {
		this.abilityKey = abilityKey;
		this.type = type;
		this.level = level;
		this.triggersGcd = triggersGcd;
		this.castDuration = castDuration;
		this.cooldown = cooldown;
		this.detectionThreshold = detectionThreshold;
		this.mask = mask;
	}

	public static EffectiveAbilityConfig from(String abilityKey,
	                                          AbilityConfig.AbilityData baseAbility,
	                                          AbilitySettingsOverrides overrides) {
		Objects.requireNonNull(abilityKey, "abilityKey");
		Objects.requireNonNull(baseAbility, "baseAbility");

		AbilitySettingsOverrides effectiveOverrides = overrides != null ? overrides : AbilitySettingsOverrides.empty();

		String type = effectiveOverrides.getTypeOverride().orElse(baseAbility.getType());
		Integer level = sanitizeLevel(effectiveOverrides.getLevelOverride().orElse(baseAbility.getLevel()));
		boolean triggersGcd = effectiveOverrides.getTriggersGcdOverride().orElse(baseAbility.isTriggersGcd());
		short castDuration = sanitizeTiming(effectiveOverrides.getCastDurationOverride().orElse(baseAbility.getCastDuration()));
		short cooldown = sanitizeTiming(effectiveOverrides.getCooldownOverride().orElse(baseAbility.getCooldown()));
		Double detectionThreshold = sanitizeThreshold(effectiveOverrides.getDetectionThresholdOverride().orElse(baseAbility.getDetectionThreshold()));
		String mask = sanitizeMask(effectiveOverrides.getMaskOverride().orElse(baseAbility.getMask()));

		return new EffectiveAbilityConfig(abilityKey, type, level, triggersGcd, castDuration, cooldown,
				detectionThreshold, mask);
	}

	private static Integer sanitizeLevel(Integer level) {
		if (level == null) {
			return null;
		}
		return Math.max(0, level);
	}

	private static short sanitizeTiming(short value) {
		return (short) Math.max(0, value);
	}

	private static Double sanitizeThreshold(Double value) {
		return AbilityValueSanitizers.sanitizeDetectionThreshold(value);
	}

	private static String sanitizeMask(String mask) {
		if (mask == null) {
			return null;
		}
		return mask.trim();
	}

	public String getAbilityKey() {
		return abilityKey;
	}

	public Optional<String> getType() {
		return Optional.ofNullable(type);
	}

	public Optional<Integer> getLevel() {
		return Optional.ofNullable(level);
	}

	public boolean isTriggersGcd() {
		return triggersGcd;
	}

	public short getCastDuration() {
		return castDuration;
	}

	public short getCooldown() {
		return cooldown;
	}

	public Optional<Double> getDetectionThreshold() {
		return Optional.ofNullable(detectionThreshold);
	}

	public Optional<String> getMask() {
		return Optional.ofNullable(mask);
	}
}
