package com.lansoftprogramming.runeSequence.ui.presetManager;

import com.lansoftprogramming.runeSequence.application.SequenceRunService;
import com.lansoftprogramming.runeSequence.infrastructure.config.ConfigManager;
import com.lansoftprogramming.runeSequence.ui.presetManager.detail.SequenceDetailService;
import com.lansoftprogramming.runeSequence.ui.presetManager.masterRotations.SelectedSequenceIndicator;
import com.lansoftprogramming.runeSequence.ui.presetManager.masterRotations.SequenceListModel;
import com.lansoftprogramming.runeSequence.ui.presetManager.service.AbilityOverridesService;
import com.lansoftprogramming.runeSequence.ui.presetManager.service.SequenceVisualService;
import com.lansoftprogramming.runeSequence.ui.shared.service.AbilityIconLoader;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.swing.*;

/**
 * Centralizes the construction of Preset Manager UI dependencies so
 * PresetManagerWindow can focus on presentation and event handling.
 */
public class PresetManagerWindowBuilder {
	private static final Logger logger = LoggerFactory.getLogger(PresetManagerWindowBuilder.class);

	private final ConfigManager configManager;
	private final SequenceRunService sequenceRunService;

	public PresetManagerWindowBuilder(ConfigManager configManager, SequenceRunService sequenceRunService) {
		this.configManager = configManager;
		this.sequenceRunService = sequenceRunService;
	}

	public PresetManagerWindow buildAndShow() {
		try {
			AbilityIconLoader iconLoader = new AbilityIconLoader(
					configManager.getConfigDir().resolve("Abilities")
			);
			SequenceVisualService visualService = new SequenceVisualService(
					configManager.getAbilities().getAbilities().keySet()
			);
			AbilityOverridesService overridesService = new AbilityOverridesService(
					new com.lansoftprogramming.runeSequence.infrastructure.config.AbilitySettingsOverridesMapper()
			);
			SequenceDetailService detailService = new SequenceDetailService(
					configManager,
					iconLoader,
					visualService
			);
			SelectedSequenceIndicator selectionIndicator = SelectedSequenceIndicator.forSettings(
					configManager.getSettings()
			);
			SequenceListModel listModel = new SequenceListModel();

			return new PresetManagerWindow(
					configManager,
					listModel,
					iconLoader,
					detailService,
					overridesService,
					selectionIndicator,
					sequenceRunService
			);
		} catch (Exception e) {
			logger.error("Failed to build PresetManagerWindow", e);
			JOptionPane.showMessageDialog(
					null,
					"Failed to open Preset Manager: " + e.getMessage(),
					"Preset Manager Error",
					JOptionPane.ERROR_MESSAGE
			);
			return null;
		}
	}
}
