package com.lansoftprogramming.runeSequence.core.sequence.model;

import java.util.Objects;
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

	/**
	 * Merges two overrides objects into a single object where non-null fields from {@code delta}
	 * take precedence over non-null fields from {@code base}.
	 * <p>
	 * Returns {@code null} when the merged result has no overrides (all fields null).
	 */
	public static AbilitySettingsOverrides merge(AbilitySettingsOverrides base, AbilitySettingsOverrides delta) {
		AbilitySettingsOverrides left = base != null ? base : empty();
		AbilitySettingsOverrides right = delta != null ? delta : empty();
		if (left.isEmpty() && right.isEmpty()) {
			return null;
		}

		Builder builder = builder();
		builder.type(right.getTypeOverride().orElse(left.getTypeOverride().orElse(null)));
		builder.level(right.getLevelOverride().orElse(left.getLevelOverride().orElse(null)));
		builder.triggersGcd(right.getTriggersGcdOverride().orElse(left.getTriggersGcdOverride().orElse(null)));
		builder.castDuration(right.getCastDurationOverride().orElse(left.getCastDurationOverride().orElse(null)));
		builder.cooldown(right.getCooldownOverride().orElse(left.getCooldownOverride().orElse(null)));
		builder.detectionThreshold(right.getDetectionThresholdOverride().orElse(left.getDetectionThresholdOverride().orElse(null)));
		builder.mask(right.getMaskOverride().orElse(left.getMaskOverride().orElse(null)));

		AbilitySettingsOverrides merged = builder.build();
		return merged.isEmpty() ? null : merged;
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

	@Override
	public boolean equals(Object o) {
		if (this == o) return true;
		if (o == null || getClass() != o.getClass()) return false;

		AbilitySettingsOverrides that = (AbilitySettingsOverrides) o;

		if (!Objects.equals(type, that.type)) return false;
		if (!Objects.equals(level, that.level)) return false;
		if (!Objects.equals(triggersGcd, that.triggersGcd)) return false;
		if (!Objects.equals(castDuration, that.castDuration)) return false;
		if (!Objects.equals(cooldown, that.cooldown)) return false;
		if (!Objects.equals(detectionThreshold, that.detectionThreshold)) return false;
		return Objects.equals(mask, that.mask);
	}

	@Override
	public int hashCode() {
		int result = type != null ? type.hashCode() : 0;
		result = 31 * result + (level != null ? level.hashCode() : 0);
		result = 31 * result + (triggersGcd != null ? triggersGcd.hashCode() : 0);
		result = 31 * result + (castDuration != null ? castDuration.hashCode() : 0);
		result = 31 * result + (cooldown != null ? cooldown.hashCode() : 0);
		result = 31 * result + (detectionThreshold != null ? detectionThreshold.hashCode() : 0);
		result = 31 * result + (mask != null ? mask.hashCode() : 0);
		return result;
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
