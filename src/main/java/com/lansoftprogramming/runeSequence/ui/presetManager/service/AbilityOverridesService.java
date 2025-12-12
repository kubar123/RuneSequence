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
				if (abilitySettings.getPerInstance() == null || abilitySettings.getPerInstance().isEmpty()) {
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
			if (!element.isAbility() || !element.hasOverrides()) {
				normalized.add(element);
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
