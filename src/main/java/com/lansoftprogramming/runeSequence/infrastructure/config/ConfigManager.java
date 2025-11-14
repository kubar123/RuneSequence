package com.lansoftprogramming.runeSequence.infrastructure.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.lansoftprogramming.runeSequence.core.image.OpenCvImageProcessor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.Instant;
import java.util.Comparator;

public class ConfigManager {
	private static final Logger logger = LoggerFactory.getLogger(ConfigManager.class);
	private static final String APP_NAME = "RuneSequence";

	private final Path configDir;
	private final Path settingsPath;
	private final Path rotationsPath;
	private final Path abilitiesPath;
	private final Path abilityCategoriesPath;
	private final Path abilityImagePath;
	private final ObjectMapper objectMapper;

	private AppSettings settings;
	private RotationConfig rotations;
	private AbilityConfig abilities;
	private AbilityCategoryConfig abilityCategories;

	public ConfigManager() {
		this.configDir = getAppDataPath().resolve(APP_NAME);
		this.settingsPath = configDir.resolve("settings.json");
		this.rotationsPath = configDir.resolve("rotations.json");
		this.abilitiesPath = configDir.resolve("abilities.json");
		this.abilityCategoriesPath = configDir.resolve("ability_categories.json");
		this.abilityImagePath = configDir.resolve("Abilities");

		this.objectMapper = createObjectMapper();
	}

	public void initialize() throws IOException {
		//User AppData folder ifExists check
		createConfigDirectory();
		//assets
		checkOrCreateAbilities();
		//settings files
		loadOrCreateSettings();
		loadOrCreateRotations();
		loadOrCreateAbilities();
		loadOrCreateAbilityCategories();
	}

	private void checkOrCreateAbilities() {
		if(!Files.exists(abilityImagePath)) {
			logger.info("Abilities directory not found. Extracting defaults...");
			try {
				AssetExtractor.extractSubfolder("/defaults/Abilities", abilityImagePath);
				logger.info("Extracted default abilities to: {}", abilityImagePath);
			}catch (IOException e) {
				logger.error("Failed to extract default abilities", e);
			}

			// Process images with OpenCV if processor is available
			processAbilityImages(abilityImagePath);
		}
	}

	private void processAbilityImages(Path abilitiesDir) {
		try {
			int[] sizes = ScalingConverter.getAllSizes();
			var processor = new OpenCvImageProcessor();
			processor.processImages(abilitiesDir, sizes);
			logger.info("Processed ability images for sizes: {}", java.util.Arrays.toString(sizes));
		} catch (Exception e) {
			logger.warn("Failed to process ability images: {}", e.getMessage());
		}
	}

	private void createConfigDirectory() throws IOException {
		if (!Files.exists(configDir)) {
			Files.createDirectories(configDir);
			logger.info("Created config directory: {}", configDir);
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

			// Check if abilities need migration (missing new fields)
			if (needsAbilitiesMigration()) {
				logger.warn("Abilities file is missing new fields. Backing up and regenerating from defaults.");
				backupAndRegenerate(abilitiesPath, "abilities.json.backup");
				abilities = loadDefaultAbilities();
				saveAbilities();
				logger.info("Regenerated abilities with updated schema");
			} else {
				logger.info("Loaded existing abilities");
			}
		} else {
			abilities = loadDefaultAbilities();
			saveAbilities();
			logger.info("Created default abilities");
		}
	}

	/**
	 * Checks if the abilities configuration needs migration to the new schema.
	 * Returns true if any ability is missing the new fields (common_name, type, level).
	 */
	private boolean needsAbilitiesMigration() {
		if (abilities == null || abilities.getAbilities() == null) {
			return false;
		}

		// Sample a few abilities to check if they have the new fields
		return abilities.getAbilities().values().stream()
				.limit(5)
				.anyMatch(data -> data.getCommonName() == null && data.getType() == null && data.getLevel() == null);
	}

	/**
	 * Backs up an existing file and prepares for regeneration.
	 */
	private void backupAndRegenerate(Path filePath, String backupName) throws IOException {
		Path backupPath = filePath.getParent().resolve(backupName);
		Files.copy(filePath, backupPath, java.nio.file.StandardCopyOption.REPLACE_EXISTING);
		logger.info("Backed up {} to {}", filePath.getFileName(), backupName);
	}

	private void loadOrCreateAbilityCategories() throws IOException {
		if (Files.exists(abilityCategoriesPath)) {
			abilityCategories = objectMapper.readValue(abilityCategoriesPath.toFile(), AbilityCategoryConfig.class);
			logger.info("Loaded existing ability categories");
		} else {
			abilityCategories = loadDefaultAbilityCategories();
			saveAbilityCategories();
			logger.info("Created default ability categories");
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

	public void saveAbilityCategories() throws IOException {
		objectMapper.writeValue(abilityCategoriesPath.toFile(), abilityCategories);
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

	public AbilityCategoryConfig getAbilityCategories() {
		return abilityCategories;
	}

	public Path getConfigDir() {
		return configDir;
	}

	public Path getAbilityImagePath() {
		return abilityImagePath;
	}

	/**
	 * Resolves the best-matching ability icon folder for the requested size.
	 * Prefers exact matches but will fall back to the nearest numeric folder name.
	 *
	 * @param iconSize desired icon size in pixels
	 * @return the resolved folder path, or null if none exists
	 */
	public Path resolveAbilityIconFolder(int iconSize) {
		if (!Files.isDirectory(abilityImagePath)) {
			return null;
		}

		Path exact = abilityImagePath.resolve(String.valueOf(iconSize));
		if (Files.isDirectory(exact)) {
			return exact;
		}

		try (var children = Files.list(abilityImagePath)) {
			return children
					.filter(Files::isDirectory)
					.map(path -> path.getFileName().toString())
					.filter(name -> name.matches("\\d+"))
					.map(Integer::parseInt)
					.sorted(Comparator.comparingInt(size -> Math.abs(size - iconSize)))
					.findFirst()
					.map(size -> abilityImagePath.resolve(String.valueOf(size)))
					.orElse(null);
		} catch (IOException e) {
			logger.debug("Unable to resolve ability icon folder for size {}", iconSize, e);
			return null;
		}
	}

	public boolean isFirstRun() {
		return !settings.isMigrated();
	}

	// Missing methods for DetectionEngine

	public int getDetectionInterval() {

		return settings.getDetection().getIntervalMs();

	}


//	public double getConfidenceThreshold() {
//
//		return settings.getDetection().getConfidenceThreshold();
//
//	}

	private AppSettings loadDefaultSettings() throws IOException {
		return loadDefaultResource("defaults/settings.json", AppSettings.class);
	}

	private RotationConfig loadDefaultRotations() throws IOException {
		return loadDefaultResource("defaults/rotations.json", RotationConfig.class);
	}

	private AbilityConfig loadDefaultAbilities() throws IOException {
		return loadDefaultResource("defaults/abilities.json", AbilityConfig.class);
	}

	private AbilityCategoryConfig loadDefaultAbilityCategories() throws IOException {
		return loadDefaultResource("defaults/ability_categories.json", AbilityCategoryConfig.class);
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
