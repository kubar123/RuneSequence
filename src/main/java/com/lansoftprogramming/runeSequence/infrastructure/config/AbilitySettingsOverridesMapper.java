package com.lansoftprogramming.runeSequence.infrastructure.config;

import com.lansoftprogramming.runeSequence.core.sequence.model.AbilitySettingsOverrides;
import com.lansoftprogramming.runeSequence.infrastructure.config.dto.PresetAbilityOverrides;
import com.lansoftprogramming.runeSequence.infrastructure.config.dto.PresetAbilitySettings;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Maps between persisted preset override DTOs and domain override objects.
 */
public class AbilitySettingsOverridesMapper {

	public Map<String, AbilitySettingsOverrides> toDomain(PresetAbilitySettings abilitySettings) {
		if (abilitySettings == null || abilitySettings.getPerInstance() == null) {
			return Map.of();
		}
		Map<String, AbilitySettingsOverrides> domain = new LinkedHashMap<>();
		for (Map.Entry<String, PresetAbilityOverrides> entry : abilitySettings.getPerInstance().entrySet()) {
			AbilitySettingsOverrides converted = toDomain(entry.getValue());
			if (converted != null && !converted.isEmpty()) {
				domain.put(entry.getKey(), converted);
			}
		}
		return domain;
	}

	public PresetAbilitySettings toConfig(Map<String, AbilitySettingsOverrides> overridesByLabel) {
		if (overridesByLabel == null || overridesByLabel.isEmpty()) {
			return null;
		}
		Map<String, PresetAbilityOverrides> perInstance = new LinkedHashMap<>();
		for (Map.Entry<String, AbilitySettingsOverrides> entry : overridesByLabel.entrySet()) {
			PresetAbilityOverrides converted = toConfig(entry.getValue());
			if (converted != null) {
				perInstance.put(entry.getKey(), converted);
			}
		}
		if (perInstance.isEmpty()) {
			return null;
		}
		PresetAbilitySettings settings = new PresetAbilitySettings();
		settings.setPerInstance(perInstance);
		return settings;
	}

	public AbilitySettingsOverrides toDomain(PresetAbilityOverrides overrides) {
		if (overrides == null) {
			return null;
		}
		AbilitySettingsOverrides.Builder builder = AbilitySettingsOverrides.builder();
		if (overrides.getType() != null) {
			builder.type(overrides.getType());
		}
		if (overrides.getLevel() != null) {
			builder.level(overrides.getLevel());
		}
		if (overrides.getTriggersGcd() != null) {
			builder.triggersGcd(overrides.getTriggersGcd());
		}
		if (overrides.getCastDuration() != null) {
			builder.castDuration(overrides.getCastDuration());
		}
		if (overrides.getCooldown() != null) {
			builder.cooldown(overrides.getCooldown());
		}
		if (overrides.getDetectionThreshold() != null) {
			builder.detectionThreshold(overrides.getDetectionThreshold());
		}
		if (overrides.getMask() != null) {
			builder.mask(overrides.getMask());
		}
		AbilitySettingsOverrides built = builder.build();
		return built.isEmpty() ? AbilitySettingsOverrides.empty() : built;
	}

	public PresetAbilityOverrides toConfig(AbilitySettingsOverrides overrides) {
		if (overrides == null || overrides.isEmpty()) {
			return null;
		}
		PresetAbilityOverrides config = new PresetAbilityOverrides();
		overrides.getTypeOverride().ifPresent(config::setType);
		overrides.getLevelOverride().ifPresent(config::setLevel);
		overrides.getTriggersGcdOverride().ifPresent(config::setTriggersGcd);
		overrides.getCastDurationOverride().ifPresent(config::setCastDuration);
		overrides.getCooldownOverride().ifPresent(config::setCooldown);
		overrides.getDetectionThresholdOverride().ifPresent(config::setDetectionThreshold);
		overrides.getMaskOverride().ifPresent(config::setMask);
		return config;
	}
}
