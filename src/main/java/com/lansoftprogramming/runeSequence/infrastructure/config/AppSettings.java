package com.lansoftprogramming.runeSequence.infrastructure.config;


import com.fasterxml.jackson.annotation.JsonProperty;

import java.time.Instant;
import java.util.List;

public class AppSettings {
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
		return hotkeys;
	}

	public void setHotkeys(HotkeySettings hotkeys) {
		this.hotkeys = hotkeys;
	}

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
	}

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

	public static class RotationSettings {
		@JsonProperty("selectedId")
		private String selectedId;

		public String getSelectedId() {
			return selectedId;
		}

		public void setSelectedId(String selectedId) {
			this.selectedId = selectedId;
		}
	}

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

		public static class Binding {
			@JsonProperty("id")
			private String id;

			@JsonProperty("action")
			private String action;

			@JsonProperty("keys")
			private List<List<String>> keys;

			@JsonProperty("scope")
			private String scope;

			public String getId() {
				return id;
			}

			public void setId(String id) {
				this.id = id;
			}

			public String getAction() {
				return action;
			}

			public void setAction(String action) {
				this.action = action;
			}

			public List<List<String>> getKeys() {
				return keys;
			}

			public void setKeys(List<List<String>> keys) {
				this.keys = keys;
			}

			public String getScope() {
				return scope;
			}

			public void setScope(String scope) {
				this.scope = scope;
			}
		}
	}
}