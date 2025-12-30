package com.lansoftprogramming.runeSequence.ui.presetManager.detail;

import com.lansoftprogramming.runeSequence.core.sequence.model.AbilitySettingsOverrides;
import com.lansoftprogramming.runeSequence.core.sequence.model.EffectiveAbilityConfig;
import com.lansoftprogramming.runeSequence.infrastructure.config.AbilityConfig;
import com.lansoftprogramming.runeSequence.infrastructure.config.ConfigManager;
import com.lansoftprogramming.runeSequence.infrastructure.config.RotationConfig;
import com.lansoftprogramming.runeSequence.infrastructure.config.dto.PresetAbilitySettings;
import com.lansoftprogramming.runeSequence.ui.presetManager.model.SequenceElement;
import com.lansoftprogramming.runeSequence.ui.presetManager.service.SequenceVisualService;
import com.lansoftprogramming.runeSequence.ui.shared.model.AbilityItem;
import com.lansoftprogramming.runeSequence.ui.shared.service.AbilityIconLoader;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.swing.*;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
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

	public List<SequenceElement> parseSequenceExpression(String expression, Map<String, AbilitySettingsOverrides> overridesByLabel) {
		return visualService.parseToVisualElements(expression, overridesByLabel);
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

	public ImageIcon loadIcon(String abilityKey) {
		return iconLoader.loadIcon(abilityKey);
	}

	public AbilityConfig.AbilityData resolveBaseAbilityData(String abilityKey) {
		try {
			if (abilityKey == null || abilityKey.isBlank()) {
				return null;
			}
			return configManager.getAbilities().getAbility(abilityKey);
		} catch (Exception e) {
			logger.warn("Failed to resolve base ability data for key: {}", abilityKey, e);
			return null;
		}
	}

	public EffectiveAbilityConfig resolveEffectiveAbilityConfig(String abilityKey, AbilitySettingsOverrides overrides) {
		AbilityConfig.AbilityData baseAbility = resolveBaseAbilityData(abilityKey);
		if (baseAbility == null) {
			return null;
		}
		return EffectiveAbilityConfig.from(abilityKey, baseAbility, overrides);
	}

	AbilityPropertiesDialog.MaskValidator createMaskValidator() {
		return maskKey -> {
			if (maskKey == null || maskKey.isBlank()) {
				return AbilityPropertiesDialog.ValidationResult.valid();
			}

			Path abilitiesRoot = configManager != null ? configManager.getAbilityImagePath() : null;
			if (abilitiesRoot == null || !Files.isDirectory(abilitiesRoot)) {
				return AbilityPropertiesDialog.ValidationResult.valid();
			}

			String fileName = toMaskFileName(maskKey);
			Path candidate = abilitiesRoot.resolve(fileName);
			if (Files.exists(candidate)) {
				return AbilityPropertiesDialog.ValidationResult.valid();
			}

			return AbilityPropertiesDialog.ValidationResult.invalid("Mask file not found: " + fileName);
		};
	}

	private String toMaskFileName(String maskKey) {
		String trimmed = maskKey != null ? maskKey.trim() : "";
		String lower = trimmed.toLowerCase();
		if (lower.endsWith(".png") || lower.endsWith(".jpg") || lower.endsWith(".jpeg")) {
			return trimmed;
		}
		return trimmed + ".png";
	}

	private String getDisplayName(AbilityConfig.AbilityData abilityData, String fallbackKey) {
		String commonName = abilityData.getCommonName();
		if (commonName != null && !commonName.isEmpty()) {
			return commonName;
		}
		return fallbackKey;
	}

	public SaveOutcome saveSequence(String existingId,
									RotationConfig.PresetData referencePreset,
									String sequenceName,
									String expression,
									PresetAbilitySettings abilitySettings) {
		if (sequenceName == null || sequenceName.isBlank()) {
			return SaveOutcome.validationFailure("Sequence name must not be empty");
		}

		try {
			RotationConfig rotations = configManager.getRotations();
			if (rotations == null) {
				return SaveOutcome.failure("Rotation configuration is not initialized");
			}

			Map<String, RotationConfig.PresetData> presets = rotations.getPresets();
			if (presets == null) {
				presets = new HashMap<>();
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
			presetData.setAbilitySettings(abilitySettings);

			configManager.saveRotations();
			logger.info("Saved sequence '{}' with id {}", sequenceName, targetId);

			SaveResult result = new SaveResult(targetId, presetData, created);
			return SaveOutcome.success(result, created ? "Sequence created." : "Sequence updated.");

		} catch (IOException ioException) {
			logger.error("Failed to persist sequence '{}'", sequenceName, ioException);
			return SaveOutcome.failure("Failed to save sequence: " + ioException.getMessage());
		} catch (Exception unexpected) {
			logger.error("Unexpected error while saving sequence '{}'", sequenceName, unexpected);
			return SaveOutcome.failure("Unexpected error occurred while saving sequence.");
		}
	}

	private String resolvePresetId(String existingId,
								   RotationConfig.PresetData referencePreset,
								   Map<String, RotationConfig.PresetData> presets) {
		if (existingId != null && !existingId.isBlank()) {
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

	public static class SaveOutcome {
		private final boolean success;
		private final boolean validationFailure;
		private final String message;
		private final SaveResult result;

		private SaveOutcome(boolean success, boolean validationFailure, String message, SaveResult result) {
			this.success = success;
			this.validationFailure = validationFailure;
			this.message = message;
			this.result = result;
		}

		public static SaveOutcome success(SaveResult result, String message) {
			return new SaveOutcome(true, false, message, result);
		}

		public static SaveOutcome validationFailure(String message) {
			return new SaveOutcome(false, true, message, null);
		}

		public static SaveOutcome failure(String message) {
			return new SaveOutcome(false, false, message, null);
		}

		public boolean isSuccess() {
			return success;
		}

		public boolean isValidationFailure() {
			return validationFailure;
		}

		public String getMessage() {
			return message;
		}

		public SaveResult getResult() {
			return result;
		}
	}
}
