package com.lansoftprogramming.runeSequence.infrastructure.config;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.HashMap;
import java.util.Map;

public class RotationConfig {
	@JsonProperty("presets")
	private Map<String, PresetData> presets = new HashMap<>();

	public Map<String, PresetData> getPresets() {
		return presets;
	}

	public void setPresets(Map<String, PresetData> presets) {
		this.presets = presets;
	}

	public static class PresetData {
		@JsonProperty("name")
		private String name;

		@JsonProperty("expression")
		private String expression;

		/**
		 * Optional per-ability settings container for this preset.
		 * Serialized as "ability_settings" when present.
		 */
		@JsonProperty("ability_settings")
		@JsonInclude(JsonInclude.Include.NON_NULL)
		private AbilitySettings abilitySettings;

		public String getName() {
			return name;
		}

		public void setName(String name) {
			this.name = name;
		}

		public String getExpression() {
			return expression;
		}

		public void setExpression(String expression) {
			this.expression = expression;
		}

		public AbilitySettings getAbilitySettings() {
			return abilitySettings;
		}

		public void setAbilitySettings(AbilitySettings abilitySettings) {
			this.abilitySettings = abilitySettings;
		}
	}

	/**
	 * Container for per-ability settings within a preset.
	 * Uses a per-instance map keyed by instanceLabel strings.
	 */
	@JsonInclude(JsonInclude.Include.NON_EMPTY)
	public static class AbilitySettings {
		@JsonProperty("per_instance")
		@JsonInclude(JsonInclude.Include.NON_EMPTY)
		private Map<String, AbilitySettingsOverrides> perInstance;

		public Map<String, AbilitySettingsOverrides> getPerInstance() {
			return perInstance;
		}

		public void setPerInstance(Map<String, AbilitySettingsOverrides> perInstance) {
			this.perInstance = perInstance;
		}
	}

	/**
	 * Overrides for a single ability instance within a preset.
	 * Only non-null fields are serialized, so absence means "no override".
	 */
	@JsonInclude(JsonInclude.Include.NON_NULL)
	public static class AbilitySettingsOverrides {
		@JsonProperty("type")
		private String type;

		@JsonProperty("level")
		private Integer level;

		@JsonProperty("triggers_gcd")
		private Boolean triggersGcd;

		@JsonProperty("cast_duration")
		private Short castDuration;

		@JsonProperty("cooldown")
		private Short cooldown;

		@JsonProperty("detection_threshold")
		private Double detectionThreshold;

		@JsonProperty("mask")
		private String mask;

		public String getType() {
			return type;
		}

		public void setType(String type) {
			this.type = type;
		}

		public Integer getLevel() {
			return level;
		}

		public void setLevel(Integer level) {
			this.level = level;
		}

		public Boolean getTriggersGcd() {
			return triggersGcd;
		}

		public void setTriggersGcd(Boolean triggersGcd) {
			this.triggersGcd = triggersGcd;
		}

		public Short getCastDuration() {
			return castDuration;
		}

		public void setCastDuration(Short castDuration) {
			this.castDuration = castDuration;
		}

		public Short getCooldown() {
			return cooldown;
		}

		public void setCooldown(Short cooldown) {
			this.cooldown = cooldown;
		}

		public Double getDetectionThreshold() {
			return detectionThreshold;
		}

		public void setDetectionThreshold(Double detectionThreshold) {
			this.detectionThreshold = detectionThreshold;
		}

		public String getMask() {
			return mask;
		}

		public void setMask(String mask) {
			this.mask = mask;
		}
	}
}
