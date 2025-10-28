package com.lansoftprogramming.runeSequence.ui.presetManager.detail;

import com.lansoftprogramming.runeSequence.infrastructure.config.AbilityConfig;
import com.lansoftprogramming.runeSequence.infrastructure.config.ConfigManager;
import com.lansoftprogramming.runeSequence.infrastructure.config.RotationConfig;
import com.lansoftprogramming.runeSequence.ui.presetManager.model.SequenceElement;
import com.lansoftprogramming.runeSequence.ui.presetManager.service.SequenceVisualService;
import com.lansoftprogramming.runeSequence.ui.shared.model.AbilityItem;
import com.lansoftprogramming.runeSequence.ui.shared.service.AbilityIconLoader;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.swing.*;
import java.io.IOException;
import java.util.List;
import java.util.Map;
import java.util.UUID;

public class SequenceDetailService {
	private static final Logger logger = LoggerFactory.getLogger(SequenceDetailService.class);

	private final ConfigManager configManager;
	private final AbilityIconLoader iconLoader;
	private final SequenceVisualService visualService;

	public SequenceDetailService(
			ConfigManager configManager,
			AbilityIconLoader iconLoader,
			SequenceVisualService visualService) {
		this.configManager = configManager;
		this.iconLoader = iconLoader;
		this.visualService = visualService;
	}

	public List<SequenceElement> parseSequenceExpression(String expression) {
		return visualService.parseToVisualElements(expression);
	}

	public AbilityItem createAbilityItem(String abilityKey) {
		try {
			AbilityConfig.AbilityData abilityData = configManager.getAbilities().getAbility(abilityKey);

			if (abilityData == null) {
				logger.debug("Ability data not found for key '{}'", abilityKey);
				ImageIcon icon = iconLoader.loadIcon(abilityKey);
				return new AbilityItem(abilityKey, abilityKey, 0, "Unknown", icon);
			}

			String displayName = getDisplayName(abilityData, abilityKey);
			int level = abilityData.getLevel() != null ? abilityData.getLevel() : 0;
			String type = abilityData.getType() != null ? abilityData.getType() : "Unknown";
			ImageIcon icon = iconLoader.loadIcon(abilityKey);

			return new AbilityItem(abilityKey, displayName, level, type, icon);

		} catch (Exception e) {
			logger.warn("Failed to create ability item for key: {}", abilityKey, e);
			return null;
		}
	}

	private String getDisplayName(AbilityConfig.AbilityData abilityData, String fallbackKey) {
		String commonName = abilityData.getCommonName();
		if (commonName != null && !commonName.isEmpty()) {
			return commonName;
		}
		return fallbackKey;
	}

	public SaveResult saveSequence(String existingId,
								   RotationConfig.PresetData referencePreset,
								   String sequenceName,
								   String expression) throws IOException {
		if (sequenceName == null || sequenceName.isBlank()) {
			throw new IllegalArgumentException("Sequence name must not be empty");
		}

		RotationConfig rotations = configManager.getRotations();
		if (rotations == null) {
			throw new IllegalStateException("Rotation configuration is not initialized");
		}

		Map<String, RotationConfig.PresetData> presets = rotations.getPresets();
		if (presets == null) {
			presets = new java.util.HashMap<>();
			rotations.setPresets(presets);
		}

		String targetId = resolvePresetId(existingId, referencePreset, presets);
		boolean created = false;

		RotationConfig.PresetData presetData = presets.get(targetId);
		if (presetData == null) {
			presetData = new RotationConfig.PresetData();
			presets.put(targetId, presetData);
			created = true;
		}

		presetData.setName(sequenceName);
		presetData.setExpression(expression);

		configManager.saveRotations();
		logger.info("Saved sequence '{}' with id {}", sequenceName, targetId);

		return new SaveResult(targetId, presetData, created);
	}

	private String resolvePresetId(String existingId,
								   RotationConfig.PresetData referencePreset,
								   Map<String, RotationConfig.PresetData> presets) {
		if (existingId != null && !existingId.isBlank() && presets.containsKey(existingId)) {
			return existingId;
		}

		if (referencePreset != null) {
			for (Map.Entry<String, RotationConfig.PresetData> entry : presets.entrySet()) {
				if (entry.getValue() == referencePreset) {
					return entry.getKey();
				}
			}
		}

		return UUID.randomUUID().toString();
	}

	public static class SaveResult {
		private final String presetId;
		private final RotationConfig.PresetData presetData;
		private final boolean created;

		public SaveResult(String presetId, RotationConfig.PresetData presetData, boolean created) {
			this.presetId = presetId;
			this.presetData = presetData;
			this.created = created;
		}

		public String getPresetId() {
			return presetId;
		}

		public RotationConfig.PresetData getPresetData() {
			return presetData;
		}

		public boolean isCreated() {
			return created;
		}
	}
}