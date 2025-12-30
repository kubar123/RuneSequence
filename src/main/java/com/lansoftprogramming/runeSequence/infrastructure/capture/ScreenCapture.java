package com.lansoftprogramming.runeSequence.infrastructure.capture;

import com.lansoftprogramming.runeSequence.infrastructure.config.AppSettings;
import org.bytedeco.javacv.FFmpegFrameGrabber;
import org.bytedeco.javacv.Frame;
import org.bytedeco.javacv.OpenCVFrameConverter;
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
	private final AtomicBoolean isInitialized = new AtomicBoolean(false);
	private String platform;
	private final boolean supportsNativeRegionCapture;

	// Platform-specific capture formats
	private static final String WINDOWS_FORMAT = "gdigrab";
	private static final String LINUX_FORMAT = "x11grab";
	private static final String MACOS_FORMAT = "avfoundation";

	public ScreenCapture() {
		this(null);
	}

	public ScreenCapture(AppSettings settings) {
		detectPlatform();
		supportsNativeRegionCapture = isNativeRegionCaptureSupported();
		initializeScreenBounds();
		// Lazily initialize the OpenCV converter on first capture to avoid
		// eagerly loading native libraries during construction (important for tests
		// that never call captureScreen()).
		converter = null;

		captureRegion = determineInitialRegion(settings != null ? settings.getRegion() : null);

		logger.info("ScreenCapture initialized for {}: capture={} within screen {}x{}",
				platform, captureRegion, screenBounds.width, screenBounds.height);
	}

	private synchronized void ensureConverter() {
		if (converter != null) {
			return;
		}
		converter = new OpenCVFrameConverter.ToMat();
	}

	/**
	 * Initialize FFmpeg grabber with the active capture region
	 */
	private synchronized void initializeGrabber() throws Exception {
		if (grabber != null) {
			try {
				grabber.stop();
			} catch (Exception e) {
				logger.debug("Ignoring grabber.stop failure during reinit", e);
			}
			try {
				grabber.close();
			} catch (Exception e) {
				logger.debug("Ignoring grabber.close failure during reinit", e);
			}
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

		configureCaptureRegion();

		grabber.start();
		isInitialized.set(true);

		logger.info("FFmpeg grabber started for region {} (nativeRegionCapture={}?)",
				captureRegion, supportsNativeRegionCapture && !isFullScreen());
	}

	/**
	 * Capture screen and return OpenCV Mat (cropped to region)
	 */
	public Mat captureScreen() {
		try {
			ensureConverter();
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

			boolean needsCropping = shouldCropFrame();
			if (!needsCropping) {
				Mat clone = fullScreenMat.clone();
				fullScreenMat.release();
				return clone;
			}

			return cropMatToRegion(fullScreenMat);

		} catch (Exception e) {
			logger.error("Screen capture failed", e);
			stopCapture();
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
	 * Set capture region and reconfigure the grabber if required
	 */
	public synchronized void setRegion(Rectangle region) {
		Rectangle newRegion = clampRegion(region);
		Rectangle previous = captureRegion;
		captureRegion = newRegion;

		logger.debug("Capture region set to: {}", captureRegion);

		boolean regionChanged = previous != null && !previous.equals(captureRegion);
		if (regionChanged && supportsNativeRegionCapture && isInitialized.get()) {
			restartGrabber();
		}
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
		if (GraphicsEnvironment.isHeadless()) {
			screenBounds = new Rectangle(0, 0, 1, 1);
			logger.info("Headless environment detected; using fallback screen bounds {}x{}", screenBounds.width, screenBounds.height);
			return;
		}
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

			stopCapture();

			logger.info("ScreenCapture shutdown completed");

		} catch (Exception e) {
			logger.error("Error during shutdown", e);
		}
	}

	/**
	 * Stops and releases the FFmpeg grabber but keeps the converter so capture can be restarted later.
	 * Safe to call multiple times.
	 */
	public synchronized void stopCapture() {
		isInitialized.set(false);
		if (grabber == null) {
			return;
		}
		try {
			grabber.stop();
		} catch (Exception e) {
			logger.debug("Ignoring grabber.stop failure", e);
		}
		try {
			grabber.close();
		} catch (Exception e) {
			logger.debug("Ignoring grabber.close failure", e);
		} finally {
			grabber = null;
		}
	}

	private boolean isNativeRegionCaptureSupported() {
		return "Windows".equals(platform) || "Linux".equals(platform);
	}

	private Rectangle determineInitialRegion(AppSettings.RegionSettings regionSettings) {
		if (regionSettings == null) {
			return new Rectangle(screenBounds);
		}

		Rectangle desired = regionSettings.toRectangle();
		return clampRegion(desired);
	}

	private Rectangle clampRegion(Rectangle region) {
		if (region == null) {
			return new Rectangle(screenBounds);
		}

		Rectangle clamped = new Rectangle(region);
		clamped = clamped.intersection(screenBounds);
		if (clamped.isEmpty() || clamped.width <= 0 || clamped.height <= 0) {
			logger.warn("Invalid region {}, falling back to full screen", region);
			return new Rectangle(screenBounds);
		}
		return clamped;
	}

	private void configureCaptureRegion() {
		Rectangle region = isFullScreen() ? screenBounds : captureRegion;
		grabber.setImageWidth(region.width);
		grabber.setImageHeight(region.height);

		if (isFullScreen()) {
			grabber.setOption("video_size", region.width + "x" + region.height);
			return;
		}

		if (!supportsNativeRegionCapture) {
			logger.info("Platform {} does not support native region capture; will crop in software", platform);
			grabber.setOption("video_size", screenBounds.width + "x" + screenBounds.height);
			return;
		}

		switch (platform) {
			case "Windows":
				grabber.setOption("video_size", region.width + "x" + region.height);
				grabber.setOption("offset_x", String.valueOf(region.x));
				grabber.setOption("offset_y", String.valueOf(region.y));
				break;
			case "Linux":
				grabber.setOption("video_size", region.width + "x" + region.height);
				grabber.setOption("grab_x", String.valueOf(region.x));
				grabber.setOption("grab_y", String.valueOf(region.y));
				break;
			default:
				logger.info("Native region capture not configured for platform {}, defaulting to cropping", platform);
				break;
		}
	}

	private boolean shouldCropFrame() {
		return !isFullScreen() && !supportsNativeRegionCapture;
	}

	private void restartGrabber() {
		try {
			initializeGrabber();
			logger.info("Capture region updated, grabber restarted for new bounds {}", captureRegion);
		} catch (Exception e) {
			logger.error("Failed to restart grabber after region update", e);
			isInitialized.set(false);
		}
	}
}
