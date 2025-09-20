package com.lansoftprogramming.runeSequence.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;


import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.Instant;
import java.util.logging.Logger;

public class ConfigManager {
	private static final Logger logger = Logger.getLogger(ConfigManager.class.getName());
	private static final String APP_NAME = "RuneSequence";

	private final Path configDir;
	private final Path settingsPath;
	private final Path rotationsPath;
	private final Path abilitiesPath;
	private final ObjectMapper objectMapper;

	private AppSettings settings;
	private RotationConfig rotations;
	private AbilityConfig abilities;

	public ConfigManager() {
		this.configDir = getAppDataPath().resolve(APP_NAME);
		this.settingsPath = configDir.resolve("settings.json");
		this.rotationsPath = configDir.resolve("rotations.json");
		this.abilitiesPath = configDir.resolve("abilities.json");

		this.objectMapper = createObjectMapper();
	}

	public void initialize() throws IOException {
		//get Screen Scaling Size
		createConfigDirectory();
		loadOrCreateSettings();
		loadOrCreateRotations();
		loadOrCreateAbilities();
	}

	private void createConfigDirectory() throws IOException {
		if (!Files.exists(configDir)) {
			Files.createDirectories(configDir);
			logger.info("Created config directory: {}" + configDir);
		}
	}

	private void loadOrCreateSettings() throws IOException {
		if (Files.exists(settingsPath)) {
			settings = objectMapper.readValue(settingsPath.toFile(), AppSettings.class);
			logger.info("Loaded existing settings");
		} else {
			settings = loadDefaultSettings();
			saveSettings();
			logger.info("Created default settings");
		}
	}

	private void loadOrCreateRotations() throws IOException {
		if (Files.exists(rotationsPath)) {
			rotations = objectMapper.readValue(rotationsPath.toFile(), RotationConfig.class);
			logger.info("Loaded existing rotations");
		} else {
			rotations = loadDefaultRotations();
			saveRotations();
			logger.info("Created default rotations");
		}
	}

	private void loadOrCreateAbilities() throws IOException {
		if (Files.exists(abilitiesPath)) {
			abilities = objectMapper.readValue(abilitiesPath.toFile(), AbilityConfig.class);
			logger.info("Loaded existing abilities");
		} else {
			abilities = loadDefaultAbilities();
			saveAbilities();
			logger.info("Created default abilities");
		}
	}

	public void saveSettings() throws IOException {
		settings.setUpdated(Instant.now());
		objectMapper.writeValue(settingsPath.toFile(), settings);
	}

	public void saveRotations() throws IOException {
		objectMapper.writeValue(rotationsPath.toFile(), rotations);
	}

	public void saveAbilities() throws IOException {
		objectMapper.writeValue(abilitiesPath.toFile(), abilities);
	}

	// Getters
	public AppSettings getSettings() {
		return settings;
	}

	public RotationConfig getRotations() {
		return rotations;
	}

	public AbilityConfig getAbilities() {
		return abilities;
	}

	public Path getConfigDir() {
		return configDir;
	}

	public boolean isFirstRun() {
		return !settings.isMigrated();
	}

	// Missing methods for DetectionEngine

	public int getDetectionInterval() {

		return settings.getDetection().getIntervalMs();

	}


	public double getConfidenceThreshold() {

		return settings.getDetection().getConfidenceThreshold();

	}

	private AppSettings loadDefaultSettings() throws IOException {
		return loadDefaultResource("defaults/settings.json", AppSettings.class);
	}

	private RotationConfig loadDefaultRotations() throws IOException {
		return loadDefaultResource("defaults/rotations.json", RotationConfig.class);
	}

	private AbilityConfig loadDefaultAbilities() throws IOException {
		return loadDefaultResource("defaults/abilities.json", AbilityConfig.class);
	}

	private <T> T loadDefaultResource(String resourcePath, Class<T> clazz) throws IOException {
		try (var inputStream = getClass().getClassLoader().getResourceAsStream(resourcePath)) {
			if (inputStream == null) {
				throw new IOException("Default resource not found: " + resourcePath);
			}
			return objectMapper.readValue(inputStream, clazz);
		}
	}

	public static Path getAppDataPath() {
		String os = System.getProperty("os.name").toLowerCase();

		if (os.contains("win")) {
			return Paths.get(System.getenv("APPDATA"));
		} else if (os.contains("mac")) {
			return Paths.get(System.getProperty("user.home"), "Library", "Application Support");
		} else {
			// Linux/Unix
			String xdgConfig = System.getenv("XDG_CONFIG_HOME");
			return xdgConfig != null ?
					Paths.get(xdgConfig) :
					Paths.get(System.getProperty("user.home"), ".config");
		}
	}

	private ObjectMapper createObjectMapper() {
		ObjectMapper mapper = new ObjectMapper();
		mapper.registerModule(new JavaTimeModule());
		mapper.enable(SerializationFeature.INDENT_OUTPUT);
		mapper.disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);
		return mapper;
	}

}