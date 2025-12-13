package com.lansoftprogramming.runeSequence.infrastructure.config.dto;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.Map;

/**
 * Container for per-ability overrides within a preset.
 */
@JsonInclude(JsonInclude.Include.NON_EMPTY)
@JsonIgnoreProperties(ignoreUnknown = true)
public class PresetAbilitySettings {
	@JsonProperty("per_instance")
	@JsonInclude(JsonInclude.Include.NON_EMPTY)
	private Map<String, PresetAbilityOverrides> perInstance;

	@JsonProperty("per_ability")
	@JsonInclude(JsonInclude.Include.NON_EMPTY)
	private Map<String, PresetAbilityOverrides> perAbility;

	@JsonProperty("rotation_defaults")
	@JsonInclude(JsonInclude.Include.NON_EMPTY)
	private PresetRotationDefaults rotationDefaults;

	public Map<String, PresetAbilityOverrides> getPerInstance() {
		return perInstance;
	}

	public void setPerInstance(Map<String, PresetAbilityOverrides> perInstance) {
		this.perInstance = perInstance;
	}

	public Map<String, PresetAbilityOverrides> getPerAbility() {
		return perAbility;
	}

	public void setPerAbility(Map<String, PresetAbilityOverrides> perAbility) {
		this.perAbility = perAbility;
	}

	public PresetRotationDefaults getRotationDefaults() {
		return rotationDefaults;
	}

	public void setRotationDefaults(PresetRotationDefaults rotationDefaults) {
		this.rotationDefaults = rotationDefaults;
	}

	@JsonIgnore
	public boolean isEmpty() {
		boolean perInstanceEmpty = perInstance == null || perInstance.isEmpty();
		boolean perAbilityEmpty = perAbility == null || perAbility.isEmpty();
		boolean rotationEmpty = rotationDefaults == null || rotationDefaults.isEmpty();
		return perInstanceEmpty && perAbilityEmpty && rotationEmpty;
	}
}