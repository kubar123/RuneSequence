package com.lansoftprogramming.runeSequence.ui.shared;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.imageio.ImageIO;
import java.awt.*;
import java.awt.image.BufferedImage;
import java.net.URL;
import java.util.ArrayList;
import java.util.List;

public final class AppIcon {
	private static final Logger logger = LoggerFactory.getLogger(AppIcon.class);

	private AppIcon() {
	}

	public static Image load() {
		List<IconVariant> variants = loadIconFolderVariants();
		if (!variants.isEmpty()) {
			IconVariant selected = variants.get(variants.size() - 1);
			logger.info("Selected app icon {} ({}x{}) as primary.", selected.path, selected.width, selected.height);
			return selected.image;
		}
		logger.error("No app icons found under /icon/. Expected files like /icon/icon_16x16.png.");
		return null;
	}

	public static List<Image> loadWindowIcons() {
		List<IconVariant> variants = loadIconFolderVariants();
		if (!variants.isEmpty()) {
			List<IconVariant> sorted = new ArrayList<>(variants);
			sorted.sort((a, b) -> Integer.compare(Math.max(b.width, b.height), Math.max(a.width, a.height)));
			IconVariant primary = sorted.get(0);
			logger.info("Selected window/taskbar icon {} ({}x{}) as primary.", primary.path, primary.width, primary.height);
			return sorted.stream().map(v -> (Image) v.image).toList();
		}

		logger.error("No window icons found under /icon/. Expected files like /icon/icon_16x16.png.");
		return List.of();
	}

	public static Image loadForTray(Dimension trayIconSize) {
		List<IconVariant> variants = loadIconFolderVariants();
		if (!variants.isEmpty()) {
			return pickBestForSize(variants, trayIconSize);
		}
		logger.error("No tray icons found under /icon/. Expected files like /icon/icon_16x16.png.");
		return null;
	}

	private static List<IconVariant> loadIconFolderVariants() {
		int[] sizes = {16, 20, 24, 32, 40, 48, 64, 96, 128, 256};
		List<IconVariant> icons = new ArrayList<>();
		for (int size : sizes) {
			String path = String.format("/icon/icon_%dx%d.png", size, size);
			BufferedImage img = loadPng(path);
			if (img != null) {
				icons.add(new IconVariant(path, img, img.getWidth(), img.getHeight()));
				logger.debug("Loaded app icon resource {}", path);
			} else {
				logger.debug("Missing app icon resource {}", path);
			}
		}
		if (!icons.isEmpty()) {
			logger.info("Loaded {} app icon(s) from /icon/.", icons.size());
		}
		return icons;
	}

	private static BufferedImage loadPng(String path) {
		URL url = AppIcon.class.getResource(path);
		if (url == null) {
			return null;
		}
		try {
			return ImageIO.read(url);
		} catch (Exception e) {
			logger.warn("Failed reading app icon resource {}", path, e);
			return null;
		}
	}

	private static Image pickBestForSize(List<IconVariant> candidates, Dimension targetSize) {
		if (candidates == null || candidates.isEmpty()) {
			return null;
		}
		if (targetSize == null || targetSize.width <= 0 || targetSize.height <= 0) {
			IconVariant selected = candidates.get(candidates.size() - 1);
			logger.info("Selected tray icon {} ({}x{}) (no target size).", selected.path, selected.width, selected.height);
			return selected.image;
		}

		int target = Math.max(targetSize.width, targetSize.height);
		IconVariant best = candidates.get(0);
		int bestScore = Integer.MAX_VALUE;
		for (IconVariant candidate : candidates) {
			int size = Math.max(candidate.width, candidate.height);
			int score = Math.abs(size - target);
			if (score < bestScore) {
				best = candidate;
				bestScore = score;
			}
		}
		logger.info(
				"Selected tray icon {} ({}x{}) best-fit for target {}x{}.",
				best.path,
				best.width,
				best.height,
				targetSize.width,
				targetSize.height
		);
		return best.image;
	}

	private static int safeWidth(Image image) {
		int width = image != null ? image.getWidth(null) : -1;
		return width > 0 ? width : 0;
	}

	private static int safeHeight(Image image) {
		int height = image != null ? image.getHeight(null) : -1;
		return height > 0 ? height : 0;
	}

	private record IconVariant(String path, BufferedImage image, int width, int height) {
	}

	private static BufferedImage trimTransparent(BufferedImage source, int alphaThresholdInclusive) {
		int width = source.getWidth();
		int height = source.getHeight();

		int minX = width;
		int minY = height;
		int maxX = -1;
		int maxY = -1;

		for (int y = 0; y < height; y++) {
			for (int x = 0; x < width; x++) {
				int argb = source.getRGB(x, y);
				int alpha = (argb >>> 24) & 0xFF;
				if (alpha > alphaThresholdInclusive) {
					if (x < minX) minX = x;
					if (y < minY) minY = y;
					if (x > maxX) maxX = x;
					if (y > maxY) maxY = y;
				}
			}
		}

		if (maxX < 0 || maxY < 0) {
			return source;
		}

		int croppedWidth = (maxX - minX) + 1;
		int croppedHeight = (maxY - minY) + 1;
		if (croppedWidth == width && croppedHeight == height) {
			return source;
		}

		BufferedImage cropped = new BufferedImage(croppedWidth, croppedHeight, BufferedImage.TYPE_INT_ARGB);
		Graphics2D g = cropped.createGraphics();
		try {
			g.setComposite(AlphaComposite.Src);
			g.drawImage(source, -minX, -minY, null);
		} finally {
			g.dispose();
		}
		return cropped;
	}

	private static Image scaleToFitHighQuality(BufferedImage source, int width, int height, double fillPercent) {
		int srcWidth = source.getWidth();
		int srcHeight = source.getHeight();

		double availableWidth = Math.max(1.0, width * fillPercent);
		double availableHeight = Math.max(1.0, height * fillPercent);
		double scale = Math.min(availableWidth / srcWidth, availableHeight / srcHeight);

		int targetWidth = Math.max(1, (int) Math.round(srcWidth * scale));
		int targetHeight = Math.max(1, (int) Math.round(srcHeight * scale));

		BufferedImage scaled = new BufferedImage(width, height, BufferedImage.TYPE_INT_ARGB);
		Graphics2D g2d = scaled.createGraphics();
		try {
			g2d.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BICUBIC);
			g2d.setRenderingHint(RenderingHints.KEY_RENDERING, RenderingHints.VALUE_RENDER_QUALITY);
			g2d.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);

			g2d.setComposite(AlphaComposite.Clear);
			g2d.fillRect(0, 0, width, height);
			g2d.setComposite(AlphaComposite.SrcOver);

			int x = (width - targetWidth) / 2;
			int y = (height - targetHeight) / 2;
			g2d.drawImage(source, x, y, targetWidth, targetHeight, null);
		} finally {
			g2d.dispose();
		}
		return scaled;
	}
}