package com.lansoftprogramming.runeSequence;

import com.formdev.flatlaf.FlatDarkLaf;
import com.lansoftprogramming.runeSequence.application.*;
import com.lansoftprogramming.runeSequence.core.detection.DetectionEngine;
import com.lansoftprogramming.runeSequence.core.detection.TemplateDetector;
import com.lansoftprogramming.runeSequence.core.sequence.model.SequenceDefinition;
import com.lansoftprogramming.runeSequence.core.sequence.runtime.TooltipSchedule;
import com.lansoftprogramming.runeSequence.infrastructure.capture.ScreenCapture;
import com.lansoftprogramming.runeSequence.infrastructure.config.AppSettings;
import com.lansoftprogramming.runeSequence.infrastructure.config.ConfigManager;
import com.lansoftprogramming.runeSequence.infrastructure.config.RotationConfig;
import com.lansoftprogramming.runeSequence.infrastructure.hotkey.HotkeyBindingSource;
import com.lansoftprogramming.runeSequence.infrastructure.hotkey.HotkeyManager;
import com.lansoftprogramming.runeSequence.ui.notification.DefaultNotificationService;
import com.lansoftprogramming.runeSequence.ui.notification.NotificationService;
import com.lansoftprogramming.runeSequence.ui.overlay.MouseTooltipOverlay;
import com.lansoftprogramming.runeSequence.ui.overlay.OverlayRenderer;
import com.lansoftprogramming.runeSequence.ui.overlay.toast.ToastManager;
import com.lansoftprogramming.runeSequence.ui.presetManager.PresetManagerAction;
import com.lansoftprogramming.runeSequence.ui.regionSelector.RegionSelectorAction;
import com.lansoftprogramming.runeSequence.ui.taskbar.PrimeAbilityCacheAction;
import com.lansoftprogramming.runeSequence.ui.taskbar.SettingsAction;
import com.lansoftprogramming.runeSequence.ui.taskbar.Taskbar;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.swing.*;
import java.awt.*;
import java.nio.file.Path;
import java.util.Map;
import java.util.stream.Collectors;

public class Main {
	private static final String APP_NAME = "RuneSequence";
	private static final Logger logger = LoggerFactory.getLogger(Main.class);

	private static ConfigManager configManager;
	private static TemplateCache templateCache;
	private static Window toastHostWindow;

	public static void main(String[] args) {
		logger.info("Starting {} application...", APP_NAME);

		try {
			// 1. Load Configurations
			populateSettings();
			// 2. Load Image Templates
			populateTemplateCache();

			// 3. Initialize core components
			ScreenCapture screenCapture = new ScreenCapture(configManager.getSettings());
			TemplateDetector templateDetector = new TemplateDetector(templateCache, configManager.getAbilities());
			OverlayRenderer overlayRenderer = new OverlayRenderer(() -> {
				AppSettings settings = configManager.getSettings();
				return settings != null
						&& settings.getUi() != null
						&& settings.getUi().isBlinkCurrentAbilities();
			});
			MouseTooltipOverlay mouseTooltipOverlay = new MouseTooltipOverlay();
			NotificationService notifications = createNotificationService();

			// 4. Set up the Sequence Manager with our debug rotation
			RotationConfig rotationConfig = configManager.getRotations();
			if (rotationConfig == null || rotationConfig.getPresets().isEmpty()) {
				logger.error("No rotations found in config. Exiting.");
				return;
			}

			// Parse all presets from the config file and build tooltip schedules
			TooltipScheduleBuilder scheduleBuilder = new TooltipScheduleBuilder(
					configManager.getAbilities().getAbilities().keySet()
			);
			Map<String, TooltipScheduleBuilder.BuildResult> buildResults = rotationConfig.getPresets().entrySet().stream()
					.collect(Collectors.toMap(
							Map.Entry::getKey,
							entry -> scheduleBuilder.build(entry.getValue().getExpression())
					));

			Map<String, SequenceDefinition> namedSequences = buildResults.entrySet().stream()
					.filter(entry -> entry.getValue().definition() != null)
					.collect(Collectors.toMap(
							Map.Entry::getKey,
							entry -> entry.getValue().definition()
					));

			Map<String, TooltipSchedule> tooltipSchedules = buildResults.entrySet().stream()
					.filter(entry -> entry.getValue().definition() != null)
					.collect(Collectors.toMap(
							Map.Entry::getKey,
							entry -> entry.getValue().schedule()
					));

			SequenceManager sequenceManager = new SequenceManager(
					namedSequences,
					tooltipSchedules,
					configManager.getAbilities(),
					notifications,
					templateDetector
			);

			//Hotkeys
			SequenceController sequenceController = new SequenceController(sequenceManager);
			sequenceManager.setSequenceController(sequenceController);


			// Activate selected rotation or fall back to debug sequence
			// --- Determine rotation to activate ---
			String fallbackSequenceName = "debug-limitless";

			String selected = configManager.getSettings().getRotation() != null
					? configManager.getSettings().getRotation().getSelectedId()
					: null;
			String sequenceToActivate = fallbackSequenceName;

			// --- Check for selected rotation ---
			if (selected == null || selected.isBlank()) {
				logger.warn("No rotation selected in settings. Falling back to '{}'.", fallbackSequenceName);
			} else if (namedSequences.containsKey(selected)) {
				sequenceToActivate = selected;
				logger.info("Activating configured rotation by id '{}'.", selected);
			} else {
				// --- Try to match rotation by name ---
				String matchedKey = rotationConfig.getPresets().entrySet().stream()
						.filter(e -> e.getValue().getName() != null)
						.filter(e -> e.getValue().getName().equalsIgnoreCase(selected))
						.map(Map.Entry::getKey)
						.filter(namedSequences::containsKey)
						.findFirst()
						.orElse(null);

				if (matchedKey != null) {
					sequenceToActivate = matchedKey;
					logger.info("Configured rotation '{}' matched preset '{}'.",
							selected, rotationConfig.getPresets().get(matchedKey).getName());
				} else {
					logger.error("Configured rotation '{}' not found by id or name. Falling back to '{}'.",
							selected, fallbackSequenceName);
				}
			}

			// --- Activate selected or fallback sequence ---
			if (sequenceManager.activateSequence(sequenceToActivate)) {
				logger.info("Successfully activated the '{}' sequence.", sequenceToActivate);
			} else {
				logger.error("Failed to activate the '{}' sequence. Is it defined in rotations.json?", sequenceToActivate);
				return;
			}

			// 5. Initialize and start the detection engine
			DetectionEngine detectionEngine = new DetectionEngine(
					screenCapture,
					templateDetector,
					sequenceManager,
					overlayRenderer,
					mouseTooltipOverlay,
					notifications,
					configManager.getDetectionInterval()
			);
			SequenceRunService sequenceRunService = new SequenceRunService(
					sequenceController,
					sequenceManager,
					detectionEngine
			);

			HotkeyBindingSource bindingSource = new HotkeyBindingSource();
			HotkeyManager hotkeyManager = new HotkeyManager(bindingSource.loadBindings(configManager.getSettings().getHotkeys()));
			hotkeyManager.initialize();
			hotkeyManager.addListener(sequenceRunService);

			detectionEngine.primeActiveSequence();
			detectionEngine.start();

			// Initialize GUI on the Event Dispatch Thread
			SwingUtilities.invokeLater(() -> {
				FlatDarkLaf.setup(); // Set the look and feel
				Taskbar taskbar = new Taskbar();
				taskbar.initialize();

				// Add a settings option to the context menu
				taskbar.addMenuItem("Preset Manager", new PresetManagerAction(configManager, sequenceRunService));
				taskbar.addMenuItem("Select Region", new RegionSelectorAction(configManager));
				taskbar.addMenuItem("Prime Ability Cache", new PrimeAbilityCacheAction(detectionEngine));
				taskbar.addMenuItem("Settings", new SettingsAction(configManager));
				taskbar.addSeparator();
			});

			// Add a shutdown hook to clean up resources gracefully
			Runtime.getRuntime().addShutdownHook(new Thread(() -> {
				logger.info("Shutdown sequence initiated.");
				detectionEngine.stop();
				overlayRenderer.shutdown();
				mouseTooltipOverlay.shutdown();
				templateCache.shutdown();
				if (toastHostWindow != null) {
					toastHostWindow.dispose();
				}
				logger.info("Application has been shut down successfully.");
			}));

			logger.info("Application setup complete. Detection engine is running.");

		} catch (Exception e) {
			logger.error("A critical error occurred during application startup.", e);
		}
	}

	private static NotificationService createNotificationService() {
		try {
			Rectangle bounds = GraphicsEnvironment.getLocalGraphicsEnvironment().getMaximumWindowBounds();
			int width = 420;
			int height = 160;
			int x = bounds.x + bounds.width - width - 24;
			int y = bounds.y + bounds.height - height - 24;

			JWindow toastWindow = new JWindow();
			toastWindow.setAlwaysOnTop(true);
			toastWindow.setFocusableWindowState(false);
			toastWindow.setBackground(new Color(0, 0, 0, 0));
			toastWindow.setSize(width, height);
			toastWindow.setLocation(x, y);
			toastWindow.setVisible(true);

			ToastManager toastManager = new ToastManager(toastWindow);
			toastHostWindow = toastWindow;
			return new DefaultNotificationService(toastWindow, toastManager);
		} catch (Exception e) {
			logger.warn("Failed to initialize toast notifications; falling back to logging.", e);
			return new DefaultNotificationService(new JPanel(), ToastManager.loggingFallback(logger));
		}
	}

	public static void populateTemplateCache() {
		logger.info("Initializing TemplateCache...");

		int requestedSize = 30;
		if (configManager.getSettings() != null && configManager.getSettings().getUi() != null) {
			requestedSize = configManager.getSettings().getUi().getIconSize();
		}

		Path iconFolder = configManager.resolveAbilityIconFolder(requestedSize);
		if (iconFolder == null) {
			logger.warn("Unable to resolve ability folder for icon size {}. Falling back to base directory.", requestedSize);
			iconFolder = configManager.getAbilityImagePath();
		}

		templateCache = new TemplateCache(iconFolder);
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
