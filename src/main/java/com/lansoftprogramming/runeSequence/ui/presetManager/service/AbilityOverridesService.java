package com.lansoftprogramming.runeSequence.ui.presetManager.service;

import com.lansoftprogramming.runeSequence.core.sequence.model.AbilitySettingsOverrides;
import com.lansoftprogramming.runeSequence.infrastructure.config.AbilitySettingsOverridesMapper;
import com.lansoftprogramming.runeSequence.infrastructure.config.dto.PresetAbilitySettings;
import com.lansoftprogramming.runeSequence.infrastructure.config.dto.PresetRotationDefaults;
import com.lansoftprogramming.runeSequence.ui.presetManager.model.SequenceElement;

import java.util.*;

/**
 * Handles per-instance ability override extraction, normalization, and mapping.
 */
public class AbilityOverridesService {

	private final AbilitySettingsOverridesMapper overridesMapper;

	public AbilityOverridesService(AbilitySettingsOverridesMapper overridesMapper) {
		this.overridesMapper = overridesMapper;
	}

	public Map<String, AbilitySettingsOverrides> toDomainOverrides(PresetAbilitySettings abilitySettings) {
		return overridesMapper.toDomain(abilitySettings);
	}

	public Map<String, AbilitySettingsOverrides> toDomainPerAbilityOverrides(PresetAbilitySettings abilitySettings) {
		return overridesMapper.toDomainPerAbility(abilitySettings);
	}

	public PresetAbilitySettings buildAbilitySettings(List<SequenceElement> elements) {
		if (elements == null || elements.isEmpty()) {
			return null;
		}
		Map<String, AbilitySettingsOverrides> perInstance = new LinkedHashMap<>();

		for (SequenceElement element : elements) {
			if (!element.isAbility() || !element.hasOverrides()) {
				continue;
			}
			String label = element.getInstanceLabel();
			if (label == null || label.isBlank()) {
				continue;
			}
			AbilitySettingsOverrides overrides = element.getAbilitySettingsOverrides();
			if (overrides != null) {
				perInstance.put(label, overrides);
			}
		}

		if (perInstance.isEmpty()) {
			return null;
		}

		return overridesMapper.toConfig(perInstance);
	}

	/**
	 * Builds a persisted per-instance ability settings container from a label -> overrides map.
	 * Blank labels and empty override objects are ignored.
	 */
	public PresetAbilitySettings buildAbilitySettingsFromOverrides(Map<String, AbilitySettingsOverrides> overridesByLabel) {
		if (overridesByLabel == null || overridesByLabel.isEmpty()) {
			return null;
		}

		Map<String, AbilitySettingsOverrides> normalized = new LinkedHashMap<>();
		for (Map.Entry<String, AbilitySettingsOverrides> entry : overridesByLabel.entrySet()) {
			String label = entry.getKey();
			AbilitySettingsOverrides overrides = entry.getValue();
			if (label == null || label.isBlank() || overrides == null || overrides.isEmpty()) {
				continue;
			}
			normalized.put(label, overrides);
		}

		if (normalized.isEmpty()) {
			return null;
		}

		return overridesMapper.toConfig(normalized);
	}

	public PresetAbilitySettings applyPerAbilityOverrides(PresetAbilitySettings abilitySettings,
	                                                      Map<String, AbilitySettingsOverrides> perAbilityOverrides) {
		if (perAbilityOverrides == null || perAbilityOverrides.isEmpty()) {
			if (abilitySettings != null) {
				abilitySettings.setPerAbility(null);
				if (abilitySettings.isEmpty()) {
					return null;
				}
			}
			return abilitySettings;
		}

		Map<String, AbilitySettingsOverrides> normalized = new LinkedHashMap<>();
		for (Map.Entry<String, AbilitySettingsOverrides> entry : perAbilityOverrides.entrySet()) {
			if (entry.getKey() == null || entry.getKey().isBlank()) {
				continue;
			}
			AbilitySettingsOverrides overrides = entry.getValue();
			if (overrides != null && !overrides.isEmpty()) {
				normalized.put(entry.getKey(), overrides);
			}
		}

		if (normalized.isEmpty()) {
			return applyPerAbilityOverrides(abilitySettings, null);
		}

		PresetAbilitySettings out = abilitySettings != null ? abilitySettings : new PresetAbilitySettings();
		out.setPerAbility(overridesMapper.toConfigMap(normalized));
		return out.isEmpty() ? null : out;
	}

	public PresetRotationDefaults extractRotationDefaults(PresetAbilitySettings abilitySettings) {
		if (abilitySettings == null) {
			return null;
		}
		PresetRotationDefaults defaults = abilitySettings.getRotationDefaults();
		if (defaults == null || defaults.isEmpty()) {
			return null;
		}
		return defaults;
	}

	public PresetAbilitySettings applyRotationDefaults(PresetAbilitySettings abilitySettings, PresetRotationDefaults defaults) {
		if (defaults == null || defaults.isEmpty()) {
			if (abilitySettings != null) {
				abilitySettings.setRotationDefaults(null);
				if (abilitySettings.isEmpty()) {
					return null;
				}
			}
			return abilitySettings;
		}

		PresetAbilitySettings out = abilitySettings != null ? abilitySettings : new PresetAbilitySettings();
		out.setRotationDefaults(defaults);
		return out;
	}

	public Map<String, AbilitySettingsOverrides> extractOverridesByLabel(List<SequenceElement> elements) {
		if (elements == null || elements.isEmpty()) {
			return Map.of();
		}
		Map<String, AbilitySettingsOverrides> out = new LinkedHashMap<>();
		for (SequenceElement element : elements) {
			if (!element.isAbility() || !element.hasOverrides()) {
				continue;
			}
			String label = element.getInstanceLabel();
			if (label == null || label.isBlank()) {
				continue;
			}
			AbilitySettingsOverrides overrides = element.getAbilitySettingsOverrides();
			if (overrides != null && !overrides.isEmpty()) {
				out.put(label, overrides);
			}
		}
		return out;
	}

	public List<SequenceElement> normalizeAbilityElements(List<SequenceElement> elements) {
		if (elements == null) {
			return new ArrayList<>();
		}
		Set<String> usedLabels = collectExistingLabels(elements);
		int nextLabel = 1;
		List<SequenceElement> normalized = new ArrayList<>(elements.size());

		for (SequenceElement element : elements) {
			if (!element.isAbility()) {
				normalized.add(element);
				continue;
			}

			if (!element.hasOverrides()) {
				String label = element.getInstanceLabel();
				if (isAutoAssignedLabel(label)) {
					normalized.add(element.withInstanceLabel(null));
				} else {
					normalized.add(element);
				}
				continue;
			}

			String label = element.getInstanceLabel();
			if (label != null && !label.isBlank()) {
				normalized.add(element);
				continue;
			}
			while (usedLabels.contains(String.valueOf(nextLabel))) {
				nextLabel++;
			}
			String assigned = String.valueOf(nextLabel++);
			usedLabels.add(assigned);
			normalized.add(element.withInstanceLabel(assigned));
		}

		return normalized;
	}

	private boolean isAutoAssignedLabel(String label) {
		if (label == null) {
			return false;
		}
		String trimmed = label.trim();
		if (trimmed.isEmpty()) {
			return false;
		}
		for (int i = 0; i < trimmed.length(); i++) {
			char c = trimmed.charAt(i);
			if (c < '0' || c > '9') {
				return false;
			}
		}
		return true;
	}

	private Set<String> collectExistingLabels(List<SequenceElement> elements) {
		Set<String> labels = new HashSet<>();
		for (SequenceElement element : elements) {
			if (!element.isAbility()) {
				continue;
			}
			String label = element.getInstanceLabel();
			if (label != null && !label.isBlank()) {
				labels.add(label);
			}
		}
		return labels;
	}
}
