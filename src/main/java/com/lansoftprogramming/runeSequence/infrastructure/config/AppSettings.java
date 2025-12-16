package com.lansoftprogramming.runeSequence.infrastructure.config;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;

import java.awt.*;
import java.time.Instant;
import java.util.List;
import java.util.logging.Logger;

@JsonIgnoreProperties(ignoreUnknown = true)
public class AppSettings {
	private static final Logger LOGGER = Logger.getLogger(AppSettings.class.getName());

	@JsonProperty("version")
	private String version = "1.0.0";

	@JsonProperty("migrated")
	private boolean migrated = false;

	@JsonProperty("region")
	private RegionSettings region = new RegionSettings();

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
		return region;
	}

	public void setRegion(RegionSettings region) {
		this.region = region;
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
