package com.lansoftprogramming.runeSequence.core.image;

import org.bytedeco.opencv.global.opencv_core;
import org.bytedeco.opencv.global.opencv_imgcodecs;
import org.bytedeco.opencv.global.opencv_imgproc;
import org.bytedeco.opencv.opencv_core.Mat;
import org.bytedeco.opencv.opencv_core.MatVector;
import org.bytedeco.opencv.opencv_core.Size;

import java.io.IOException;
import java.nio.file.*;
import java.util.stream.Stream;

import static org.bytedeco.opencv.global.opencv_imgcodecs.IMREAD_UNCHANGED;

public class OpenCvImageProcessor implements ImageProcessor {

	@Override
	public void processImages(Path abilitiesRoot, int[] sizes) throws IOException {
		if (!Files.exists(abilitiesRoot))
			throw new IOException("Abilities folder not found: " + abilitiesRoot);

		Path maskPath = abilitiesRoot.resolve("mask.png");
		if (!Files.exists(maskPath))
			throw new IOException("mask.png not found in: " + abilitiesRoot);

		try (Stream<Path> files = Files.list(abilitiesRoot)) {
			files.filter(Files::isRegularFile)
					.filter(p -> p.toString().toLowerCase().matches(".*\\.(png|jpg|jpeg)$"))
					.forEach(file -> {
						if (!file.getFileName().toString().equalsIgnoreCase("mask.png"))
							processSingleImage(file, maskPath, abilitiesRoot, sizes);
					});
		}
	}

	private void processSingleImage(Path imgPath, Path maskPath, Path root, int[] sizes) {
		Mat original = opencv_imgcodecs.imread(imgPath.toString(), IMREAD_UNCHANGED);
		Mat mask = opencv_imgcodecs.imread(maskPath.toString(), IMREAD_UNCHANGED);

		for (int s : sizes) {
			try {
				Path dir = root.resolve(String.valueOf(s));
				Files.createDirectories(dir);
				Mat resized = new Mat();
				opencv_imgproc.resize(original, resized, new Size(s, s), 0, 0, opencv_imgproc.INTER_CUBIC);

				// Resize mask with nearest neighbor
				Mat maskResized = new Mat();
				opencv_imgproc.resize(mask, maskResized, new Size(s, s), 0, 0, opencv_imgproc.INTER_NEAREST);

				Mat finalImg = applyMask(resized, maskResized);
				Path outPath = dir.resolve(imgPath.getFileName().toString());
				opencv_imgcodecs.imwrite(outPath.toString(), finalImg);

				resized.close();
				maskResized.close();
				finalImg.close();
			} catch (IOException e) {
				throw new RuntimeException(e);
			}
		}

		original.close();
		mask.close();
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