package com.lansoftprogramming.runeSequence.core.image;

import com.lansoftprogramming.runeSequence.infrastructure.config.AbilityConfig;
import org.bytedeco.opencv.global.opencv_core;
import org.bytedeco.opencv.global.opencv_imgcodecs;
import org.bytedeco.opencv.global.opencv_imgproc;
import org.bytedeco.opencv.opencv_core.Mat;
import org.bytedeco.opencv.opencv_core.MatVector;
import org.bytedeco.opencv.opencv_core.Size;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Optional;
import java.util.stream.Stream;

import static org.bytedeco.opencv.global.opencv_imgcodecs.IMREAD_UNCHANGED;

public class OpenCvImageProcessor implements ImageProcessor {

	@Override
	public void processImages(Path abilitiesRoot, int[] sizes, AbilityConfig abilityConfig) throws IOException {
		if (!Files.exists(abilitiesRoot))
			throw new IOException("Abilities folder not found: " + abilitiesRoot);

		try (Stream<Path> files = Files.list(abilitiesRoot)) {
			files.filter(Files::isRegularFile)
					.filter(p -> p.toString().toLowerCase().matches(".*\\.(png|jpg|jpeg)$"))
					.forEach(file -> {
						if (!file.getFileName().toString().equalsIgnoreCase("mask.png")
								&& !file.getFileName().toString().equalsIgnoreCase("mask_potion.png")) {
							MaskSelection selectedMask = selectMaskForAbility(file, abilitiesRoot, abilityConfig);
							processSingleImage(file, selectedMask, abilitiesRoot, sizes);
						}
					});
		}
	}

	private MaskSelection selectMaskForAbility(Path imgPath, Path abilitiesRoot, AbilityConfig abilityConfig) {
		String filename = imgPath.getFileName().toString();
		String abilityKey = stripExtension(filename);

		// Legacy fallback: if no config is available, keep using the default mask
		if (abilityConfig == null || abilityConfig.getAbilities() == null) {
			return MaskSelection.withMask(abilitiesRoot.resolve("mask.png"), false);
		}

		AbilityConfig.AbilityData data = abilityConfig.getAbility(abilityKey);
		if (data != null) {
			String maskKey = safeTrim(data.getMask());
			if (maskKey == null) {
				return MaskSelection.noMask(); // explicitly no mask
			}
			Path maskPath = resolveMaskPath(maskKey, abilitiesRoot);
			boolean preserveAspect = !"mask".equalsIgnoreCase(maskKey);
			return MaskSelection.withMask(maskPath, preserveAspect);
		}
		return MaskSelection.withMask(abilitiesRoot.resolve("mask.png"), false);
	}

	private Path resolveMaskPath(String maskKey, Path abilitiesRoot) {
		// Allow simple names like "mask" or "mask_potion" and also custom filenames
		String fileName = maskKey.endsWith(".png") || maskKey.endsWith(".jpg") || maskKey.endsWith(".jpeg")
				? maskKey
				: maskKey + ".png";
		Path resolved = abilitiesRoot.resolve(fileName);
		if (!Files.exists(resolved)) {
			throw new IllegalArgumentException("Requested mask file not found: " + resolved);
		}
		return resolved;
	}

	private String stripExtension(String filename) {
		int dot = filename.lastIndexOf('.');
		return dot > 0 ? filename.substring(0, dot) : filename;
	}

	private String safeTrim(String value) {
		return value == null ? null : value.trim();
	}

	private void processSingleImage(Path imgPath, MaskSelection maskSelection, Path root, int[] sizes) {
		Mat original = opencv_imgcodecs.imread(imgPath.toString(), IMREAD_UNCHANGED);
		Mat mask = maskSelection.maskPath
				.map(path -> opencv_imgcodecs.imread(path.toString(), IMREAD_UNCHANGED))
				.orElse(null);

		for (int s : sizes) {
			try {
				Path dir = root.resolve(String.valueOf(s));
				Files.createDirectories(dir);
				int targetHeight = s;
				int targetWidth = maskSelection.preserveAspect
						? Math.max(1, Math.round(original.cols() * (targetHeight / (float) original.rows())))
						: s;

				int interpolation = maskSelection.preserveAspect
						? opencv_imgproc.INTER_NEAREST  // keep pixel edges sharp for non-standard masks / no mask
						: opencv_imgproc.INTER_CUBIC;   // legacy behavior for square masked icons

				Mat resized = new Mat();
				opencv_imgproc.resize(original, resized, new Size(targetWidth, targetHeight), 0, 0, interpolation);

				Mat finalImg;
				if (mask != null) {
					// Resize mask with nearest neighbor
					Mat maskResized = new Mat();
					opencv_imgproc.resize(mask, maskResized, new Size(targetWidth, targetHeight), 0, 0, opencv_imgproc.INTER_NEAREST);
					finalImg = applyMask(resized, maskResized);
					maskResized.close();
				} else {
					finalImg = resized.clone(); // keep without mask
				}

				Path outPath = dir.resolve(imgPath.getFileName().toString());
				opencv_imgcodecs.imwrite(outPath.toString(), finalImg);

				resized.close();
				finalImg.close();
			} catch (IOException e) {
				throw new RuntimeException(e);
			}
		}

		original.close();
		if (mask != null) {
			mask.close();
		}
	}

	private static class MaskSelection {
		private final Optional<Path> maskPath;
		private final boolean preserveAspect;

		private MaskSelection(Optional<Path> maskPath, boolean preserveAspect) {
			this.maskPath = maskPath;
			this.preserveAspect = preserveAspect;
		}

		static MaskSelection withMask(Path path, boolean preserveAspect) {
			return new MaskSelection(Optional.of(path), preserveAspect);
		}

		static MaskSelection noMask() {
			return new MaskSelection(Optional.empty(), true);
		}
	}

	private Mat applyMask(Mat src, Mat mask) {
		Mat result = new Mat();
		Mat maskGray = new Mat();
		Mat alpha = new Mat();

		// Convert to grayscale if needed
		if (mask.channels() > 1)
			opencv_imgproc.cvtColor(mask, maskGray, opencv_imgproc.COLOR_BGR2GRAY);
		else
			maskGray = mask.clone();

		// White = keep, black = transparent
		opencv_imgproc.threshold(maskGray, alpha, 1, 255, opencv_imgproc.THRESH_BINARY);

		Mat srcBgra = new Mat();
		opencv_imgproc.cvtColor(src, srcBgra, opencv_imgproc.COLOR_BGR2BGRA);

		MatVector channels = new MatVector();
		opencv_core.split(srcBgra, channels);

		// Replace alpha channel
		MatVector merged = new MatVector(4);
		merged.put(0, channels.get(0)); // B
		merged.put(1, channels.get(1)); // G
		merged.put(2, channels.get(2)); // R
		merged.put(3, alpha);           // A

		opencv_core.merge(merged, result);

		channels.deallocate();
		srcBgra.close();
		maskGray.close();
		alpha.close();

		return result;
	}

}