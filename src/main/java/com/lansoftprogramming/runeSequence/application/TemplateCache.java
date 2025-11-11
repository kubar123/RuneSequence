package com.lansoftprogramming.runeSequence.application;

import com.lansoftprogramming.runeSequence.infrastructure.config.ScalingConverter;
import org.bytedeco.opencv.global.opencv_imgcodecs;
import org.bytedeco.opencv.opencv_core.Mat;
import org.bytedeco.opencv.opencv_core.Size;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.stream.Stream;

import static org.bytedeco.opencv.global.opencv_imgcodecs.IMREAD_UNCHANGED;

public class TemplateCache {
	private static final Logger logger = LoggerFactory.getLogger(TemplateCache.class);
	private final Map<String, TemplateData> cache = new ConcurrentHashMap<>();
	private final ExecutorService backgroundLoader = Executors.newCachedThreadPool();
	private final int scaleInt;
	private final String appName;
	private final Path imagePath;

	public TemplateCache(String appName, int scalingPercent) {
		this.appName = appName;
		this.scaleInt = ScalingConverter.getScaling(scalingPercent);
		this.imagePath = getAppDataPath();
		initialize();
	}

	public TemplateCache(String appName) {
		this.appName = appName;
		this.scaleInt = ScalingConverter.getScaling(100); // default
		this.imagePath = getAppDataPath();
		initialize();
	}

	public static class TemplateData {
		private final String name;
		private final Mat template;
		private final Size size;


		public TemplateData(String name, Mat template) {
			this.name = name;
			this.template = template.clone(); // deep copy
			this.size = template.size();
		}

		public String getName() {
			return name;
		}

		public Mat getTemplate() {
			return template;
		}

		public Size getSize() {
			return size;
		}

		public void close() {
			template.close();
		}
	}

	/**
	 * Initialize the cache by loading all templates in the app’s folder (non-recursive).
	 */
	public int initialize() {
		int count = 0;
		logger.info("Loading templates from: {}", imagePath);

		if (!Files.exists(imagePath)) {
			logger.error("Directory does not exist: {}", imagePath);
			return 0;
		}

		try (Stream<Path> files = Files.list(imagePath)) { // non-recursive
			for (Path file : files.filter(Files::isRegularFile)
					.filter(p -> isImageFile(p.toString()))
					.toList()) {

				String name = stripExtension(file.getFileName().toString());
				if (cacheTemplate(name, file)) {
					count++;
				}
			}
		} catch (IOException e) {
			throw new RuntimeException("Failed to initialize template cache", e);
		}

		logger.info("Loaded {} templates", count);
		return count;
	}

	private boolean cacheTemplate(String abilityName, Path file) {
		Mat mat = opencv_imgcodecs.imread(file.toString(), IMREAD_UNCHANGED);
		if (mat.empty()) {
			logger.warn("Failed to load image: {}", file);
			return false;
		}
		cache.put(abilityName, new TemplateData(abilityName, mat));
		mat.close(); // safe because TemplateData clones it
		return true;
	}


	private String stripExtension(String filename) {
		int idx = filename.lastIndexOf('.');
		return (idx > 0) ? filename.substring(0, idx) : filename;
	}

	private boolean isImageFile(String filename) {
		String lower = filename.toLowerCase();
		return lower.endsWith(".png") || lower.endsWith(".jpg")
				|| lower.endsWith(".jpeg") || lower.endsWith(".bmp");
	}

	public Mat getTemplate(String abilityName) {
		TemplateData data = cache.get(abilityName);
		return data != null ? data.getTemplate() : null;
	}

	public boolean hasTemplate(String abilityName) {
		return cache.containsKey(abilityName);
	}

	public int getCacheSize() {
		return cache.size();
	}

	public void shutdown() {
		cache.values().forEach(TemplateData::close);
		cache.clear();
		backgroundLoader.shutdown();
	}

	private Path getAppDataPath() {
		String abilityFolderName = "Abilities";
		String os = System.getProperty("os.name").toLowerCase();
		if (os.contains("win")) {
			return Paths.get(System.getenv("APPDATA"), this.appName, abilityFolderName, scaleInt + "");
		} else if (os.contains("mac")) {
			return Paths.get(System.getProperty("user.home"), "Library", "Application Support", appName,
					abilityFolderName, scaleInt + "");
		} else {
			return Paths.get(System.getProperty("user.home"), "." + appName.toLowerCase(), abilityFolderName,
					scaleInt + "");
		}
	}
}
