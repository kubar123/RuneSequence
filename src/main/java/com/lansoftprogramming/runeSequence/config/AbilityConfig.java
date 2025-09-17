package com.lansoftprogramming.runeSequence.config;

import com.fasterxml.jackson.annotation.JsonAnySetter;
import com.fasterxml.jackson.annotation.JsonAnyGetter;
import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.HashMap;
import java.util.Map;

public class AbilityConfig {
	/*++*/ private Map<String, AbilityData> abilities = new HashMap<>();

	/*++*/
	@JsonAnyGetter
	public Map<String, AbilityData> getAbilities() {
		return abilities;
	}

	/*++*/
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
		@JsonProperty("triggers_gcd")
		private boolean triggersGcd = true;

		@JsonProperty("cast_duration")
		private double castDuration = 0.0;

		@JsonProperty("cooldown")
		private double cooldown = 0.0;

		@JsonProperty("detection_threshold")
		private Double detectionThreshold; // Optional

		// Getters and setters
		public boolean isTriggersGcd() {
			return triggersGcd;
		}

		public void setTriggersGcd(boolean triggersGcd) {
			this.triggersGcd = triggersGcd;
		}

		public double getCastDuration() {
			return castDuration;
		}

		public void setCastDuration(double castDuration) {
			this.castDuration = castDuration;
		}

		public double getCooldown() {
			return cooldown;
		}

		public void setCooldown(double cooldown) {
			this.cooldown = cooldown;
		}

		public Double getDetectionThreshold() {
			return detectionThreshold;
		}

		public void setDetectionThreshold(Double detectionThreshold) {
			this.detectionThreshold = detectionThreshold;
		}
	}
}