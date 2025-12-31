package com.lansoftprogramming.runeSequence.ui.shared.icons;

import javax.swing.*;
import java.awt.*;
import java.net.URL;
import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

public final class IconLoader {
	private static final ConcurrentMap<CacheKey, ImageIcon> iconCache = new ConcurrentHashMap<>();

	private IconLoader() {
	}

	public static ImageIcon loadScaledOrNull(String resourcePath, int width, int height) {
		if (resourcePath == null || resourcePath.isBlank() || width <= 0 || height <= 0) {
			return null;
		}
		String normalizedPath = resourcePath.startsWith("/") ? resourcePath : "/" + resourcePath;
		CacheKey cacheKey = new CacheKey(normalizedPath, width, height);
		ImageIcon cached = iconCache.get(cacheKey);
		if (cached != null) {
			return cached;
		}

		ImageIcon loaded = loadScaledOrNullUncached(normalizedPath, width, height);
		if (loaded != null) {
			iconCache.putIfAbsent(cacheKey, loaded);
		}
		return loaded;
	}

	private static ImageIcon loadScaledOrNullUncached(String resourcePath, int width, int height) {
		try {
			URL iconUrl = IconLoader.class.getResource(resourcePath);
			if (iconUrl == null) {
				return null;
			}
			ImageIcon originalIcon = new ImageIcon(iconUrl);
			Image image = originalIcon.getImage();
			if (image == null) {
				return null;
			}
			Image scaledImage = image.getScaledInstance(width, height, Image.SCALE_SMOOTH);
			return new ImageIcon(scaledImage);
		} catch (Exception ignored) {
			return null;
		}
	}

	private record CacheKey(String resourcePath, int width, int height) {
		private CacheKey {
			Objects.requireNonNull(resourcePath, "resourcePath");
		}
	}
}
