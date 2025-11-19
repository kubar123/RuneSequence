package com.lansoftprogramming.runeSequence.core.detection;

import com.lansoftprogramming.runeSequence.application.TemplateCache;
import com.lansoftprogramming.runeSequence.infrastructure.config.AbilityConfig;
import org.bytedeco.javacpp.DoublePointer;
import org.bytedeco.javacpp.Loader;
import org.bytedeco.javacpp.indexer.UByteIndexer;
import org.bytedeco.opencv.global.opencv_core;
import org.bytedeco.opencv.global.opencv_cudaimgproc;
import org.bytedeco.opencv.global.opencv_imgproc;
import org.bytedeco.opencv.opencv_core.GpuMat;
import org.bytedeco.opencv.opencv_core.Mat;
import org.bytedeco.opencv.opencv_core.MatVector;
import org.bytedeco.opencv.opencv_core.Point;
import org.bytedeco.opencv.opencv_core.Rect;
import org.bytedeco.opencv.opencv_cudaimgproc.TemplateMatching;
import org.bytedeco.opencv.opencv_core.DeviceInfo;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.awt.*;
import java.util.Collection;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import static org.bytedeco.opencv.global.opencv_imgproc.TM_SQDIFF_NORMED;

/**
 * GPU-accelerated template matcher using OpenCV CUDA modules via JavaCPP/Bytedeco.
 *
 * This implementation mirrors the semantics of {@link TemplateDetector} but offloads the
 * core matchTemplate operation to the GPU when available. If any CUDA-related error occurs,
 * callers are expected to catch the exception and fall back to a CPU-based matcher.
 */
public class GpuTemplateMatcher implements TemplateMatcher {
	private static final Logger logger = LoggerFactory.getLogger(GpuTemplateMatcher.class);

	// Once we detect that CUDA template matching is not available (e.g. missing native symbol),
	// we disable GPU matching for the remainder of the run to avoid repeated linkage errors.
	private static volatile boolean cudaTemplateMatchingAvailable = true;
	private static volatile boolean cudaEnvironmentReady = false;

	private static boolean ensureCudaReady() {
		if (cudaEnvironmentReady) {
			return true;
		}

		synchronized (GpuTemplateMatcher.class) {
			if (cudaEnvironmentReady) {
				return true;
			}

			try {
				// Force-load CUDA-capable modules. If the platform classifier is wrong, this will throw.
				Loader.load(opencv_cudaimgproc.class);
				int deviceCount = opencv_core.getCudaEnabledDeviceCount();

				if (deviceCount <= 0) {
					logger.warn("No CUDA-enabled devices detected; GPU matcher will not be used.");
					return false;
				}

				opencv_core.setDevice(0);
				DeviceInfo info = new DeviceInfo(0);

				boolean compatible = info.isCompatible();
				long memoryMb = info.totalGlobalMem() / (1024L * 1024L);
				int major = info.majorVersion();
				int minor = info.minorVersion();
				logger.info(
						"Detected CUDA device 0: {} (cc {}.{}, {} MB); compatible={}.",
						info.name().getString(),
						major,
						minor,
						memoryMb,
						compatible
				);
				info.close();

				if (!compatible) {
					return false;
				}

				cudaEnvironmentReady = true;
				return true;
			} catch (Throwable t) {
				logger.error("CUDA preflight failed; GPU matcher unavailable.", t);
				return false;
			}
		}
	}

	public static boolean selfTestCuda() {
		return ensureCudaReady();
	}

	private final TemplateCache templateCache;
	private final AbilityConfig abilityConfig;
	// CPU fallback matcher used for templates that require alpha masking
	// or when CUDA operations fail at runtime.
	private final TemplateDetector cpuFallbackMatcher;

	// Cache templates on GPU to reduce upload cost.
	private final Map<String, GpuTemplateData> gpuTemplateCache = new ConcurrentHashMap<>();

	public GpuTemplateMatcher(TemplateCache templateCache, AbilityConfig abilityConfig) {
		this.templateCache = templateCache;
		this.abilityConfig = abilityConfig;
		this.cpuFallbackMatcher = new TemplateDetector(templateCache, abilityConfig);
	}

	@Override
	public DetectionResult detectTemplate(Mat screen, String templateName) {
		return detectTemplate(screen, templateName, false);
	}

	@Override
	public Map<String, DetectionResult> cacheAbilityLocations(Mat screen, Collection<String> abilityKeys) {
		if (screen == null || screen.empty() || abilityKeys == null || abilityKeys.isEmpty()) {
			return Collections.emptyMap();
		}

		LinkedHashSet<String> uniqueKeys = new LinkedHashSet<>(abilityKeys);
		Map<String, DetectionResult> results = new ConcurrentHashMap<>();

		uniqueKeys.parallelStream().forEach(abilityKey -> {
			if (abilityKey == null || abilityKey.isEmpty()) {
				return;
			}
			if (!templateCache.hasTemplate(abilityKey)) {
				logger.debug("GPU pre-cache: template {} not loaded, skipping", abilityKey);
				return;
			}

			DetectionResult result = detectTemplate(screen, abilityKey);
			results.put(abilityKey, result);
		});

		return results;
	}

	@Override
	public DetectionResult detectTemplate(Mat screen, String templateName, boolean isAlternative) {
		if (!cudaTemplateMatchingAvailable || !ensureCudaReady()) {
			return cpuFallbackMatcher.detectTemplate(screen, templateName, isAlternative);
		}

		Mat template = templateCache.getTemplate(templateName);
		if (template == null) {
			logger.error("GPU TemplateMatcher: template not found in cache: {}", templateName);
			return DetectionResult.notFound(templateName, isAlternative);
		}

		if (template.cols() > screen.cols() || template.rows() > screen.rows()) {
			logger.debug("GPU template {} larger than screen; skipping", templateName);
			return DetectionResult.notFound(templateName, isAlternative);
		}

		double threshold = getThresholdForTemplate(templateName);

		Mat screenBgr = null;
		GpuMat gpuScreen = null;
		GpuTemplateData gpuData = null;
		GpuMat gpuResult = null;
		Mat resultCpu = null;
		DoublePointer minVal = null;
		DoublePointer maxVal = null;
		Point minLoc = null;
		Point maxLoc = null;

		try {
			// Ensure color layout is compatible with template (CPU-side).
			// We convert BGRA -> BGR so that both template and screen are 3-channel on the GPU.
			screenBgr = ensureColorConsistency(screen);

			gpuScreen = new GpuMat();
			gpuScreen.upload(screenBgr);

			// Upload or reuse template on GPU (with cropping information based on alpha).
			gpuData = gpuTemplateCache.computeIfAbsent(templateName, key -> createGpuTemplateData(key, template));

			gpuResult = new GpuMat();

			// Run CUDA template matching (SQDIFF_NORMED like CPU version) on the cropped template.
			TemplateMatching matcher = opencv_cudaimgproc.createTemplateMatching(screenBgr.type(), TM_SQDIFF_NORMED);
			matcher.match(gpuScreen, gpuData.template, gpuResult);
			matcher.close();

			// Download result back to CPU for min/max search
			resultCpu = new Mat();
			gpuResult.download(resultCpu);

			minVal = new DoublePointer(1);
			maxVal = new DoublePointer(1);
			minLoc = new Point();
			maxLoc = new Point();

			opencv_core.minMaxLoc(resultCpu, minVal, maxVal, minLoc, maxLoc, null);

			double minValD = minVal.get();
			double confidence = 1.0 - minValD;
			boolean found = confidence >= threshold;

			if (found) {
				Rectangle croppedBounds = gpuData.createCroppedBoundingBox(minLoc.x(), minLoc.y());

				// Sanity check: the cropped template must lie within screen bounds.
				if (!isWithinScreen(croppedBounds, screenBgr)) {
					logger.debug(
							"GPU match for {} exceeded screen bounds after cropping (bbox={}, cropOffset=({}, {}), originalSize={}x{}).",
							templateName,
							croppedBounds,
							gpuData.cropOffsetX,
							gpuData.cropOffsetY,
							gpuData.originalWidth,
							gpuData.originalHeight
					);
					return DetectionResult.notFound(templateName, isAlternative);
				}

				return DetectionResult.found(
						templateName,
						new java.awt.Point(croppedBounds.x, croppedBounds.y),
						confidence,
						croppedBounds,
						isAlternative
				);
			}

			return DetectionResult.notFound(templateName, isAlternative);

		} catch (Throwable t) {
			// Any CUDA-related error here should cause a transparent fallback to CPU
			// so that detection continues to work even when GPU is unavailable
			// or misconfigured.
			logger.error("GPU template matching failed for {}; falling back to CPU matcher.", templateName, t);
			// If this is a linkage problem (missing native symbol), permanently disable
			// further attempts to use the CUDA template matcher in this run.
			if (t instanceof UnsatisfiedLinkError) {
				cudaTemplateMatchingAvailable = false;
				logger.warn("Disabling CUDA template matching for the remainder of this run due to UnsatisfiedLinkError.");
			}
			return cpuFallbackMatcher.detectTemplate(screen, templateName, isAlternative);
		} finally {
			if (gpuScreen != null) {
				gpuScreen.close();
			}
			if (gpuResult != null) {
				gpuResult.close();
			}
			if (resultCpu != null) {
				resultCpu.close();
			}
			if (minVal != null) {
				minVal.close();
			}
			if (maxVal != null) {
				maxVal.close();
			}
			if (minLoc != null) {
				minLoc.close();
			}
			if (maxLoc != null) {
				maxLoc.close();
			}
			// If ensureColorConsistency allocated a new Mat, close it.
			if (screenBgr != null && screenBgr != screen) {
				screenBgr.close();
			}
		}
	}

	private double getThresholdForTemplate(String templateName) {
		AbilityConfig.AbilityData abilityData = abilityConfig.getAbility(templateName);
		if (abilityData != null && abilityData.getDetectionThreshold() != null) {
			return abilityData.getDetectionThreshold();
		}
		return 0.99;
	}

	private static boolean isWithinScreen(Rectangle boundingBox, Mat screen) {
		return boundingBox.x >= 0 && boundingBox.y >= 0
				&& boundingBox.x + boundingBox.width <= screen.cols()
				&& boundingBox.y + boundingBox.height <= screen.rows();
	}

	/**
	 * Convert screen to an appropriate 3-channel layout if needed to match template.
	 * We normalize to BGR on the GPU path.
	 */
	private Mat ensureColorConsistency(Mat screen) {
		if (screen.channels() == 4) {
			Mat converted = new Mat();
			opencv_imgproc.cvtColor(screen, converted, opencv_imgproc.COLOR_BGRA2BGR);
			return converted;
		}
		return screen;
	}

	/**
	 * Create GPU template from the original template Mat.
	 * If the template has an alpha channel, we compute the bounding box of
	 * non-transparent pixels from the alpha channel and crop to that region
	 * to approximate masked matching while keeping the heavy computation on GPU.
	 */
	private GpuTemplateData createGpuTemplateData(String templateName, Mat template) {
		Mat workingTemplate = null;
		MatVector channels = null;
		MatVector bgrChannels = null;
		GpuMat gpuTemplate = null;

		int croppedWidth;
		int croppedHeight;
		int cropOffsetX = 0;
		int cropOffsetY = 0;
		final int originalWidth = template.cols();
		final int originalHeight = template.rows();

		try {
			if (template.channels() == 4) {
				// Split template into BGRA
				channels = new MatVector(4);
				opencv_core.split(template, channels);

				Mat alpha = channels.get(3);

				// Compute bounding box of non-transparent pixels in alpha channel
				Rect bbox = computeNonZeroBoundingBox(alpha);
				// Merge BGR channels into a 3-channel template, cropping if we have a bbox
				bgrChannels = new MatVector(3);
				if (bbox != null) {
					cropOffsetX = bbox.x();
					cropOffsetY = bbox.y();

					bgrChannels.put(0, new Mat(channels.get(0), bbox));
					bgrChannels.put(1, new Mat(channels.get(1), bbox));
					bgrChannels.put(2, new Mat(channels.get(2), bbox));
				} else {
					bgrChannels.put(0, channels.get(0));
					bgrChannels.put(1, channels.get(1));
					bgrChannels.put(2, channels.get(2));
				}

				workingTemplate = new Mat();
				opencv_core.merge(bgrChannels, workingTemplate);
			} else {
				// No alpha channel; use the original template directly
				workingTemplate = template;
			}

			if (workingTemplate == null) {
				throw new IllegalStateException("Working template was not initialized for " + templateName);
			}

			croppedWidth = workingTemplate.cols();
			croppedHeight = workingTemplate.rows();

			gpuTemplate = new GpuMat();
			gpuTemplate.upload(workingTemplate);

			return new GpuTemplateData(
					gpuTemplate,
					croppedWidth,
					croppedHeight,
					cropOffsetX,
					cropOffsetY,
					originalWidth,
					originalHeight
			);
		} catch (Throwable t) {
			logger.error("Failed to upload template {} to GPU", templateName, t);
			if (gpuTemplate != null) {
				gpuTemplate.close();
			}
			throw new RuntimeException("Failed to upload template to GPU", t);
		} finally {
			if (workingTemplate != null && workingTemplate != template) {
				workingTemplate.close();
			}
			if (channels != null) {
				channels.close();
			}
			if (bgrChannels != null) {
				bgrChannels.close();
			}
		}
	}

	/**
	 * Compute an axis-aligned bounding box of non-zero pixels in the given single-channel Mat.
	 * Returns null if no non-zero pixels are found.
	 */
	private Rect computeNonZeroBoundingBox(Mat alpha) {
		int rows = alpha.rows();
		int cols = alpha.cols();

		int minX = cols;
		int minY = rows;
		int maxX = -1;
		int maxY = -1;

		UByteIndexer indexer = null;
		try {
			indexer = (UByteIndexer) alpha.createIndexer();
			for (int y = 0; y < rows; y++) {
				for (int x = 0; x < cols; x++) {
					int value = indexer.get(y, x) & 0xFF;
					if (value != 0) {
						if (x < minX) minX = x;
						if (y < minY) minY = y;
						if (x > maxX) maxX = x;
						if (y > maxY) maxY = y;
					}
				}
			}
		} finally {
			if (indexer != null) {
				indexer.close();
			}
		}

		if (maxX < 0 || maxY < 0) {
			return null;
		}

		int width = maxX - minX + 1;
		int height = maxY - minY + 1;
		return new Rect(minX, minY, width, height);
	}

	/**
	 * Holder for GPU-resident template data.
	 */
	private static final class GpuTemplateData {
		final GpuMat template;
		final int croppedWidth;
		final int croppedHeight;
		final int cropOffsetX;
		final int cropOffsetY;
		final int originalWidth;
		final int originalHeight;

		private GpuTemplateData(GpuMat template,
		                        int croppedWidth,
		                        int croppedHeight,
		                        int cropOffsetX,
		                        int cropOffsetY,
		                        int originalWidth,
		                        int originalHeight) {
			this.template = template;
			this.croppedWidth = croppedWidth;
			this.croppedHeight = croppedHeight;
			this.cropOffsetX = cropOffsetX;
			this.cropOffsetY = cropOffsetY;
			this.originalWidth = originalWidth;
			this.originalHeight = originalHeight;
		}

		private Rectangle createCroppedBoundingBox(int screenX, int screenY) {
			return new Rectangle(screenX, screenY, croppedWidth, croppedHeight);
		}
	}

	/**
	 * Release any cached GPU templates. Intended for shutdown, not currently wired.
	 */
	public void shutdown() {
		gpuTemplateCache.values().forEach(data -> {
			if (data.template != null) {
				data.template.close();
			}
		});
		gpuTemplateCache.clear();
	}
}
