package com.lansoftprogramming.runeSequence;

import com.formdev.flatlaf.FlatDarkLaf;
import com.lansoftprogramming.runeSequence.application.SequenceController;
import com.lansoftprogramming.runeSequence.application.SequenceManager;
import com.lansoftprogramming.runeSequence.application.TemplateCache;
import com.lansoftprogramming.runeSequence.core.detection.DetectionEngine;
import com.lansoftprogramming.runeSequence.core.detection.TemplateDetector;
import com.lansoftprogramming.runeSequence.core.sequence.model.SequenceDefinition;
import com.lansoftprogramming.runeSequence.core.sequence.parser.SequenceParser;
import com.lansoftprogramming.runeSequence.infrastructure.capture.ScreenCapture;
import com.lansoftprogramming.runeSequence.infrastructure.config.ConfigManager;
import com.lansoftprogramming.runeSequence.infrastructure.config.RotationConfig;
import com.lansoftprogramming.runeSequence.infrastructure.hotkey.HotkeyManager;
import com.lansoftprogramming.runeSequence.ui.overlay.OverlayRenderer;
import com.lansoftprogramming.runeSequence.ui.presetManager.PresetManagerAction;
import com.lansoftprogramming.runeSequence.ui.regionSelector.RegionSelectorAction;
import com.lansoftprogramming.runeSequence.ui.taskbar.SettingsAction;
import com.lansoftprogramming.runeSequence.ui.taskbar.Taskbar;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.swing.*;
import java.util.Map;
import java.util.stream.Collectors;

public class Main {
	private static final String APP_NAME = "RuneSequence";
	private static final Logger logger = LoggerFactory.getLogger(Main.class);

	private static ConfigManager configManager;
	private static TemplateCache templateCache;

	public static void main(String[] args) {
		logger.info("Starting {} application...", APP_NAME);

		try {
			// 1. Load Configurations
			populateSettings();
			// 2. Load Image Templates
			populateTemplateCache();

			// 3. Initialize core components
			ScreenCapture screenCapture = new ScreenCapture();
			TemplateDetector templateDetector = new TemplateDetector(templateCache, configManager.getAbilities());
			OverlayRenderer overlayRenderer = new OverlayRenderer();

			// 4. Set up the Sequence Manager with our debug rotation
			RotationConfig rotationConfig = configManager.getRotations();
			if (rotationConfig == null || rotationConfig.getPresets().isEmpty()) {
				logger.error("No rotations found in config. Exiting.");
				return;
			}

			// Parse all presets from the config file
			Map<String, SequenceDefinition> namedSequences = rotationConfig.getPresets().entrySet().stream()
					.collect(Collectors.toMap(
							Map.Entry::getKey,
							entry -> SequenceParser.parse(entry.getValue().getExpression())
					));

			SequenceManager sequenceManager = new SequenceManager(namedSequences, configManager.getAbilities());

			//Hotkeys
			SequenceController sequenceController = new SequenceController(sequenceManager);
			sequenceManager.setSequenceController(sequenceController);

			HotkeyManager hotkeyManager = new HotkeyManager();
			hotkeyManager.initialize();
			hotkeyManager.addListener(sequenceController);

			// Activate our specific debug sequence
			String debugSequenceName = "debug-limitless";
			if (sequenceManager.activateSequence(debugSequenceName)) {
				logger.info("Successfully activated the '{}' debug sequence.", debugSequenceName);
			} else {
				logger.error("Failed to activate the '{}' debug sequence. Is it defined in rotations.json?", debugSequenceName);
				return;
			}

			// 5. Initialize and start the detection engine
			DetectionEngine detectionEngine = new DetectionEngine(
					screenCapture,
					templateDetector,
					sequenceManager,
					overlayRenderer,
					configManager.getDetectionInterval(),
					sequenceController
			);

			detectionEngine.start();

			// Initialize GUI on the Event Dispatch Thread
			SwingUtilities.invokeLater(() -> {
				FlatDarkLaf.setup(); // Set the look and feel
				Taskbar taskbar = new Taskbar();
				taskbar.initialize();

				// Add a settings option to the context menu
				taskbar.addMenuItem("Preset Manager", new PresetManagerAction(configManager));
				taskbar.addMenuItem("Select Region", new RegionSelectorAction(configManager));
				taskbar.addMenuItem("Settings", new SettingsAction());
				taskbar.addSeparator();
			});

			// Add a shutdown hook to clean up resources gracefully
			Runtime.getRuntime().addShutdownHook(new Thread(() -> {
				logger.info("Shutdown sequence initiated.");
				detectionEngine.stop();
				overlayRenderer.shutdown();
				templateCache.shutdown();
				logger.info("Application has been shut down successfully.");
			}));

			logger.info("Application setup complete. Detection engine is running.");

		} catch (Exception e) {
			logger.error("A critical error occurred during application startup.", e);
		}
	}

	public static void populateTemplateCache() {
		logger.info("Initializing TemplateCache...");
		templateCache = new TemplateCache(APP_NAME);
	}

	public static void populateSettings() {
		logger.info("Initializing ConfigManager...");
		configManager = new ConfigManager();
		try {
			configManager.initialize();
		} catch (java.io.IOException e) {
			logger.error("Failed to initialize ConfigManager", e);
			throw new RuntimeException(e);
		}
		logger.info("ConfigManager initialized. Settings loaded.");
	}
}