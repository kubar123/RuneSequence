package com.lansoftprogramming.runeSequence.infrastructure.config;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.lansoftprogramming.runeSequence.infrastructure.config.dto.PresetAbilitySettings;

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
		private PresetAbilitySettings abilitySettings;

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

		public PresetAbilitySettings getAbilitySettings() {
			return abilitySettings;
		}

		public void setAbilitySettings(PresetAbilitySettings abilitySettings) {
			this.abilitySettings = abilitySettings;
		}
	}
}
