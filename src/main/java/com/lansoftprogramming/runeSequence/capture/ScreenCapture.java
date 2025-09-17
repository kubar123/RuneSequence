package com.lansoftprogramming.runeSequence.capture;

import org.bytedeco.ffmpeg.global.avutil;
import org.bytedeco.javacv.FFmpegFrameGrabber;
import org.bytedeco.javacv.Frame;
import org.bytedeco.javacv.OpenCVFrameConverter;
//import org.opencv.core.Mat;
import org.bytedeco.opencv.opencv_core.Mat;
import org.bytedeco.opencv.opencv_core.Rect;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.awt.*;
import java.util.concurrent.atomic.AtomicBoolean;

public class ScreenCapture {
	private static final Logger logger = LoggerFactory.getLogger(ScreenCapture.class);

	private FFmpegFrameGrabber grabber;
	private OpenCVFrameConverter.ToMat converter;
	private Rectangle captureRegion;
	private Rectangle screenBounds;
	private AtomicBoolean isInitialized = new AtomicBoolean(false);
	private String platform;

	// Platform-specific capture formats
	private static final String WINDOWS_FORMAT = "gdigrab";
	private static final String LINUX_FORMAT = "x11grab";
	private static final String MACOS_FORMAT = "avfoundation";

	public ScreenCapture() {
		detectPlatform();
		initializeScreenBounds();
		converter = new OpenCVFrameConverter.ToMat();

		// Set default to full screen
		captureRegion = new Rectangle(screenBounds);

		logger.info("ScreenCapture initialized for {}: {}x{}",
				platform, screenBounds.width, screenBounds.height);
	}

	/**
	 * Initialize FFmpeg grabber - always captures full screen
	 */
	private void initializeGrabber() throws Exception {
		if (grabber != null) {
			grabber.close();
		}

		grabber = new FFmpegFrameGrabber(getInputSource());
		grabber.setFormat(getScreenFormat());

		// Performance optimizations
		grabber.setOption("framerate", "60"); // High framerate
		grabber.setOption("probesize", "32"); // Fast probe
		grabber.setOption("fflags", "nobuffer"); // Minimize latency
		grabber.setOption("flags", "low_delay");

		// GPU acceleration options
		enableHardwareAcceleration();

		// Always capture full screen - crop in OpenCV
		grabber.setImageWidth(screenBounds.width);
		grabber.setImageHeight(screenBounds.height);

		grabber.start();
		isInitialized.set(true);

		logger.info("FFmpeg grabber started: {}x{}", screenBounds.width, screenBounds.height);
	}

	/**
	 * Capture screen and return OpenCV Mat (cropped to region)
	 */
	public Mat captureScreen() {
		try {
			if (!isInitialized.get()) {
				initializeGrabber();
			}

			Frame frame = grabber.grab();
			if (frame == null) {
				logger.warn("Frame grab returned null");
				return new Mat();
			}

			Mat fullScreenMat = converter.convert(frame);
			if (fullScreenMat == null) {
				logger.warn("Frame conversion returned null");
				return new Mat();
			}

			// Crop to region if not full screen
			if (isFullScreen()) {
				return fullScreenMat.clone();
			} else {
				return cropMatToRegion(fullScreenMat);
			}

		} catch (Exception e) {
			logger.error("Screen capture failed", e);
			isInitialized.set(false);
			return new Mat();
		}
	}

	/**
	 * Crop OpenCV Mat to capture region
	 */
	private Mat cropMatToRegion(Mat fullMat) {
		try {
//			org.opencv.core.Rect roi = new org.opencv.core.Rect(
			Rect roi = new Rect(
					captureRegion.x, captureRegion.y,
					captureRegion.width, captureRegion.height
			);

			Mat croppedMat = new Mat(fullMat, roi);
			Mat result = croppedMat.clone();

			// Cleanup
			fullMat.release();
			croppedMat.release();

			return result;

		} catch (Exception e) {
			logger.error("Failed to crop Mat", e);
			fullMat.release();
			return new Mat();
		}
	}

	/**
	 * Set capture region - no restart needed, OpenCV crops
	 */
	public void setRegion(Rectangle region) {
		if (region == null) {
			// Reset to full screen
			captureRegion = new Rectangle(screenBounds);
		} else {
			// Validate region bounds
			Rectangle clampedRegion = new Rectangle(region);
			clampedRegion = clampedRegion.intersection(screenBounds);

			if (clampedRegion.isEmpty()) {
				logger.warn("Invalid region {}, using full screen", region);
				captureRegion = new Rectangle(screenBounds);
			} else {
				captureRegion = clampedRegion;
			}
		}

		logger.debug("Capture region set to: {}", captureRegion);
		// No restart needed - cropping happens in OpenCV
	}

	/**
	 * Get current capture region
	 */
	public Rectangle getRegion() {
		return new Rectangle(captureRegion);
	}

	/**
	 * Get full screen bounds
	 */
	public Rectangle getScreenBounds() {
		return new Rectangle(screenBounds);
	}

	/**
	 * Check if using full screen capture
	 */
	private boolean isFullScreen() {
		return captureRegion.equals(screenBounds);
	}

	/**
	 * Detect platform and set appropriate format
	 */
	private void detectPlatform() {
		String os = System.getProperty("os.name").toLowerCase();

		if (os.contains("win")) {
			platform = "Windows";
		} else if (os.contains("mac") || os.contains("darwin")) {
			platform = "macOS";
		} else if (os.contains("nix") || os.contains("nux") || os.contains("aix")) {
			platform = "Linux";
		} else {
			platform = "Unknown";
			logger.warn("Unknown platform: {}, defaulting to Linux format", os);
		}
	}

	/**
	 * Get platform-specific screen format
	 */
	private String getScreenFormat() {
		switch (platform) {
			case "Windows":
				return WINDOWS_FORMAT;
			case "Linux":
				return LINUX_FORMAT;
			case "macOS":
				return MACOS_FORMAT;
			default:
				return LINUX_FORMAT; // Fallback
		}
	}

	/**
	 * Get platform-specific input source
	 */
	private String getInputSource() {
		switch (platform) {
			case "Windows":
				return "desktop"; // gdigrab desktop
			case "Linux":
				String display = System.getenv("DISPLAY");
				return (display != null ? display : ":0") + ".0"; // x11grab
			case "macOS":
				return "1"; // avfoundation screen capture device
			default:
				return ":0.0"; // Fallback to Linux
		}
	}

	/**
	 * Enable hardware acceleration based on platform
	 */
	private void enableHardwareAcceleration() {
		try {
			switch (platform) {
				case "Windows":
					// Try DXVA2, D3D11VA, then CUDA
					grabber.setOption("hwaccel", "dxva2");
					break;
				case "Linux":
					// Try VAAPI, then CUDA
					grabber.setOption("hwaccel", "vaapi");
					break;
				case "macOS":
					// VideoToolbox
					grabber.setOption("hwaccel", "videotoolbox");
					break;
			}
			logger.debug("Hardware acceleration enabled for {}", platform);
		} catch (Exception e) {
			logger.warn("Hardware acceleration failed, using software decoding: {}", e.getMessage());
		}
	}

	/**
	 * Initialize screen bounds detection
	 */
	private void initializeScreenBounds() {
		GraphicsEnvironment ge = GraphicsEnvironment.getLocalGraphicsEnvironment();
		GraphicsDevice device = ge.getDefaultScreenDevice();
		DisplayMode mode = device.getDisplayMode();

		screenBounds = new Rectangle(0, 0, mode.getWidth(), mode.getHeight());

		logger.debug("Screen bounds detected: {}x{}", screenBounds.width, screenBounds.height);
	}

	/**
	 * Check if capture is running
	 */
	public boolean isRunning() {
		return isInitialized.get() && grabber != null;
	}

	/**
	 * Cleanup resources
	 */
	public void shutdown() {
		try {
			isInitialized.set(false);

			if (converter != null) {
				converter.close();
				converter = null;
			}

			if (grabber != null) {
				grabber.stop();
				grabber.close();
				grabber = null;
			}

			logger.info("ScreenCapture shutdown completed");

		} catch (Exception e) {
			logger.error("Error during shutdown", e);
		}
	}
}