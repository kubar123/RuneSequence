package com.lansoftprogramming.runeSequence.ui.shared.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.imageio.ImageIO;
import javax.swing.*;
import java.awt.*;
import java.awt.image.BufferedImage;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.Map;

/**
 * Service responsible for loading and caching ability icons.
 * Provides lazy loading with caching to improve performance.
 */
public class AbilityIconLoader {
	private static final Logger logger = LoggerFactory.getLogger(AbilityIconLoader.class);
	private static final int DEFAULT_ICON_SIZE = 48;

	private final Path abilityImagePath;
	private final int iconSize;
	private final Map<String, ImageIcon> iconCache;
	private final Path resolvedSizeFolder; // expected to be provided by callers
	private final int resolvedFolderSize;
	private ImageIcon placeholderIcon;

	public AbilityIconLoader(Path abilityImagePath) {
		this(abilityImagePath, DEFAULT_ICON_SIZE);
	}

	public AbilityIconLoader(Path abilityImagePath, int iconSize) {
		this(abilityImagePath, iconSize, null);
	}

	/**
	 * @param abilityImagePath base directory containing ability icons
	 * @param iconSize desired UI icon size
	 * @param resolvedSizeFolder folder containing pre-sized icons, or null when unavailable
	 */
	public AbilityIconLoader(Path abilityImagePath, int iconSize, Path resolvedSizeFolder) {
		this.abilityImagePath = abilityImagePath;
		this.iconSize = iconSize;
		this.iconCache = new HashMap<>();
		this.resolvedSizeFolder = resolvedSizeFolder;
		this.resolvedFolderSize = parseFolderSize(resolvedSizeFolder);
	}

	/**
	 * Loads an icon for the specified ability key.
	 * Returns a cached icon if available, otherwise loads from disk.
	 *
	 * @param abilityKey the ability key
	 * @return the icon, or a placeholder if not found
	 */
	public ImageIcon loadIcon(String abilityKey) {
		if (abilityKey == null || abilityKey.isEmpty()) {
			return getPlaceholderIcon();
		}

		// Check cache first
		if (iconCache.containsKey(abilityKey)) {
			return iconCache.get(abilityKey);
		}

		// Try to load from disk
		ImageIcon icon = loadIconFromDisk(abilityKey);
		iconCache.put(abilityKey, icon);
		return icon;
	}

	/**
	 * Loads an icon from disk.
	 */
	private ImageIcon loadIconFromDisk(String abilityKey) {
		try {
			// Prefer preprocessed images inside the size-named subfolder in AppData if present
			Path sizedImageFile = null;
			if (resolvedSizeFolder != null && Files.isDirectory(resolvedSizeFolder)) {
				sizedImageFile = resolvedSizeFolder.resolve(abilityKey + ".png");
			}

			if (sizedImageFile != null && Files.exists(sizedImageFile)) {
				BufferedImage img = ImageIO.read(sizedImageFile.toFile());
				if (img != null) {
					// If the resolved folder size does not exactly match the requested iconSize,
					// rescale so we still respect the configured size.
					if (resolvedFolderSize > 0 && resolvedFolderSize != iconSize) {
						Image scaledImg = img.getScaledInstance(iconSize, iconSize, Image.SCALE_SMOOTH);
						return new ImageIcon(scaledImg);
					}
					return new ImageIcon(img);
				}
			}

			// Fallback to base folder and scale at runtime
			Path imageFile = abilityImagePath.resolve(abilityKey + ".png");
			if (Files.exists(imageFile)) {
				BufferedImage img = ImageIO.read(imageFile.toFile());
				if (img != null) {
					Image scaledImg = img.getScaledInstance(iconSize, iconSize, Image.SCALE_SMOOTH);
					return new ImageIcon(scaledImg);
				}
			}
		} catch (IOException e) {
			logger.debug("Could not load icon for ability: {}", abilityKey, e);
		}

		return getPlaceholderIcon();
	}

	private int parseFolderSize(Path folder) {
		if (folder == null) {
			return -1;
		}
		try {
			return Integer.parseInt(folder.getFileName().toString());
		} catch (NumberFormatException ignored) {
			return -1;
		}
	}

	/**
	 * Gets or creates the placeholder icon.
	 */
	private ImageIcon getPlaceholderIcon() {
		if (placeholderIcon == null) {
			placeholderIcon = createPlaceholderIcon();
		}
		return placeholderIcon;
	}

	/**
	 * Creates a placeholder icon.
	 */
	private ImageIcon createPlaceholderIcon() {
		BufferedImage placeholder = new BufferedImage(iconSize, iconSize, BufferedImage.TYPE_INT_ARGB);
		Graphics2D g2d = placeholder.createGraphics();

		// Enable antialiasing for smoother edges
		g2d.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);

		// Fill with light gray
		g2d.setColor(new Color(220, 220, 220));
		g2d.fillRect(0, 0, iconSize, iconSize);

		// Draw border
		g2d.setColor(new Color(180, 180, 180));
		g2d.drawRect(0, 0, iconSize - 1, iconSize - 1);

		// Draw question mark in center
		g2d.setColor(new Color(150, 150, 150));
		g2d.setFont(new Font("SansSerif", Font.BOLD, iconSize / 2));
		FontMetrics fm = g2d.getFontMetrics();
		String questionMark = "?";
		int x = (iconSize - fm.stringWidth(questionMark)) / 2;
		int y = ((iconSize - fm.getHeight()) / 2) + fm.getAscent();
		g2d.drawString(questionMark, x, y);

		g2d.dispose();
		return new ImageIcon(placeholder);
	}

	/**
	 * Clears the icon cache.
	 * Useful for reloading icons after changes.
	 */
	public void clearCache() {
		iconCache.clear();
		placeholderIcon = null;
		logger.debug("Icon cache cleared");
	}

	/**
	 * Pre-loads icons for the specified ability keys.
	 * Useful for improving perceived performance.
	 *
	 * @param abilityKeys the ability keys to preload
	 */
	public void preloadIcons(Iterable<String> abilityKeys) {
		for (String key : abilityKeys) {
			loadIcon(key);
		}
		logger.debug("Preloaded {} icons", iconCache.size());
	}
}
