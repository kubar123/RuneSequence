package com.lansoftprogramming.runeSequence.core.sequence.model;

import java.util.Optional;

/**
 * Represents per-ability overrides. A null/empty field means inherit the base config.
 */
public class AbilitySettingsOverrides {

	private static final AbilitySettingsOverrides EMPTY = new AbilitySettingsOverrides(null, null, null,
			null, null, null, null);

	private final String type;
	private final Integer level;
	private final Boolean triggersGcd;
	private final Short castDuration;
	private final Short cooldown;
	private final Double detectionThreshold;
	private final String mask;

	public AbilitySettingsOverrides(String type,
	                                Integer level,
	                                Boolean triggersGcd,
	                                Short castDuration,
	                                Short cooldown,
	                                Double detectionThreshold,
	                                String mask) {
		this.type = type;
		this.level = level;
		this.triggersGcd = triggersGcd;
		this.castDuration = castDuration;
		this.cooldown = cooldown;
		this.detectionThreshold = detectionThreshold;
		this.mask = mask;
	}

	public static AbilitySettingsOverrides empty() {
		return EMPTY;
	}

	public static Builder builder() {
		return new Builder();
	}

	public Optional<String> getTypeOverride() {
		return Optional.ofNullable(type);
	}

	public Optional<Integer> getLevelOverride() {
		return Optional.ofNullable(level);
	}

	public Optional<Boolean> getTriggersGcdOverride() {
		return Optional.ofNullable(triggersGcd);
	}

	public Optional<Short> getCastDurationOverride() {
		return Optional.ofNullable(castDuration);
	}

	public Optional<Short> getCooldownOverride() {
		return Optional.ofNullable(cooldown);
	}

	public Optional<Double> getDetectionThresholdOverride() {
		return Optional.ofNullable(detectionThreshold);
	}

	public Optional<String> getMaskOverride() {
		return Optional.ofNullable(mask);
	}

	public boolean isEmpty() {
		return type == null &&
				level == null &&
				triggersGcd == null &&
				castDuration == null &&
				cooldown == null &&
				detectionThreshold == null &&
				mask == null;
	}

	public static final class Builder {
		private String type;
		private Integer level;
		private Boolean triggersGcd;
		private Short castDuration;
		private Short cooldown;
		private Double detectionThreshold;
		private String mask;

		public Builder type(String type) {
			this.type = type;
			return this;
		}

		public Builder level(Integer level) {
			this.level = level;
			return this;
		}

		public Builder triggersGcd(Boolean triggersGcd) {
			this.triggersGcd = triggersGcd;
			return this;
		}

		public Builder castDuration(Short castDuration) {
			this.castDuration = castDuration;
			return this;
		}

		public Builder cooldown(Short cooldown) {
			this.cooldown = cooldown;
			return this;
		}

		public Builder detectionThreshold(Double detectionThreshold) {
			this.detectionThreshold = detectionThreshold;
			return this;
		}

		public Builder mask(String mask) {
			this.mask = mask;
			return this;
		}

		public AbilitySettingsOverrides build() {
			return new AbilitySettingsOverrides(type, level, triggersGcd, castDuration, cooldown,
					detectionThreshold, mask);
		}
	}
}
