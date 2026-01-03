package com.lansoftprogramming.runeSequence;

import com.formdev.flatlaf.FlatDarkLaf;
import com.lansoftprogramming.runeSequence.application.*;
import com.lansoftprogramming.runeSequence.core.detection.DetectionEngine;
import com.lansoftprogramming.runeSequence.core.detection.TemplateDetector;
import com.lansoftprogramming.runeSequence.core.sequence.model.SequenceDefinition;
import com.lansoftprogramming.runeSequence.core.sequence.runtime.TooltipSchedule;
import com.lansoftprogramming.runeSequence.infrastructure.capture.ScreenCapture;
import com.lansoftprogramming.runeSequence.infrastructure.config.AbilitySettingsOverridesMapper;
import com.lansoftprogramming.runeSequence.infrastructure.config.AppSettings;
import com.lansoftprogramming.runeSequence.infrastructure.config.ConfigManager;
import com.lansoftprogramming.runeSequence.infrastructure.config.RotationConfig;
import com.lansoftprogramming.runeSequence.infrastructure.hotkey.HotkeyBindingSource;
import com.lansoftprogramming.runeSequence.infrastructure.hotkey.HotkeyEvent;
import com.lansoftprogramming.runeSequence.infrastructure.hotkey.HotkeyManager;
import com.lansoftprogramming.runeSequence.infrastructure.hotkey.KeyChord;
import com.lansoftprogramming.runeSequence.ui.notification.DefaultNotificationService;
import com.lansoftprogramming.runeSequence.ui.notification.NotificationService;
import com.lansoftprogramming.runeSequence.ui.overlay.MouseTooltipOverlay;
import com.lansoftprogramming.runeSequence.ui.overlay.OverlayRenderer;
import com.lansoftprogramming.runeSequence.ui.overlay.toast.ToastManager;
import com.lansoftprogramming.runeSequence.ui.presetManager.PresetManagerAction;
import com.lansoftprogramming.runeSequence.ui.regionSelector.RegionSelectorAction;
import com.lansoftprogramming.runeSequence.ui.settings.debug.IconDetectionDebugService;
import com.lansoftprogramming.runeSequence.ui.shared.window.WindowPlacementSupport;
import com.lansoftprogramming.runeSequence.ui.taskbar.PrimeAbilityCacheAction;
import com.lansoftprogramming.runeSequence.ui.taskbar.SettingsAction;
import com.lansoftprogramming.runeSequence.ui.taskbar.Taskbar;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.swing.*;
import java.awt.*;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.atomic.AtomicReference;
import java.util.stream.Collectors;

public class Main {
	private static final String APP_NAME = "RuneSequence";
	private static volatile Logger logger;

	private static Logger logger() {
		Logger cached = logger;
		if (cached != null) {
			return cached;
		}
		Logger created = LoggerFactory.getLogger(Main.class);
		logger = created;
		return created;
	}

	private static ConfigManager configManager;
	private static TemplateCache templateCache;
	private static Window toastHostWindow;
	private static Taskbar taskbar;
	private static ScreenCapture screenCapture;
	private static OverlayRenderer overlayRenderer;
	private static MouseTooltipOverlay mouseTooltipOverlay;
	private static DetectionEngine detectionEngine;
	private static HotkeyManager hotkeyManager;
	private static final Object shutdownLock = new Object();
	private static boolean shutdownInitiated = false;

	public static void main(String[] args) {
		BootstrapPaths bootstrap = bootstrapPaths();
		Logger logger = logger();
		logger.info("Starting {} application...", APP_NAME);
		logger.info("Runtime: java={} os={} {}", System.getProperty("java.version"), System.getProperty("os.name"), System.getProperty("os.version"));
		logger.info("Config dir: {}", bootstrap.configDir);
		logger.info("Logs dir: {}", bootstrap.logsDir);
		logger.info("JavaCPP cache dir: {}", bootstrap.javacppCacheDir);

		try {
			// 1. Load Configurations
			populateSettings();
			// 2. Load Image Templates
			populateTemplateCache();

			// 3. Initialize core components
			screenCapture = new ScreenCapture(configManager.getSettings());
			TemplateDetector templateDetector = new TemplateDetector(templateCache, configManager.getAbilities());
				overlayRenderer = new OverlayRenderer(
						() -> {
							AppSettings settings = configManager.getSettings();
							return settings != null
									&& settings.getUi() != null
									&& settings.getUi().isBlinkCurrentAbilities();
						},
						() -> {
							AppSettings settings = configManager.getSettings();
							return settings == null
									|| settings.getUi() == null
									|| settings.getUi().isAbilityIndicatorEnabled();
						},
						() -> {
							AppSettings settings = configManager.getSettings();
							return settings != null && settings.getUi() != null
									? settings.getUi().getAbilityIndicatorLoopMs()
									: 600L;
						}
				);
				mouseTooltipOverlay = new MouseTooltipOverlay();
				NotificationService notifications = createNotificationService();

			// 4. Set up the Sequence Manager
			RotationConfig rotationConfig = configManager.getRotations();
			Map<String, RotationConfig.PresetData> presets = rotationConfig != null && rotationConfig.getPresets() != null
					? rotationConfig.getPresets()
					: Map.of();

			// Parse all presets from the config file and build tooltip schedules
			TooltipScheduleBuilder scheduleBuilder = new TooltipScheduleBuilder(
					configManager.getAbilities().getAbilities().keySet()
			);
			AbilitySettingsOverridesMapper overridesMapper = new AbilitySettingsOverridesMapper();
			Map<String, TooltipScheduleBuilder.BuildResult> buildResults = presets.entrySet().stream()
					.collect(Collectors.toMap(
							Map.Entry::getKey,
							entry -> scheduleBuilder.build(
									entry.getValue().getExpression(),
									overridesMapper.toDomain(entry.getValue().getAbilitySettings()),
									overridesMapper.toDomainPerAbility(entry.getValue().getAbilitySettings())
							)
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

			// 5. Initialize and start the detection engine
			detectionEngine = new DetectionEngine(
					screenCapture,
					templateDetector,
					sequenceManager,
					overlayRenderer,
					mouseTooltipOverlay,
					notifications,
					configManager.getDetectionInterval(),
					() -> {
						AppSettings settings = configManager.getSettings();
						return settings == null
								|| settings.getUi() == null
								|| settings.getUi().isChanneledWaitTooltipsEnabled();
					}
			);
			SequenceRunService sequenceRunService = new SequenceRunService(
					sequenceController,
					sequenceManager,
					detectionEngine,
					scheduleBuilder
			);

			IconDetectionDebugService iconDetectionDebugService = new IconDetectionDebugService(
					detectionEngine,
					screenCapture,
					templateDetector,
					templateCache,
					overlayRenderer,
					Math.max(250, configManager.getDetectionInterval())
			);

			HotkeyBindingSource bindingSource = new HotkeyBindingSource();
			Map<HotkeyEvent, List<KeyChord>> initialHotkeys = bindingSource.loadBindings(configManager.getSettings().getHotkeys());
			AtomicReference<Map<HotkeyEvent, List<KeyChord>>> lastHotkeys = new AtomicReference<>(initialHotkeys);
			hotkeyManager = new HotkeyManager(initialHotkeys);
			hotkeyManager.initialize();
			hotkeyManager.addListener(sequenceRunService);
			configManager.addSettingsSaveListener(settings -> {
				if (hotkeyManager == null) {
					return;
				}
				AppSettings.HotkeySettings hotkeys = settings != null ? settings.getHotkeys() : null;
				Map<HotkeyEvent, List<KeyChord>> nextBindings = bindingSource.loadBindings(hotkeys);
				Map<HotkeyEvent, List<KeyChord>> previous = lastHotkeys.get();
				if (nextBindings.equals(previous)) {
					return;
				}
				lastHotkeys.set(nextBindings);
				hotkeyManager.refreshBindings(nextBindings);
				hotkeyManager.initialize();
			});

			sequenceRunService.pause();

			// Initialize GUI on the Event Dispatch Thread
			SwingUtilities.invokeLater(() -> {
				FlatDarkLaf.setup(); // Set the look and feel
				taskbar = new Taskbar();
				taskbar.initialize();
				taskbar.setExitHandler(Main::requestShutdown);

				PresetManagerAction presetManagerAction = new PresetManagerAction(configManager, sequenceRunService, iconDetectionDebugService);

				// Add a settings option to the context menu
				taskbar.addMenuItem("Preset Manager", presetManagerAction);
				taskbar.addMenuItem("Select Region", new RegionSelectorAction(configManager));
				taskbar.addMenuItem("Prime Ability Cache", new PrimeAbilityCacheAction(detectionEngine));
				taskbar.addMenuItem("Settings", new SettingsAction(configManager, iconDetectionDebugService));
				taskbar.addSeparator();

				// Main UI
				presetManagerAction.execute();
			});

			// Add a shutdown hook to clean up resources gracefully
			Runtime.getRuntime().addShutdownHook(new Thread(Main::shutdownApplication));

			logger.info("Application setup complete. Detection engine is ready (paused).");

		} catch (Exception e) {
			logger.error("A critical error occurred during application startup.", e);
			showFatalStartupError(bootstrap, e);
		}
	}

	private static void showFatalStartupError(BootstrapPaths bootstrap, Exception error) {
		String message = buildFatalErrorMessage(bootstrap, error);
		try {
			Runnable showDialog = () -> com.lansoftprogramming.runeSequence.ui.theme.ThemedDialogs.showMessageDialog(
					null,
					"RuneSequence failed to start",
					message
			);
			if (SwingUtilities.isEventDispatchThread()) {
				showDialog.run();
			} else {
				SwingUtilities.invokeLater(showDialog);
			}
		} catch (Exception ignored) {
			System.err.println(message);
		}
	}

	private static String buildFatalErrorMessage(BootstrapPaths bootstrap, Exception error) {
		StringBuilder sb = new StringBuilder();
		sb.append("A critical error occurred during startup.\n\n");
		if (bootstrap != null) {
			sb.append("Config folder:\n").append(bootstrap.configDir).append("\n\n");
			sb.append("Logs folder:\n").append(bootstrap.logsDir).append("\n\n");
		}
		if (error != null) {
			String type = error.getClass().getSimpleName();
			String msg = error.getMessage();
			sb.append("Error:\n").append(type);
			if (msg != null && !msg.isBlank()) {
				sb.append(": ").append(msg);
			}
			sb.append("\n");
		}
		return sb.toString();
	}

	private static BootstrapPaths bootstrapPaths() {
		Path baseConfig = resolveAppDataPath();
		Path defaultConfigDir = baseConfig.resolve(APP_NAME);
		Path configDir = resolvePathOverride("runeSequence.config.dir", defaultConfigDir);
		Path logsDir = resolvePathOverride("runeSequence.log.dir", configDir.resolve("logs"));

		String explicitCacheDir = System.getProperty("org.bytedeco.javacpp.cachedir");
		String envCacheDir = System.getenv("JAVACPP_CACHE");
		Path javacppCacheDir;
		if (explicitCacheDir != null && !explicitCacheDir.isBlank()) {
			javacppCacheDir = Paths.get(explicitCacheDir);
		} else if (envCacheDir != null && !envCacheDir.isBlank()) {
			javacppCacheDir = Paths.get(envCacheDir);
			System.setProperty("org.bytedeco.javacpp.cachedir", javacppCacheDir.toString());
		} else {
			javacppCacheDir = configDir.resolve("javacpp-cache");
			System.setProperty("org.bytedeco.javacpp.cachedir", javacppCacheDir.toString());
		}

		setIfAbsent("runeSequence.config.dir", configDir.toString());
		setIfAbsent("runeSequence.log.dir", logsDir.toString());

		try {
			Files.createDirectories(configDir);
			Files.createDirectories(logsDir);
			Files.createDirectories(javacppCacheDir);
		} catch (Exception e) {
			System.err.println("Failed to create runtime directories: " + e.getMessage());
		}

		return new BootstrapPaths(configDir, logsDir, javacppCacheDir);
	}

	private static void setIfAbsent(String key, String value) {
		if (key == null || key.isBlank() || value == null) {
			return;
		}
		String existing = System.getProperty(key);
		if (existing != null && !existing.isBlank()) {
			return;
		}
		System.setProperty(key, value);
	}

	private static Path resolvePathOverride(String propertyKey, Path fallback) {
		if (propertyKey == null || propertyKey.isBlank()) {
			return fallback;
		}
		String override = System.getProperty(propertyKey);
		if (override == null || override.isBlank()) {
			return fallback;
		}
		try {
			return Paths.get(override);
		} catch (Exception ignored) {
			return fallback;
		}
	}

	private static Path resolveAppDataPath() {
		String os = System.getProperty("os.name", "").toLowerCase(Locale.ROOT);

		if (os.contains("win")) {
			String appData = System.getenv("APPDATA");
			if (appData != null && !appData.isBlank()) {
				return Paths.get(appData);
			}
		} else if (os.contains("mac")) {
			return Paths.get(System.getProperty("user.home"), "Library", "Application Support");
		}

		String xdgConfig = System.getenv("XDG_CONFIG_HOME");
		return (xdgConfig != null && !xdgConfig.isBlank())
				? Paths.get(xdgConfig)
				: Paths.get(System.getProperty("user.home"), ".config");
	}

	private record BootstrapPaths(Path configDir, Path logsDir, Path javacppCacheDir) {
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
			Logger logger = logger();
			logger.warn("Failed to initialize toast notifications; falling back to logging.", e);
			return new DefaultNotificationService(new JPanel(), ToastManager.loggingFallback(logger));
		}
	}

	public static void populateTemplateCache() {
		Logger logger = logger();
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
		Logger logger = logger();
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

	public static void requestShutdown() {
		Logger logger = logger();
		shutdownApplication();
		try {
			System.exit(0);
		} catch (SecurityException e) {
			logger.error("System.exit was blocked; application will continue running.", e);
		}
	}

	private static void shutdownApplication() {
		Logger logger = logger();
		synchronized (shutdownLock) {
			if (shutdownInitiated) {
				return;
			}
			shutdownInitiated = true;
		}

		logger.info("Shutdown sequence initiated.");
		try {
			WindowPlacementSupport.flushAll();
		} catch (Exception e) {
			logger.debug("Failed to flush window placement state during shutdown.", e);
		}
		if (taskbar != null) {
			taskbar.dispose();
		}
		if (detectionEngine != null) {
			detectionEngine.stop();
		}
		if (screenCapture != null) {
			screenCapture.shutdown();
		}
		if (hotkeyManager != null) {
			hotkeyManager.shutdown();
		}
		if (overlayRenderer != null) {
			overlayRenderer.shutdown();
		}
		if (mouseTooltipOverlay != null) {
			mouseTooltipOverlay.shutdown();
		}
		if (templateCache != null) {
			templateCache.shutdown();
		}
		if (toastHostWindow != null) {
			toastHostWindow.dispose();
			toastHostWindow = null;
		}
		logger.info("Application has been shut down successfully.");
	}
}
