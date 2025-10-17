package com.lansoftprogramming.runeSequence.infrastructure.config;

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
	}
}