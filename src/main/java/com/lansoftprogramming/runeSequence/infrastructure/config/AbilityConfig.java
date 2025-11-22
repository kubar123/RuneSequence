package com.lansoftprogramming.runeSequence.infrastructure.config;

import com.fasterxml.jackson.annotation.JsonAnyGetter;
import com.fasterxml.jackson.annotation.JsonAnySetter;
import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.HashMap;
import java.util.Map;

public class AbilityConfig {
	private Map<String, AbilityData> abilities = new HashMap<>();


	@JsonAnyGetter
	public Map<String, AbilityData> getAbilities() {
		return abilities;
	}


	@JsonAnySetter
	public void setAbility(String name, AbilityData data) {
		abilities.put(name, data);
	}

	// Convenience methods
	public AbilityData getAbility(String name) {
		return abilities.get(name);
	}

	public void putAbility(String name, AbilityData data) {
		abilities.put(name, data);
	}

	public static class AbilityData {
		@JsonProperty("common_name")
		private String commonName;

		@JsonProperty("type")
		private String type;

		@JsonProperty("level")
		private Integer level;

		@JsonProperty("triggers_gcd")
		private boolean triggersGcd = true;

		@JsonProperty("cast_duration")
		private short castDuration = 0;

		@JsonProperty("cooldown")
		private short cooldown = 0;

		@JsonProperty("detection_threshold")
		private Double detectionThreshold;

		@JsonProperty("mask")
		private String mask;

		// Getters and setters
		public String getCommonName() {
			return commonName;
		}

		public void setCommonName(String commonName) {
			this.commonName = commonName;
		}

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

		public boolean isTriggersGcd() {
			return triggersGcd;
		}

		public void setTriggersGcd(boolean triggersGcd) {
			this.triggersGcd = triggersGcd;
		}

		public short getCastDuration() {
			return castDuration;
		}

		public void setCastDuration(short castDuration) {
			this.castDuration = castDuration;
		}

		public short getCooldown() {
			return cooldown;
		}

		public void setCooldown(short cooldown) {
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