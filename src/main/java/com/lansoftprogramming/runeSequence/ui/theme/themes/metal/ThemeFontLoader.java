package com.lansoftprogramming.runeSequence.ui.theme.themes.metal;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.awt.*;
import java.io.IOException;
import java.io.InputStream;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;

final class ThemeFontLoader {

	private static final Logger logger = LoggerFactory.getLogger(ThemeFontLoader.class);

	private final Map<String, Font> cache = new ConcurrentHashMap<>();
	private final Class<?> anchorClass;

	ThemeFontLoader(Class<?> anchorClass) {
		this.anchorClass = Objects.requireNonNull(anchorClass, "anchorClass");
	}

	Font loadFont(String classpathResource) {
		return cache.computeIfAbsent(classpathResource, this::loadInternal);
	}

	private Font loadInternal(String classpathResource) {
		try (InputStream inputStream = anchorClass.getResourceAsStream(classpathResource)) {
			if (inputStream == null) {
				logger.warn("Theme font resource not found on classpath: {}", classpathResource);
				throw new IllegalArgumentException("Theme font resource not found on classpath: " + classpathResource);
			}
			Font font = Font.createFont(Font.TRUETYPE_FONT, inputStream);
			logger.debug("Loaded theme font {}", classpathResource);
			return font;
		} catch (IOException | FontFormatException e) {
			logger.warn("Failed to load theme font: {}", classpathResource, e);
			throw new IllegalStateException("Failed to load theme font: " + classpathResource, e);
		}
	}
}
