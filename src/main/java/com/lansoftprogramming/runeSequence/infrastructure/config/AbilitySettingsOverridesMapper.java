package com.lansoftprogramming.runeSequence.infrastructure.config;

import com.lansoftprogramming.runeSequence.core.sequence.model.AbilitySettingsOverrides;
import com.lansoftprogramming.runeSequence.core.sequence.model.AbilityValueSanitizers;
import com.lansoftprogramming.runeSequence.core.validation.FilenameValidators;
import com.lansoftprogramming.runeSequence.infrastructure.config.dto.PresetAbilityOverrides;
import com.lansoftprogramming.runeSequence.infrastructure.config.dto.PresetAbilitySettings;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Maps between persisted preset override DTOs and domain override objects.
 */
public class AbilitySettingsOverridesMapper {
	private static final Logger logger = LoggerFactory.getLogger(AbilitySettingsOverridesMapper.class);

	public Map<String, AbilitySettingsOverrides> toDomain(PresetAbilitySettings abilitySettings) {
		return toDomainMap(abilitySettings != null ? abilitySettings.getPerInstance() : null);
	}

	public Map<String, AbilitySettingsOverrides> toDomainPerAbility(PresetAbilitySettings abilitySettings) {
		return toDomainMap(abilitySettings != null ? abilitySettings.getPerAbility() : null);
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

	public Map<String, PresetAbilityOverrides> toConfigMap(Map<String, AbilitySettingsOverrides> overridesByKey) {
		if (overridesByKey == null || overridesByKey.isEmpty()) {
			return null;
		}
		Map<String, PresetAbilityOverrides> out = new LinkedHashMap<>();
		for (Map.Entry<String, AbilitySettingsOverrides> entry : overridesByKey.entrySet()) {
			PresetAbilityOverrides converted = toConfig(entry.getValue());
			if (converted != null) {
				out.put(entry.getKey(), converted);
			}
		}
		return out.isEmpty() ? null : out;
	}

	public AbilitySettingsOverrides toDomain(PresetAbilityOverrides overrides) {
		return toDomain(overrides, null);
	}

	private AbilitySettingsOverrides toDomain(PresetAbilityOverrides overrides, String sourceKey) {
		if (overrides == null) {
			return null;
		}
		AbilitySettingsOverrides.Builder builder = AbilitySettingsOverrides.builder();
		if (overrides.getType() != null) {
			String type = overrides.getType().trim();
			if (!type.isEmpty()) {
				builder.type(type);
			} else {
				warnIgnored(sourceKey, "type", overrides.getType(), "blank");
			}
		}
		if (overrides.getLevel() != null) {
			Integer level = overrides.getLevel();
			if (level >= 0) {
				builder.level(level);
			} else {
				warnIgnored(sourceKey, "level", level, "negative");
			}
		}
		if (overrides.getTriggersGcd() != null) {
			builder.triggersGcd(overrides.getTriggersGcd());
		}
		if (overrides.getCastDuration() != null) {
			Short castDuration = overrides.getCastDuration();
			if (castDuration >= 0) {
				builder.castDuration(castDuration);
			} else {
				warnIgnored(sourceKey, "castDuration", castDuration, "negative");
			}
		}
		if (overrides.getCooldown() != null) {
			Short cooldown = overrides.getCooldown();
			if (cooldown >= 0) {
				builder.cooldown(cooldown);
			} else {
				warnIgnored(sourceKey, "cooldown", cooldown, "negative");
			}
		}
		if (overrides.getDetectionThreshold() != null) {
			Double threshold = overrides.getDetectionThreshold();
			Double sanitized = AbilityValueSanitizers.sanitizeDetectionThreshold(threshold);
			if (sanitized == null) {
				warnIgnored(sourceKey, "detectionThreshold", threshold, "not finite");
			} else {
				if (!sanitized.equals(threshold)) {
					warnIgnored(sourceKey, "detectionThreshold", threshold, "clamped to " + sanitized);
				}
				builder.detectionThreshold(sanitized);
			}
		}
		if (overrides.getMask() != null) {
			String mask = overrides.getMask().trim();
			if (FilenameValidators.containsPathSeparator(mask)) {
				warnIgnored(sourceKey, "mask", mask, "contains path separators");
			} else {
				builder.mask(mask);
			}
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

	private Map<String, AbilitySettingsOverrides> toDomainMap(Map<String, PresetAbilityOverrides> source) {
		if (source == null || source.isEmpty()) {
			return Map.of();
		}
		Map<String, AbilitySettingsOverrides> domain = new LinkedHashMap<>();
		for (Map.Entry<String, PresetAbilityOverrides> entry : source.entrySet()) {
			String key = entry.getKey();
			if (key == null || key.isBlank()) {
				warnIgnored(null, "overrides", entry.getValue(), "blank key");
				continue;
			}
			AbilitySettingsOverrides converted = toDomain(entry.getValue(), key);
			if (converted != null && !converted.isEmpty()) {
				domain.put(key, converted);
			}
		}
		return domain;
	}

	private void warnIgnored(String sourceKey, String field, Object value, String reason) {
		if (sourceKey == null || sourceKey.isBlank()) {
			logger.warn("Ignoring invalid ability override field '{}' (value={}, reason={})", field, value, reason);
			return;
		}
		logger.warn("Ignoring invalid ability override for '{}' field '{}' (value={}, reason={})", sourceKey, field, value, reason);
	}

	private boolean containsPathSeparator(String value) {
		return FilenameValidators.containsPathSeparator(value);
	}
}
