package com.lansoftprogramming.runeSequence.ui.theme.themes.metal;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.IOException;
import java.io.InputStream;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;

final class ThemeImageLoader {

	private static final Logger logger = LoggerFactory.getLogger(ThemeImageLoader.class);

	private final Map<String, BufferedImage> cache = new ConcurrentHashMap<>();
	private final Class<?> anchorClass;

	ThemeImageLoader(Class<?> anchorClass) {
		this.anchorClass = Objects.requireNonNull(anchorClass, "anchorClass");
	}

	BufferedImage loadImage(String classpathResource) {
		return cache.computeIfAbsent(classpathResource, this::loadInternal);
	}

	private BufferedImage loadInternal(String classpathResource) {
		try (InputStream inputStream = anchorClass.getResourceAsStream(classpathResource)) {
			if (inputStream == null) {
				logger.warn("Theme resource not found on classpath: {}", classpathResource);
				throw new IllegalArgumentException("Theme resource not found on classpath: " + classpathResource);
			}
			BufferedImage image = ImageIO.read(inputStream);
			if (image == null) {
				logger.warn("Theme resource is not a supported image: {}", classpathResource);
				throw new IllegalArgumentException("Theme resource is not a supported image: " + classpathResource);
			}
			logger.debug("Loaded theme image {} ({}x{})", classpathResource, image.getWidth(), image.getHeight());
			return image;
		} catch (IOException e) {
			logger.warn("Failed to load theme image: {}", classpathResource, e);
			throw new IllegalStateException("Failed to load theme image: " + classpathResource, e);
		}
	}
}
