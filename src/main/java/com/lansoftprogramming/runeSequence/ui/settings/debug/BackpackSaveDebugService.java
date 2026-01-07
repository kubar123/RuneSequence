package com.lansoftprogramming.runeSequence.ui.settings.debug;

import org.bytedeco.javacpp.indexer.UByteIndexer;
import org.bytedeco.opencv.global.opencv_core;
import org.bytedeco.opencv.global.opencv_imgcodecs;
import org.bytedeco.opencv.global.opencv_imgproc;
import org.bytedeco.opencv.opencv_core.Mat;
import org.bytedeco.opencv.opencv_core.Point;
import org.bytedeco.opencv.opencv_core.Rect;
import org.bytedeco.opencv.opencv_core.Scalar;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.awt.*;
import java.awt.datatransfer.DataFlavor;
import java.awt.datatransfer.Transferable;
import java.awt.image.BufferedImage;
import java.awt.image.DataBufferByte;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.function.Supplier;

public final class BackpackSaveDebugService {
	private static final Logger logger = LoggerFactory.getLogger(BackpackSaveDebugService.class);
	private static final String[] BACKGROUND_TEMPLATE_RESOURCES = new String[]{
			"/tools/backpack_slow_white.png",
			"/tools/backpack_slot_white.png"
	};
	private static final double HOLE_FILL_INNER_TOLERANCE_RATIO = 0.70d;
	private static final int HOLE_FILL_MIN_PIXELS = 3;

	private static final int EXPECTED_WIDTH = 2560;
	private static final int EXPECTED_HEIGHT = 1377;

	private static final int CROP_WIDTH = 38;
	private static final int CROP_HEIGHT = 34;
	private static final int GRID_COLS = 4;
	private static final int GRID_ROWS = 7;
	private static final int GRID_DX = CROP_WIDTH + 11;
	private static final int GRID_DY = CROP_HEIGHT + 2;
	private static final int GRID_ORIGIN_X = 2150;
	private static final int GRID_ORIGIN_Y = 963;

	// Crop-local, 0-based. "3rd pixel from right" => width-3 (index 35 for width=38)
	// "4th pixel from bottom" => height-4 (index 30 for height=34)
	private static final int SEED_X = CROP_WIDTH - 3;
	private static final int SEED_Y = CROP_HEIGHT - 4;

	private static final DateTimeFormatter RUN_FOLDER_FORMAT = DateTimeFormatter.ofPattern("yyyyMMdd_HHmmss", Locale.ROOT);

	private record CropSpec(String key, int x, int y) {
	}

	public record SaveResult(Path runDir, String warning) {
	}

	private BackpackSaveDebugService() {
	}

	public static SaveResult save(Path debugRootDir,
	                              Supplier<Mat> appCaptureSupplier,
	                              double tolerancePercent) throws IOException {
		Objects.requireNonNull(debugRootDir, "debugRootDir");
		Files.createDirectories(debugRootDir);

		LocalDateTime now = LocalDateTime.now();
		String baseName = "backpack_" + RUN_FOLDER_FORMAT.format(now);
		Path runDir = createUniqueRunDir(debugRootDir, baseName);

		String source = "unknown";
		int width = 0;
		int height = 0;
		String warning = null;

		StringBuilder info = new StringBuilder();
		info.append("timestamp=").append(Instant.now()).append('\n');

		Mat sourceMat = null;
		Mat sourceBgr = null;
		try {
			BufferedImage clipboard = readClipboardImage();
			if (clipboard != null) {
				sourceMat = bufferedImageToMatBgr(clipboard);
				source = "clipboard";
			} else if (appCaptureSupplier != null) {
				Mat captured = appCaptureSupplier.get();
				if (captured != null && !captured.empty()) {
					sourceMat = captured;
					source = "app";
				}
			}

			if (sourceMat == null || sourceMat.empty()) {
				throw new IOException("Clipboard had no image and no in-app source available.");
			}

			info.append("sourceChannels=").append(sourceMat.channels()).append('\n');
			sourceBgr = normalizeToBgr(sourceMat, source);
			width = sourceBgr.cols();
			height = sourceBgr.rows();

			info.append("source=").append(source).append('\n');
			info.append("dimensions=").append(width).append('x').append(height).append('\n');
			info.append("expectedDimensions=").append(EXPECTED_WIDTH).append('x').append(EXPECTED_HEIGHT).append('\n');
			info.append("cropSize=").append(CROP_WIDTH).append('x').append(CROP_HEIGHT).append('\n');
			info.append("seedPoint=").append('(').append(SEED_X).append(',').append(SEED_Y).append(')').append('\n');
			info.append("seedPointNote=crop-local 0-based coords; seedX=(width-3), seedY=(height-4)").append('\n');

		int toleranceChannel = percentToByteTolerance(tolerancePercent);
		info.append("tolerancePercent=").append(String.format(Locale.ROOT, "%.3f", tolerancePercent)).append('\n');
		info.append("tolerancePerChannel=").append(toleranceChannel).append(" (0-255)").append('\n');
		info.append("connectivity=8").append('\n');
		info.append("holeFillInnerToleranceRatio=").append(String.format(Locale.ROOT, "%.3f", HOLE_FILL_INNER_TOLERANCE_RATIO)).append('\n');
		info.append("holeFillMinPixels=").append(HOLE_FILL_MIN_PIXELS).append('\n');
		info.append("backgroundSeedPoints=(0,0),(w-1,0),(0,h-1),(w-1,h-1),(").append(SEED_X).append(',').append(SEED_Y).append(')').append('\n');
		info.append("backgroundTemplates=").append(String.join(",", BACKGROUND_TEMPLATE_RESOURCES)).append('\n');
		info.append("gridOrigin=").append('(').append(GRID_ORIGIN_X).append(',').append(GRID_ORIGIN_Y).append(')').append('\n');
		info.append("gridCols=").append(GRID_COLS).append('\n');
			info.append("gridRows=").append(GRID_ROWS).append('\n');
			info.append("gridStep=").append('(').append(GRID_DX).append(',').append(GRID_DY).append(')').append('\n');
			info.append("cropRectanglesBasedOn=").append(EXPECTED_WIDTH).append('x').append(EXPECTED_HEIGHT).append('\n');
			info.append("cropRectangles=").append('\n');
			List<CropSpec> crops = buildGridCrops();
			for (CropSpec crop : crops) {
				info.append("  ")
						.append(crop.key)
						.append(": x=").append(crop.x)
						.append(" y=").append(crop.y)
						.append(" w=").append(CROP_WIDTH)
						.append(" h=").append(CROP_HEIGHT)
						.append('\n');
			}

			Path fullPath = runDir.resolve("full.png");
			boolean wroteFull = opencv_imgcodecs.imwrite(fullPath.toString(), sourceBgr);
			if (!wroteFull) {
				throw new IOException("Failed to write full.png.");
			}

			if (width != EXPECTED_WIDTH || height != EXPECTED_HEIGHT) {
				warning = String.format(
						Locale.ROOT,
						"WARNING: Source image size mismatch (expected %dx%d, got %dx%d). Saved full.png; icon extraction skipped.",
						EXPECTED_WIDTH, EXPECTED_HEIGHT, width, height
				);
				info.append("warning=").append(warning).append('\n');
				writeInfo(runDir, info);
				return new SaveResult(runDir, warning);
			}

			LoadedTemplate backgroundTemplateBgr = loadBackgroundTemplateBgr();
			try {
				if (backgroundTemplateBgr != null && backgroundTemplateBgr.mat != null && !backgroundTemplateBgr.mat.empty()) {
					info.append("backgroundRemoval=templateDiffFloodFill").append('\n');
					info.append("backgroundTemplateLoaded=").append(backgroundTemplateBgr.resource).append('\n');
					info.append("backgroundTemplateSize=").append(backgroundTemplateBgr.mat.cols()).append('x').append(backgroundTemplateBgr.mat.rows()).append('\n');
				} else {
					info.append("backgroundRemoval=seedColorFloodFill(fallback)").append('\n');
				}
				info.append("cropToSelectionAfterBackgroundRemoval=true").append('\n');

				for (CropSpec cropSpec : crops) {
					Rect roi = new Rect(cropSpec.x, cropSpec.y, CROP_WIDTH, CROP_HEIGHT);
					if (!rectWithin(sourceBgr, roi)) {
						throw new IOException(String.format(
								Locale.ROOT,
								"Crop out of bounds for %s: (%d,%d %dx%d) within (%dx%d)",
								cropSpec.key, roi.x(), roi.y(), roi.width(), roi.height(), width, height
						));
					}

					Mat crop = new Mat(sourceBgr, roi).clone();
					try {
						Path rawPath = runDir.resolve(cropSpec.key + "_raw.png");
						if (!opencv_imgcodecs.imwrite(rawPath.toString(), crop)) {
							throw new IOException("Failed to write " + rawPath.getFileName());
						}

						Mat transparent = backgroundTemplateBgr != null && backgroundTemplateBgr.mat != null && !backgroundTemplateBgr.mat.empty()
								? removeBackgroundUsingTemplate(crop, backgroundTemplateBgr.mat, toleranceChannel)
								: removeBackgroundMagicWand(crop, toleranceChannel);
						Mat croppedToSelection = cropToSelection(transparent);
						try {
							Path outPath = runDir.resolve(cropSpec.key + ".png");
							if (!opencv_imgcodecs.imwrite(outPath.toString(), croppedToSelection)) {
								throw new IOException("Failed to write " + outPath.getFileName());
							}
						} finally {
							if (croppedToSelection != transparent) {
								croppedToSelection.close();
							}
							transparent.close();
						}
					} finally {
						crop.close();
					}
				}
			} finally {
				if (backgroundTemplateBgr != null && backgroundTemplateBgr.mat != null) {
					backgroundTemplateBgr.mat.close();
				}
			}

			writeInfo(runDir, info);
			return new SaveResult(runDir, null);
		} catch (IOException e) {
			info.append("error=").append(e.getMessage() != null ? e.getMessage() : e).append('\n');
			try {
				writeInfo(runDir, info);
			} catch (Exception writeErr) {
				logger.warn("Failed writing backpack debug info.txt for {}", runDir, writeErr);
			}
			throw e;
		} catch (Exception e) {
			String message = e.getMessage() != null ? e.getMessage() : e.toString();
			info.append("error=").append(message).append('\n');
			try {
				writeInfo(runDir, info);
			} catch (Exception writeErr) {
				logger.warn("Failed writing backpack debug info.txt for {}", runDir, writeErr);
			}
			throw new IOException(message, e);
		} finally {
			if (sourceBgr != null) {
				sourceBgr.close();
			}
			if (sourceMat != null) {
				sourceMat.close();
			}
		}
	}

	private static void writeInfo(Path runDir, StringBuilder info) throws IOException {
		Path infoPath = runDir.resolve("info.txt");
		Files.writeString(infoPath, info.toString(), StandardCharsets.UTF_8);
	}

	private static Path createUniqueRunDir(Path debugRootDir, String baseName) throws IOException {
		Path candidate = debugRootDir.resolve(baseName);
		if (!Files.exists(candidate)) {
			Files.createDirectories(candidate);
			return candidate;
		}

		for (int i = 1; i <= 9999; i++) {
			Path next = debugRootDir.resolve(baseName + "_" + i);
			if (!Files.exists(next)) {
				Files.createDirectories(next);
				return next;
			}
		}
		throw new IOException("Failed to create unique debug run folder under: " + debugRootDir);
	}

	private static int percentToByteTolerance(double percent) {
		double clamped = Math.max(0.0d, Math.min(100.0d, percent));
		return (int) Math.round((clamped / 100.0d) * 255.0d);
	}

	private static Mat cropToSelection(Mat cropBgra) {
		Objects.requireNonNull(cropBgra, "cropBgra");
		if (cropBgra.empty() || cropBgra.channels() != 4) {
			return cropBgra;
		}

		Mat alpha = new Mat();
		Mat nonZero = new Mat();
		try {
			opencv_core.extractChannel(cropBgra, alpha, 3);
			opencv_core.findNonZero(alpha, nonZero);
			if (nonZero.empty()) {
				return cropBgra;
			}

			Rect rect = opencv_imgproc.boundingRect(nonZero);
			if (rect.width() <= 0 || rect.height() <= 0) {
				return cropBgra;
			}
			if (rect.x() == 0 && rect.y() == 0 && rect.width() == cropBgra.cols() && rect.height() == cropBgra.rows()) {
				return cropBgra;
			}

			return new Mat(cropBgra, rect).clone();
		} finally {
			nonZero.close();
			alpha.close();
		}
	}

	private static List<CropSpec> buildGridCrops() {
		java.util.ArrayList<CropSpec> crops = new java.util.ArrayList<>(GRID_COLS * GRID_ROWS);
		for (int row = 0; row < GRID_ROWS; row++) {
			for (int col = 0; col < GRID_COLS; col++) {
				int index = (row * GRID_COLS) + col + 1;
				int x = GRID_ORIGIN_X + (col * GRID_DX);
				int y = GRID_ORIGIN_Y + (row * GRID_DY);
				crops.add(new CropSpec("item" + index, x, y));
			}
		}
		return crops;
	}

	private static Mat normalizeToBgr(Mat src, String source) throws IOException {
		if (src == null || src.empty()) {
			throw new IOException("Source image is empty.");
		}

		Mat out = new Mat();
		try {
			if (src.channels() == 3) {
				out = src.clone();
			} else if (src.channels() == 4) {
				// Screen capture Mats from FFmpeg are typically BGRA with an unused/zero alpha channel.
				opencv_imgproc.cvtColor(src, out, opencv_imgproc.COLOR_BGRA2BGR);
			} else if (src.channels() == 1) {
				opencv_imgproc.cvtColor(src, out, opencv_imgproc.COLOR_GRAY2BGR);
			} else {
				throw new IOException("Unsupported source image channels: " + src.channels() + " (source=" + source + ")");
			}
			return out;
		} catch (IOException e) {
			out.close();
			throw e;
		} catch (Exception e) {
			out.close();
			throw new IOException("Failed normalizing source image: " + (e.getMessage() != null ? e.getMessage() : e), e);
		}
	}

	private static boolean rectWithin(Mat src, Rect roi) {
		if (src == null || src.empty() || roi == null) {
			return false;
		}
		return roi.x() >= 0
				&& roi.y() >= 0
				&& roi.width() > 0
				&& roi.height() > 0
				&& roi.x() + roi.width() <= src.cols()
				&& roi.y() + roi.height() <= src.rows();
	}

	private record LoadedTemplate(String resource, Mat mat) {
	}

	private static LoadedTemplate loadBackgroundTemplateBgr() {
		for (String resource : BACKGROUND_TEMPLATE_RESOURCES) {
			if (resource == null || resource.isBlank()) {
				continue;
			}
			try (var stream = BackpackSaveDebugService.class.getResourceAsStream(resource)) {
				if (stream == null) {
					continue;
				}
				BufferedImage image = javax.imageio.ImageIO.read(stream);
				if (image == null) {
					continue;
				}
				return new LoadedTemplate(resource, bufferedImageToMatBgr(image));
			} catch (Exception e) {
				logger.debug("Failed to load backpack background template {}", resource, e);
			}
		}
		return null;
	}

	private static Mat removeBackgroundUsingTemplate(Mat cropBgr, Mat templateBgr, int tolerancePerChannel) throws IOException {
		if (cropBgr == null || cropBgr.empty()) {
			throw new IOException("Empty crop.");
		}
		if (cropBgr.channels() != 3) {
			throw new IOException("Expected BGR crop (3 channels), got: " + cropBgr.channels());
		}
		if (templateBgr == null || templateBgr.empty()) {
			throw new IOException("Background template unavailable.");
		}

		Mat template = templateBgr;
		Mat resizedTemplate = null;
		try {
			if (template.cols() != cropBgr.cols() || template.rows() != cropBgr.rows() || template.channels() != 3) {
				Mat tmpBgr = new Mat();
				if (template.channels() == 4) {
					opencv_imgproc.cvtColor(template, tmpBgr, opencv_imgproc.COLOR_BGRA2BGR);
				} else if (template.channels() == 1) {
					opencv_imgproc.cvtColor(template, tmpBgr, opencv_imgproc.COLOR_GRAY2BGR);
				} else if (template.channels() == 3) {
					tmpBgr = template.clone();
				} else {
					throw new IOException("Unsupported background template channels: " + template.channels());
				}

				resizedTemplate = new Mat();
				opencv_imgproc.resize(tmpBgr, resizedTemplate, new org.bytedeco.opencv.opencv_core.Size(cropBgr.cols(), cropBgr.rows()), 0, 0, opencv_imgproc.INTER_AREA);
				tmpBgr.close();
				template = resizedTemplate;
			}

			int w = cropBgr.cols();
			int h = cropBgr.rows();
			int pixelCount = w * h;
			byte[] background = new byte[pixelCount]; // 1 = transparent
			byte[] candidate = new byte[pixelCount];  // 1 = matches template within outer tolerance
			int[] diffByPixel = new int[pixelCount];  // 0..255
			int innerTol = (int) Math.max(1L, Math.round(tolerancePerChannel * HOLE_FILL_INNER_TOLERANCE_RATIO));

			UByteIndexer cropIdx = cropBgr.createIndexer();
			UByteIndexer tmplIdx = template.createIndexer();
			try {
				for (int y = 0; y < h; y++) {
					for (int x = 0; x < w; x++) {
						int i = y * w + x;
						int cb = cropIdx.get(y, x, 0) & 0xFF;
						int cg = cropIdx.get(y, x, 1) & 0xFF;
						int cr = cropIdx.get(y, x, 2) & 0xFF;

						int tb = tmplIdx.get(y, x, 0) & 0xFF;
						int tg = tmplIdx.get(y, x, 1) & 0xFF;
						int tr = tmplIdx.get(y, x, 2) & 0xFF;

						int d = Math.max(Math.max(Math.abs(cb - tb), Math.abs(cg - tg)), Math.abs(cr - tr));
						diffByPixel[i] = d;
						candidate[i] = (byte) (d <= tolerancePerChannel ? 1 : 0);
					}
				}

				int[] qx = new int[pixelCount];
				int[] qy = new int[pixelCount];
				int head = 0;
				int tail = 0;

				int[][] seeds = new int[][]{
						{0, 0},
						{w - 1, 0},
						{0, h - 1},
						{w - 1, h - 1},
						{SEED_X, SEED_Y}
				};

				for (int[] seed : seeds) {
					if (seed == null || seed.length < 2) {
						continue;
					}
					int sx = seed[0];
					int sy = seed[1];
					if (sx < 0 || sx >= w || sy < 0 || sy >= h) {
						continue;
					}
					int si = sy * w + sx;
					if (candidate[si] == 0) {
						continue;
					}
					if (background[si] != 0) {
						continue;
					}
					background[si] = 1;
					qx[tail] = sx;
					qy[tail] = sy;
					tail++;
				}

				int[] dx = new int[]{-1, 0, 1, -1, 1, -1, 0, 1};
				int[] dy = new int[]{-1, -1, -1, 0, 0, 1, 1, 1};

				while (head < tail) {
					int x = qx[head];
					int y = qy[head];
					head++;

					for (int k = 0; k < 8; k++) {
						int nx = x + dx[k];
						int ny = y + dy[k];
						if (nx < 0 || nx >= w || ny < 0 || ny >= h) {
							continue;
						}
						int nidx = ny * w + nx;
						if (background[nidx] != 0) {
							continue;
						}
						if (candidate[nidx] == 0) {
							continue;
						}
						background[nidx] = 1;
						qx[tail] = nx;
						qy[tail] = ny;
						tail++;
					}
				}

				// Remove enclosed "holes": background-like components not connected to the crop border,
				// but only when their max template-diff is within a stricter inner tolerance.
				byte[] seen = new byte[pixelCount];
				int[] stack = new int[pixelCount];
				int[] component = new int[pixelCount];
				for (int i = 0; i < pixelCount; i++) {
					if (background[i] != 0 || candidate[i] == 0 || seen[i] != 0) {
						continue;
					}

					int sp = 0;
					int size = 0;
					int maxDiff = 0;
					boolean touchesBorder = false;

					stack[sp++] = i;
					seen[i] = 1;
					int compCount = 0;

					while (sp > 0) {
						int cur = stack[--sp];
						component[compCount++] = cur;
						int cy = cur / w;
						int cx = cur - (cy * w);
						int d = diffByPixel[cur];
						if (d > maxDiff) {
							maxDiff = d;
						}
						if (cx == 0 || cy == 0 || cx == w - 1 || cy == h - 1) {
							touchesBorder = true;
						}
						size++;

						for (int k = 0; k < 8; k++) {
							int nx = cx + dx[k];
							int ny = cy + dy[k];
							if (nx < 0 || nx >= w || ny < 0 || ny >= h) {
								continue;
							}
							int ni = ny * w + nx;
							if (seen[ni] != 0 || background[ni] != 0 || candidate[ni] == 0) {
								continue;
							}
							seen[ni] = 1;
							stack[sp++] = ni;
						}
					}

					if (!touchesBorder && size >= HOLE_FILL_MIN_PIXELS && maxDiff <= innerTol) {
						for (int j = 0; j < compCount; j++) {
							background[component[j]] = 1;
						}
					}
				}
			} finally {
				cropIdx.release();
				tmplIdx.release();
			}

			Mat cropBgra = new Mat();
			opencv_imgproc.cvtColor(cropBgr, cropBgra, opencv_imgproc.COLOR_BGR2BGRA);

			UByteIndexer outIdx = cropBgra.createIndexer();
			try {
				for (int y = 0; y < h; y++) {
					for (int x = 0; x < w; x++) {
						int idx = y * w + x;
						outIdx.put(y, x, 3, background[idx] != 0 ? 0 : 255);
					}
				}
			} finally {
				outIdx.release();
			}

			return cropBgra;
		} finally {
			if (resizedTemplate != null) {
				resizedTemplate.close();
			}
		}
	}

	private static Mat removeBackgroundMagicWand(Mat crop, int tolerancePerChannel) throws IOException {
		if (crop == null || crop.empty()) {
			throw new IOException("Empty crop.");
		}
		if (SEED_X < 0 || SEED_X >= crop.cols() || SEED_Y < 0 || SEED_Y >= crop.rows()) {
			throw new IOException(String.format(Locale.ROOT, "Seed point out of bounds: (%d,%d) in (%dx%d).", SEED_X, SEED_Y, crop.cols(), crop.rows()));
		}

		Mat cropBgra = null;
		Mat cropBgr = null;
		Mat mask = null;

		try {
			if (crop.channels() == 4) {
				cropBgra = crop.clone();
				cropBgr = new Mat();
				opencv_imgproc.cvtColor(cropBgra, cropBgr, opencv_imgproc.COLOR_BGRA2BGR);
			} else if (crop.channels() == 3) {
				cropBgra = new Mat();
				opencv_imgproc.cvtColor(crop, cropBgra, opencv_imgproc.COLOR_BGR2BGRA);
				cropBgr = crop.clone();
			} else if (crop.channels() == 1) {
				cropBgr = new Mat();
				opencv_imgproc.cvtColor(crop, cropBgr, opencv_imgproc.COLOR_GRAY2BGR);
				cropBgra = new Mat();
				opencv_imgproc.cvtColor(cropBgr, cropBgra, opencv_imgproc.COLOR_BGR2BGRA);
			} else {
				throw new IOException("Unsupported crop channels: " + crop.channels());
			}

			int w = cropBgr != null ? cropBgr.cols() : 0;
			int h = cropBgr != null ? cropBgr.rows() : 0;
			mask = new Mat(h + 2, w + 2, org.bytedeco.opencv.global.opencv_core.CV_8UC1, new Scalar(0.0));

			int flags = 8
					| (255 << 8)
					| opencv_imgproc.FLOODFILL_MASK_ONLY
					| opencv_imgproc.FLOODFILL_FIXED_RANGE;

			Scalar diff = new Scalar(tolerancePerChannel, tolerancePerChannel, tolerancePerChannel, 0.0);
			opencv_imgproc.floodFill(
					cropBgr,
					mask,
					new Point(SEED_X, SEED_Y),
					new Scalar(0.0, 0.0, 0.0, 0.0),
					null,
					diff,
					diff,
					flags
			);

			UByteIndexer bgraIdx = cropBgra.createIndexer();
			UByteIndexer maskIdx = mask.createIndexer();
			try {
				for (int y = 0; y < h; y++) {
					for (int x = 0; x < w; x++) {
						int m = maskIdx.get(y + 1, x + 1) & 0xFF;
						bgraIdx.put(y, x, 3, m != 0 ? 0 : 255);
					}
				}
			} finally {
				bgraIdx.release();
				maskIdx.release();
			}

			return cropBgra;
		} catch (Exception e) {
			if (cropBgra != null) {
				cropBgra.close();
			}
			if (e instanceof IOException ioe) {
				throw ioe;
			}
			throw new IOException(e.getMessage() != null ? e.getMessage() : e.toString(), e);
		} finally {
			if (cropBgr != null) {
				cropBgr.close();
			}
			if (mask != null) {
				mask.close();
			}
		}
	}

	private static BufferedImage readClipboardImage() {
		try {
			if (GraphicsEnvironment.isHeadless()) {
				return null;
			}
			Transferable contents = Toolkit.getDefaultToolkit().getSystemClipboard().getContents(null);
			if (contents == null || !contents.isDataFlavorSupported(DataFlavor.imageFlavor)) {
				return null;
			}
			Image image = (Image) contents.getTransferData(DataFlavor.imageFlavor);
			if (image == null) {
				return null;
			}
			return toBufferedImage(image);
		} catch (IllegalStateException e) {
			// Clipboard can be temporarily unavailable on Windows.
			logger.debug("Clipboard unavailable while reading image", e);
			return null;
		} catch (Exception e) {
			logger.debug("Failed reading clipboard image", e);
			return null;
		}
	}

	private static BufferedImage toBufferedImage(Image image) {
		if (image instanceof BufferedImage bi) {
			return bi;
		}
		int w = Math.max(1, image.getWidth(null));
		int h = Math.max(1, image.getHeight(null));
		BufferedImage buffered = new BufferedImage(w, h, BufferedImage.TYPE_INT_ARGB);
		Graphics2D g = buffered.createGraphics();
		try {
			g.setComposite(AlphaComposite.Src);
			g.drawImage(image, 0, 0, null);
		} finally {
			g.dispose();
		}
		return buffered;
	}

	private static Mat bufferedImageToMatBgr(BufferedImage image) throws IOException {
		try {
			BufferedImage bgr = new BufferedImage(
					Math.max(1, image.getWidth()),
					Math.max(1, image.getHeight()),
					BufferedImage.TYPE_3BYTE_BGR
			);
			Graphics2D g = bgr.createGraphics();
			try {
				g.setComposite(AlphaComposite.Src);
				g.drawImage(image, 0, 0, null);
			} finally {
				g.dispose();
			}

			byte[] data = ((DataBufferByte) bgr.getRaster().getDataBuffer()).getData();
			Mat mat = new Mat(bgr.getHeight(), bgr.getWidth(), opencv_core.CV_8UC3);
			mat.data().put(data);
			Mat clone = mat.clone();
			mat.close();
			return clone;
		} catch (Exception e) {
			throw new IOException("Failed converting clipboard image to Mat: " + e.getMessage(), e);
		}
	}
}
