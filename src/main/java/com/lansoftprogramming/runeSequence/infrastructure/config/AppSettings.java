package com.lansoftprogramming.runeSequence.infrastructure.config;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;

import java.awt.*;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.UUID;
import java.util.logging.Logger;

@JsonIgnoreProperties(ignoreUnknown = true)
public class AppSettings {
	private static final Logger LOGGER = Logger.getLogger(AppSettings.class.getName());
	private static final String ABILITIES_REGION_KEY = "abilities";
	private static final List<RegionDescriptor> DEFAULT_REGIONS = List.of(
			new RegionDescriptor(ABILITIES_REGION_KEY, "Abilities", true),
			new RegionDescriptor("chat-box", "Chat box", true),
			new RegionDescriptor("buff-bar", "Buff Bar", true),
			new RegionDescriptor("debuff-bar", "Debuff Bar", true),
			new RegionDescriptor("timer", "Timer", true)
	);

	@JsonProperty("version")
	private String version = "1.0.0";

	@JsonProperty("migrated")
	private boolean migrated = false;

	@JsonProperty("region")
	private RegionSettings region = new RegionSettings();

	@JsonProperty("regions")
	private List<RegionSettings> regions = new java.util.ArrayList<>();

	@JsonProperty("updated")
	private Instant updated = Instant.now();

	@JsonProperty("detection")
	private DetectionSettings detection = new DetectionSettings();

	@JsonProperty("rotation")
	private RotationSettings rotation = new RotationSettings();

	@JsonProperty("hotkeys")
	private HotkeySettings hotkeys = new HotkeySettings();

	@JsonProperty("ui")
	private UiSettings ui = new UiSettings();

	// Getters and setters
	public String getVersion() {
		return version;
	}

	public void setVersion(String version) {
		this.version = version;
	}

	public boolean isMigrated() {
		return migrated;
	}

	public void setMigrated(boolean migrated) {
		this.migrated = migrated;
	}

	public RegionSettings getRegion() {
		ensureDefaultRegions();
		return region;
	}

	public void setRegion(RegionSettings region) {
		this.region = region;
		if (region == null) {
			return;
		}
		List<RegionSettings> namedRegions = getRegions();
		if (namedRegions.isEmpty()) {
			namedRegions.add(region);
		} else {
			namedRegions.set(0, region);
		}
	}

	public List<RegionSettings> getRegions() {
		ensureDefaultRegions();
		return regions;
	}

	public void setRegions(List<RegionSettings> regions) {
		this.regions = regions != null ? new ArrayList<>(regions) : new ArrayList<>();
		ensureDefaultRegions();
	}

	public Instant getUpdated() {
		return updated;
	}

	public void setUpdated(Instant updated) {
		this.updated = updated;
	}

	public DetectionSettings getDetection() {
		return detection;
	}

	public void setDetection(DetectionSettings detection) {
		this.detection = detection;
	}

	public RotationSettings getRotation() {
		return rotation;
	}

	public void setRotation(RotationSettings rotation) {
		this.rotation = rotation;
	}

	public HotkeySettings getHotkeys() {
		LOGGER.info("Accessing hotkey settings.");
		return hotkeys;
	}

	public void setHotkeys(HotkeySettings hotkeys) {
		this.hotkeys = hotkeys;
	}

	public UiSettings getUi() {
		return ui;
	}

	public void setUi(UiSettings ui) {
		this.ui = ui;
	}

	// ------------------------------ REGION ------------------------------
	@JsonIgnoreProperties(ignoreUnknown = true)
	public static class RegionSettings {
		@JsonProperty("name")
		private String name;

		@JsonProperty("key")
		private String key;

		@JsonProperty("locked")
		private boolean locked;

		@JsonProperty("x")
		private int x = 0;

		@JsonProperty("y")
		private int y = 0;

		@JsonProperty("width")
		private int width = 1731;

		@JsonProperty("height")
		private int height = 875;

		@JsonProperty("screenWidth")
		private int screenWidth = 2560;

		@JsonProperty("screenHeight")
		private int screenHeight = 1400;

		@JsonProperty("timestamp")
		private Instant timestamp = Instant.now();

		public String getName() {
			return name;
		}

		public void setName(String name) {
			this.name = name;
		}

		public String getKey() {
			return key;
		}

		public void setKey(String key) {
			this.key = key;
		}

		public boolean isLocked() {
			return locked;
		}

		public void setLocked(boolean locked) {
			this.locked = locked;
		}

		// Getters and setters
		public int getX() {
			return x;
		}

		public void setX(int x) {
			this.x = x;
		}

		public int getY() {
			return y;
		}

		public void setY(int y) {
			this.y = y;
		}

		public int getWidth() {
			return width;
		}

		public void setWidth(int width) {
			this.width = width;
		}

		public int getHeight() {
			return height;
		}

		public void setHeight(int height) {
			this.height = height;
		}

		public int getScreenWidth() {
			return screenWidth;
		}

		public void setScreenWidth(int screenWidth) {
			this.screenWidth = screenWidth;
		}

		public int getScreenHeight() {
			return screenHeight;
		}

		public void setScreenHeight(int screenHeight) {
			this.screenHeight = screenHeight;
		}

		public Instant getTimestamp() {
			return timestamp;
		}

		public void setTimestamp(Instant timestamp) {
			this.timestamp = timestamp;
		}

		public Rectangle toRectangle() {
			return new Rectangle(x, y, width, height);
		}
	}

	private void ensureDefaultRegions() {
		if (regions == null) {
			regions = new ArrayList<>();
		}

		List<RegionSettings> working = new ArrayList<>();
		for (RegionSettings entry : regions) {
			if (entry == null) {
				continue;
			}
			assignKeyIfMissing(entry);
			working.add(entry);
		}

		List<RegionSettings> ordered = new ArrayList<>();
		for (RegionDescriptor descriptor : DEFAULT_REGIONS) {
			RegionSettings existing = removeRegionByKey(working, descriptor.key());
			if (existing == null) {
				existing = new RegionSettings();
				existing.setKey(descriptor.key());
			}
			existing.setName(descriptor.displayName());
			existing.setLocked(descriptor.locked());
			ordered.add(existing);
		}

		if (!working.isEmpty()) {
			ordered.addAll(working);
		}

		if (ordered.isEmpty()) {
			RegionSettings defaultRegion = new RegionSettings();
			defaultRegion.setKey(ABILITIES_REGION_KEY);
			defaultRegion.setName("Abilities");
			defaultRegion.setLocked(true);
			ordered.add(defaultRegion);
		}

		RegionSettings abilityRegion = null;
		for (RegionSettings entry : ordered) {
			if (ABILITIES_REGION_KEY.equals(entry.getKey())) {
				abilityRegion = entry;
				break;
			}
		}
		if (abilityRegion == null) {
			abilityRegion = ordered.get(0);
		}

		if (region != null && region != abilityRegion && hasMeaningfulRegion(region)
				&& !hasMeaningfulRegion(abilityRegion)) {
			copyRegionGeometry(region, abilityRegion);
		}

		region = abilityRegion;
		regions = ordered;
	}

	private static void copyRegionGeometry(RegionSettings source, RegionSettings target) {
		if (source == null || target == null) {
			return;
		}
		target.setX(source.getX());
		target.setY(source.getY());
		target.setWidth(source.getWidth());
		target.setHeight(source.getHeight());
		target.setScreenWidth(source.getScreenWidth());
		target.setScreenHeight(source.getScreenHeight());
		target.setTimestamp(source.getTimestamp() != null ? source.getTimestamp() : Instant.now());
	}

	private static boolean hasMeaningfulRegion(RegionSettings settings) {
		return settings != null && settings.getWidth() > 0 && settings.getHeight() > 0;
	}

	private static RegionSettings removeRegionByKey(List<RegionSettings> list, String key) {
		if (list == null || list.isEmpty()) {
			return null;
		}
		Iterator<RegionSettings> iterator = list.iterator();
		while (iterator.hasNext()) {
			RegionSettings entry = iterator.next();
			if (entry != null && key.equals(entry.getKey())) {
				iterator.remove();
				return entry;
			}
		}
		return null;
	}

	private static void assignKeyIfMissing(RegionSettings settings) {
		if (settings == null) {
			return;
		}
		String existing = settings.getKey();
		if (existing == null || existing.trim().isEmpty()) {
			settings.setKey(generateRegionKey());
		}
	}

	private static String generateRegionKey() {
		return "region-" + UUID.randomUUID();
	}

	private static final class RegionDescriptor {
		private final String key;
		private final String displayName;
		private final boolean locked;

		private RegionDescriptor(String key, String displayName, boolean locked) {
			this.key = key;
			this.displayName = displayName;
			this.locked = locked;
		}

		String key() {
			return key;
		}

		String displayName() {
			return displayName;
		}

		boolean locked() {
			return locked;
		}
	}

	// ------------------------------ DETECTION ------------------------------
	@JsonIgnoreProperties(ignoreUnknown = true)
	public static class DetectionSettings {
		@JsonProperty("intervalMs")
		private int intervalMs = 100; // 10 FPS

		@JsonProperty("confidenceThreshold")
		private double confidenceThreshold = 0.8;

		@JsonProperty("enableOverlay")
		private boolean enableOverlay = true;

		// Getters and setters
		public int getIntervalMs() {
			return intervalMs;
		}

		public void setIntervalMs(int intervalMs) {
			this.intervalMs = intervalMs;
		}

		public double getConfidenceThreshold() {
			return confidenceThreshold;
		}

		public void setConfidenceThreshold(double confidenceThreshold) {
			this.confidenceThreshold = confidenceThreshold;
		}

		public boolean isEnableOverlay() {
			return enableOverlay;
		}

		public void setEnableOverlay(boolean enableOverlay) {
			this.enableOverlay = enableOverlay;
		}
	}

	// ------------------------------ ROTATION ------------------------------
	@JsonIgnoreProperties(ignoreUnknown = true)
	public static class RotationSettings {
		@JsonProperty("selectedId")
		private String selectedId;

		@JsonProperty("autoSaveOnSwitch")
		private boolean autoSaveOnSwitch = false;

		public String getSelectedId() {
			return selectedId;
		}

		public void setSelectedId(String selectedId) {
			this.selectedId = selectedId;
		}

		public boolean isAutoSaveOnSwitch() {
			return autoSaveOnSwitch;
		}

		public void setAutoSaveOnSwitch(boolean autoSaveOnSwitch) {
			this.autoSaveOnSwitch = autoSaveOnSwitch;
		}
	}

	// ------------------------------ HOTKEYS ------------------------------
	@JsonIgnoreProperties(ignoreUnknown = true)
	public static class HotkeySettings {
		@JsonProperty("schema")
		private int schema;

		@JsonProperty("bindings")
		private List<Binding> bindings;

		public int getSchema() {
			return schema;
		}

		public void setSchema(int schema) {
			this.schema = schema;
		}

		public List<Binding> getBindings() {
			return bindings;
		}

		public void setBindings(List<Binding> bindings) {
			this.bindings = bindings;
		}

		@JsonIgnoreProperties(ignoreUnknown = true)
		public static class Binding {
			@JsonProperty("action")
			private String action;

			@JsonProperty("userEnabled")
			private boolean userEnabled;

			@JsonProperty("global")
			private List<List<String>> global;

			@JsonProperty("user")
			private List<List<String>> user;

			@JsonProperty("scope")
			private String scope;

			public String getAction() {
				return action;
			}

			public void setAction(String action) {
				this.action = action;
			}

			public boolean isUserEnabled() {
				return userEnabled;
			}

			public void setUserEnabled(boolean userEnabled) {
				this.userEnabled = userEnabled;
			}

			public List<List<String>> getGlobal() {
				return global;
			}

			public void setGlobal(List<List<String>> global) {
				this.global = global;
			}

			public List<List<String>> getUser() {
				return user;
			}

			public void setUser(List<List<String>> user) {
				this.user = user;
			}

			public String getScope() {
				return scope;
			}

			public void setScope(String scope) {
				this.scope = scope;
			}
		}
	}

	// ------------------------------ UI ------------------------------
		@JsonIgnoreProperties(ignoreUnknown = true)
		public static class UiSettings {
			@JsonProperty("iconSize")
			private int iconSize = 30; // default icon size in pixels

			@JsonProperty("blinkCurrentAbilities")
			private boolean blinkCurrentAbilities = false;

			@JsonProperty("abilityIndicatorEnabled")
			private boolean abilityIndicatorEnabled = true;

			@JsonProperty("abilityIndicatorLoopMs")
			private long abilityIndicatorLoopMs = 600;

			public int getIconSize() {
				return iconSize;
			}

			public void setIconSize(int iconSize) {
				this.iconSize = iconSize;
			}

			public boolean isBlinkCurrentAbilities() {
				return blinkCurrentAbilities;
			}

			public void setBlinkCurrentAbilities(boolean blinkCurrentAbilities) {
				this.blinkCurrentAbilities = blinkCurrentAbilities;
			}

			public boolean isAbilityIndicatorEnabled() {
				return abilityIndicatorEnabled;
			}

			public void setAbilityIndicatorEnabled(boolean abilityIndicatorEnabled) {
				this.abilityIndicatorEnabled = abilityIndicatorEnabled;
			}

			public long getAbilityIndicatorLoopMs() {
				return abilityIndicatorLoopMs;
			}

			public void setAbilityIndicatorLoopMs(long abilityIndicatorLoopMs) {
				this.abilityIndicatorLoopMs = abilityIndicatorLoopMs;
			}
		}
	}
