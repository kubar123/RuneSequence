package com.lansoftprogramming.runeSequence.config;


import com.fasterxml.jackson.annotation.JsonProperty;

import java.time.Instant;

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

	public static class RegionSettings {
		@JsonProperty("x")
		private int x = 447;

		@JsonProperty("y")
		private int y = 501;

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
}