package com.lansoftprogramming.runeSequence.ui.settings.debug;

import com.lansoftprogramming.runeSequence.application.TemplateCache;
import com.lansoftprogramming.runeSequence.core.detection.DetectionEngine;
import com.lansoftprogramming.runeSequence.core.detection.DetectionResult;
import com.lansoftprogramming.runeSequence.core.detection.IconDetectionGrader;
import com.lansoftprogramming.runeSequence.core.detection.TemplateDetector;
import com.lansoftprogramming.runeSequence.infrastructure.capture.ScreenCapture;
import com.lansoftprogramming.runeSequence.ui.overlay.OverlayRenderer;
import com.lansoftprogramming.runeSequence.ui.theme.UiColorPalette;
import org.bytedeco.opencv.opencv_core.Mat;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.awt.*;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardOpenOption;
import java.time.Duration;
import java.time.Instant;
import java.util.*;
import java.util.List;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicBoolean;

public class IconDetectionDebugService {
	private static final Logger logger = LoggerFactory.getLogger(IconDetectionDebugService.class);
	private static final int DEFAULT_TICK_MS = 250;
	private static final int DEBUG_BORDER_TTL_MS = 5_000;
	private static final int DEFAULT_YELLOW_TOLERANCE_PERCENT = 10;
	private static final int DEFAULT_NOT_FOUND_TOLERANCE_PERCENT = 20;
	private static final int DEFAULT_PARALLEL_DETECTIONS = 5;

	public interface Listener {
		void onProgress(ProgressSnapshot snapshot);

		void onCompleted(RunResult result);
	}

	public record ProgressSnapshot(int totalTemplates,
	                               int greenCount,
	                               int yellowCount,
	                               int redCount,
	                               int notFoundCount,
	                               String currentlyScanning,
	                               List<String> greenTemplates,
	                               List<String> yellowTemplates,
	                               List<String> notFoundTemplates) {
	}

	public record ResultEntry(String templateName,
	                          double requiredThreshold,
	                          double bestConfidence,
	                          double deltaRatio,
	                          Point bestLocation,
	                          Rectangle bestBounds) {
	}

	public record RunResult(int totalTemplates,
	                        int greenCount,
	                        Duration duration,
	                        Path logFile,
	                        List<String> greenTemplates,
	                        List<ResultEntry> yellow,
	                        List<ResultEntry> red,
	                        List<ResultEntry> notFound) {
	}

	public record ManualTestResult(String templateName,
	                               boolean templateKnown,
	                               double requiredThreshold,
	                               double bestConfidence,
	                               boolean found,
	                               Point bestLocation,
	                               Rectangle bestBounds,
	                               String error) {
	}

	private record BestMatch(double confidence, Point location, Rectangle bounds) {
	}

	private final DetectionEngine detectionEngine;
	private final ScreenCapture screenCapture;
	private final TemplateDetector templateDetector;
	private final TemplateCache templateCache;
	private final OverlayRenderer overlayRenderer;
	private final int tickMs;
	private final List<Listener> listeners = new CopyOnWriteArrayList<>();

	private final AtomicBoolean running = new AtomicBoolean(false);
	private final AtomicBoolean stopRequested = new AtomicBoolean(false);
	private volatile ScheduledExecutorService executor;
	private volatile boolean wasDetectionRunning;
	private volatile Instant startedAt;
	private volatile int totalTemplates = 0;
	private final Set<String> greenTemplates = ConcurrentHashMap.newKeySet();
	private final Map<String, BestMatch> bestByTemplate = new ConcurrentHashMap<>();
	private final Map<String, ResultEntry> greenEntriesByTemplate = new ConcurrentHashMap<>();
	private volatile String currentTemplate = "";
	private volatile Deque<String> scanQueue = new ArrayDeque<>();
	private volatile int yellowTolerancePercent = DEFAULT_YELLOW_TOLERANCE_PERCENT;
	private volatile int notFoundTolerancePercent = DEFAULT_NOT_FOUND_TOLERANCE_PERCENT;
	private volatile Path lastLogFile;
	private volatile List<String> templateNames = List.of();
	private final int batchSize = DEFAULT_PARALLEL_DETECTIONS;
	private volatile java.util.concurrent.ExecutorService detectionPool;
	private final Map<String, Double> requiredThresholdByTemplate = new ConcurrentHashMap<>();

	public IconDetectionDebugService(DetectionEngine detectionEngine,
	                                 ScreenCapture screenCapture,
	                                 TemplateDetector templateDetector,
	                                 TemplateCache templateCache,
	                                 OverlayRenderer overlayRenderer,
	                                 int tickMs) {
		this.detectionEngine = detectionEngine;
		this.screenCapture = screenCapture;
		this.templateDetector = templateDetector;
		this.templateCache = templateCache;
		this.overlayRenderer = overlayRenderer;
		this.tickMs = tickMs > 0 ? tickMs : DEFAULT_TICK_MS;
	}

	public void addListener(Listener listener) {
		if (listener != null) {
			listeners.add(listener);
		}
	}

	public void removeListener(Listener listener) {
		if (listener != null) {
			listeners.remove(listener);
		}
	}

	public boolean isRunning() {
		return running.get();
	}

	public Mat captureCurrentFrame() {
		if (screenCapture == null) {
			return new Mat();
		}
		try {
			return screenCapture.captureScreen();
		} catch (Exception e) {
			logger.debug("Failed capturing frame for debug utility", e);
			return new Mat();
		}
	}

	public ManualTestResult runManualTest(String templateName) {
		String name = templateName != null ? templateName.trim() : "";
		if (name.isBlank()) {
			return new ManualTestResult(name, false, 0.0d, 0.0d, false, null, null, "Enter a template name.");
		}
		if (templateDetector == null) {
			return new ManualTestResult(name, false, 0.0d, 0.0d, false, null, null, "Template detector unavailable.");
		}
		if (screenCapture == null) {
			return new ManualTestResult(name, false, 0.0d, 0.0d, false, null, null, "Screen capture unavailable.");
		}
		if (running.get()) {
			return new ManualTestResult(name, false, 0.0d, 0.0d, false, null, null, "Debug scan is running.");
		}

		boolean known = templateCache != null && templateCache.getTemplateNames().contains(name);
		double requiredThreshold;
		try {
			requiredThreshold = templateDetector.resolveRequiredThreshold(name, null);
		} catch (Exception e) {
			logger.debug("Manual test failed resolving required threshold for {}", name, e);
			return new ManualTestResult(name, known, 0.0d, 0.0d, false, null, null,
					"Failed to resolve required threshold: " + e.getMessage());
		}

		Mat screenMat = null;
		try {
			screenMat = screenCapture.captureScreen();
			if (screenMat == null || screenMat.empty()) {
				return new ManualTestResult(name, known, requiredThreshold, 0.0d, false, null, null, "Capture returned no frame.");
			}

			DetectionResult result = templateDetector.detectTemplate(screenMat, name, false, null);
			if (result == null) {
				return new ManualTestResult(name, known, requiredThreshold, 0.0d, false, null, null, "Detection returned no result.");
			}

			Rectangle captureRegion = screenCapture.getRegion();
			Point bestLocation = result.location != null ? new Point(result.location) : null;
			if (bestLocation != null) {
				bestLocation.translate(captureRegion != null ? captureRegion.x : 0, captureRegion != null ? captureRegion.y : 0);
			}
			Rectangle bestBounds = result.boundingBox != null ? new Rectangle(result.boundingBox) : null;
			if (bestBounds != null) {
				bestBounds.translate(captureRegion != null ? captureRegion.x : 0, captureRegion != null ? captureRegion.y : 0);
			}

			return new ManualTestResult(
					name,
					known,
					requiredThreshold,
					result.confidence,
					result.found,
					bestLocation,
					bestBounds,
					null
			);
		} catch (Exception e) {
			logger.debug("Manual template test failed for {}", name, e);
			return new ManualTestResult(name, known, requiredThreshold, 0.0d, false, null, null, "Manual test failed: " + e.getMessage());
		} finally {
			if (screenMat != null) {
				screenMat.close();
			}
		}
	}

	public synchronized void start() {
		start(DEFAULT_YELLOW_TOLERANCE_PERCENT, DEFAULT_NOT_FOUND_TOLERANCE_PERCENT);
	}

	public synchronized void start(int yellowTolerancePercent, int notFoundTolerancePercent) {
		if (!running.compareAndSet(false, true)) {
			return;
		}

		this.yellowTolerancePercent = sanitizeTolerancePercent(yellowTolerancePercent, DEFAULT_YELLOW_TOLERANCE_PERCENT);
		this.notFoundTolerancePercent = sanitizeTolerancePercent(notFoundTolerancePercent, DEFAULT_NOT_FOUND_TOLERANCE_PERCENT);
		if (this.notFoundTolerancePercent < this.yellowTolerancePercent) {
			int tmp = this.yellowTolerancePercent;
			this.yellowTolerancePercent = this.notFoundTolerancePercent;
			this.notFoundTolerancePercent = tmp;
		}

		stopRequested.set(false);
		greenTemplates.clear();
		bestByTemplate.clear();
		greenEntriesByTemplate.clear();
		requiredThresholdByTemplate.clear();
		currentTemplate = "";
		startedAt = Instant.now();

		Set<String> templates = templateCache != null ? templateCache.getTemplateNames() : Set.of();
		ArrayList<String> ordered = new ArrayList<>(templates);
		ordered.sort(Comparator.naturalOrder());
		totalTemplates = ordered.size();
		templateNames = List.copyOf(ordered);
		scanQueue = new ArrayDeque<>(ordered);

		wasDetectionRunning = detectionEngine != null && detectionEngine.isRunning();
		if (detectionEngine != null) {
			detectionEngine.stop();
		} else if (screenCapture != null) {
			try {
				screenCapture.stopCapture();
			} catch (Exception e) {
				logger.debug("Ignoring failure stopping screen capture for debug scan", e);
			}
		}
		if (overlayRenderer != null) {
			overlayRenderer.clearDebugBorder();
		}

		java.util.concurrent.atomic.AtomicInteger threadCounter = new java.util.concurrent.atomic.AtomicInteger(1);
		detectionPool = Executors.newFixedThreadPool(batchSize, r -> {
			Thread t = new Thread(r, "IconDetectionDebugService-detector-" + threadCounter.getAndIncrement());
			t.setDaemon(true);
			return t;
		});

		executor = Executors.newSingleThreadScheduledExecutor(r -> {
			Thread t = new Thread(r, "IconDetectionDebugService");
			t.setDaemon(true);
			return t;
		});
		executor.scheduleAtFixedRate(this::tick, 0, tickMs, TimeUnit.MILLISECONDS);

		notifyProgress();
		logger.info("Icon detection debug started (templates={}, tickMs={}).", totalTemplates, tickMs);
	}

	public synchronized void stop() {
		if (!running.get()) {
			return;
		}
		stopRequested.set(true);
	}

	private void tick() {
		try {
			if (!running.get()) {
				return;
			}

			if (stopRequested.get()) {
				finishAndPersist();
				return;
			}

			String templateName = scanQueue.pollFirst();
			if (templateName == null) {
				finishAndPersist();
				return;
			}

			ArrayList<String> batch = new ArrayList<>(batchSize);
			batch.add(templateName);
			while (batch.size() < batchSize) {
				String next = scanQueue.pollFirst();
				if (next == null) {
					break;
				}
				batch.add(next);
			}

			currentTemplate = batch.size() > 1
					? String.format(Locale.ROOT, "%s (+%d)", batch.getFirst(), batch.size() - 1)
					: batch.getFirst();

			Mat screenMat = null;
			try {
				screenMat = screenCapture != null ? screenCapture.captureScreen() : null;
				if (screenMat == null || screenMat.empty()) {
					for (String name : batch) {
						scanQueue.addLast(name);
					}
					notifyProgress();
					return;
				}

				final Mat frameMat = screenMat;
				final Rectangle captureRegion = screenCapture != null ? screenCapture.getRegion() : new Rectangle();

				java.util.concurrent.ExecutorService pool = detectionPool;
				if (pool == null) {
					for (String name : batch) {
						scanQueue.addLast(name);
					}
					notifyProgress();
					return;
				}

				java.util.concurrent.ConcurrentHashMap<String, Boolean> requeue = new java.util.concurrent.ConcurrentHashMap<>();
				java.util.concurrent.atomic.AtomicReference<OverlayHint> overlayHint = new java.util.concurrent.atomic.AtomicReference<>();

				List<java.util.concurrent.Callable<Void>> tasks = batch.stream().map(name -> (java.util.concurrent.Callable<Void>) () -> {
					try {
						DetectionResult result = templateDetector.detectTemplate(frameMat, name, false, null);
						double requiredThreshold = requiredThresholdByTemplate.computeIfAbsent(name, key -> templateDetector.resolveRequiredThreshold(key, null));

						BestMatch existing = bestByTemplate.get(name);
						BestMatch nextBest = bestOf(existing, result, captureRegion);
						if (nextBest != null) {
							bestByTemplate.put(name, nextBest);
						}

						double bestConfidence = nextBest != null ? nextBest.confidence : 0.0d;
						IconDetectionGrader.Result gradeResult = IconDetectionGrader.grade(
								requiredThreshold,
								bestConfidence,
								yellowTolerancePercent / 100.0d,
								notFoundTolerancePercent / 100.0d
						);

						if (gradeResult.grade() == IconDetectionGrader.Grade.GREEN) {
							greenTemplates.add(name);
							greenEntriesByTemplate.put(name, new ResultEntry(
									name,
									requiredThreshold,
									bestConfidence,
									gradeResult.deltaRatio(),
									nextBest != null && nextBest.location != null ? new Point(nextBest.location) : null,
									nextBest != null && nextBest.bounds != null ? new Rectangle(nextBest.bounds) : null
							));
							bestByTemplate.remove(name);
						} else {
							requeue.put(name, Boolean.TRUE);
						}

						if (name.equals(batch.getFirst())) {
							overlayHint.set(new OverlayHint(nextBest, gradeResult.grade()));
						}
						return null;
					} catch (Exception e) {
						logger.debug("Icon detection debug failed for template {}", name, e);
						requeue.put(name, Boolean.TRUE);
						return null;
					}
				}).toList();

				pool.invokeAll(tasks);

				OverlayHint hint = overlayHint.get();
				if (hint != null) {
					if (hint.grade == IconDetectionGrader.Grade.GREEN) {
						showDebugBorderIfUseful(hint.best, UiColorPalette.TEXT_SUCCESS);
					} else if (hint.grade == IconDetectionGrader.Grade.YELLOW) {
						showDebugBorderIfUseful(hint.best, UiColorPalette.TOAST_WARNING_ACCENT);
					} else if (hint.grade == IconDetectionGrader.Grade.RED) {
						showDebugBorderIfUseful(hint.best, UiColorPalette.TEXT_DANGER);
					}
				}

				for (String name : batch) {
					if (requeue.containsKey(name)) {
						scanQueue.addLast(name);
					}
				}

				notifyProgress();
			} finally {
				if (screenMat != null) {
					screenMat.close();
				}
			}
		} catch (Throwable t) {
			logger.error("Icon detection debug tick failed; stopping.", t);
			stopRequested.set(true);
			finishAndPersist();
		}
	}

	private void showDebugBorderIfUseful(BestMatch match, Color color) {
		if (match == null || match.bounds == null || overlayRenderer == null) {
			return;
		}
		overlayRenderer.showDebugBorder(match.bounds, color, 3, DEBUG_BORDER_TTL_MS);
	}

	private BestMatch bestOf(BestMatch existing, DetectionResult candidate, Rectangle captureRegion) {
		double candidateScore = candidate != null ? candidate.confidence : 0.0d;
		double existingScore = existing != null ? existing.confidence : -1.0d;
		if (candidateScore <= existingScore) {
			return existing;
		}

		Point location = null;
		if (candidate != null && candidate.location != null) {
			location = new Point(candidate.location);
			location.translate(captureRegion != null ? captureRegion.x : 0, captureRegion != null ? captureRegion.y : 0);
		}

		Rectangle bounds = null;
		if (candidate != null && candidate.boundingBox != null) {
			bounds = new Rectangle(candidate.boundingBox);
			bounds.translate(captureRegion != null ? captureRegion.x : 0, captureRegion != null ? captureRegion.y : 0);
		}

		return new BestMatch(candidateScore, location, bounds);
	}

	private void finishAndPersist() {
		if (!running.compareAndSet(true, false)) {
			return;
		}

		ScheduledExecutorService exec = executor;
		executor = null;
		if (exec != null) {
			exec.shutdown();
		}
		java.util.concurrent.ExecutorService pool = detectionPool;
		detectionPool = null;
		if (pool != null) {
			pool.shutdownNow();
		}

		try {
			RunResult result = buildRunResult();
			writeLogFile(result);
			if (overlayRenderer != null) {
				overlayRenderer.clearDebugBorder();
			}
			notifyCompleted(result);
			logger.info("Icon detection debug completed (green={}/{}).", result.greenCount(), result.totalTemplates());
		} finally {
			if (detectionEngine != null && wasDetectionRunning) {
				detectionEngine.start();
			}
		}
	}

	private RunResult buildRunResult() {
		Instant end = Instant.now();
		Duration duration = startedAt != null ? Duration.between(startedAt, end) : Duration.ZERO;

		ArrayList<ResultEntry> yellow = new ArrayList<>();
		ArrayList<ResultEntry> red = new ArrayList<>();
		ArrayList<ResultEntry> notFound = new ArrayList<>();

		List<String> greenList = new ArrayList<>(greenTemplates);
		greenList.sort(Comparator.naturalOrder());

		for (String templateName : templateNames) {
			if (templateName == null || templateName.isBlank() || greenTemplates.contains(templateName)) {
				continue;
			}
			BestMatch best = bestByTemplate.get(templateName);
			double bestConfidence = best != null ? best.confidence : 0.0d;
			double requiredThreshold = templateDetector.resolveRequiredThreshold(templateName, null);
			IconDetectionGrader.Result grade = IconDetectionGrader.grade(
					requiredThreshold,
					bestConfidence,
					yellowTolerancePercent / 100.0d,
					notFoundTolerancePercent / 100.0d
			);

			ResultEntry entry = new ResultEntry(
					templateName,
					requiredThreshold,
					bestConfidence,
					grade.deltaRatio(),
					best != null ? best.location : null,
					best != null ? best.bounds : null
			);

			switch (grade.grade()) {
				case YELLOW -> yellow.add(entry);
				case RED -> red.add(entry);
				case NOT_FOUND -> notFound.add(entry);
				case GREEN -> {
					// This can occur if stop happens between detection and queue bookkeeping; treat as green.
					greenTemplates.add(templateName);
				}
			}
		}

		Comparator<ResultEntry> byWorstDelta = Comparator
				.comparingDouble(ResultEntry::deltaRatio)
				.reversed()
				.thenComparing(ResultEntry::templateName);
		yellow.sort(byWorstDelta);
		red.sort(byWorstDelta);
		notFound.sort(byWorstDelta);

		Path logFile = resolveLogFilePath();
		lastLogFile = logFile;
		return new RunResult(totalTemplates, greenTemplates.size(), duration, logFile, List.copyOf(greenList), yellow, red, notFound);
	}

	private void writeLogFile(RunResult result) {
		if (result == null) {
			return;
		}

		Path logFile = result.logFile();
		if (logFile == null) {
			return;
		}

		Path iconFolder = templateCache != null ? templateCache.getImagePath() : null;
		Rectangle capture = screenCapture != null ? screenCapture.getRegion() : null;

		StringBuilder sb = new StringBuilder();
		sb.append("RuneSequence Icon Detection Debug\n");
		sb.append("Started: ").append(startedAt != null ? startedAt : "<unknown>").append("\n");
		sb.append("Duration: ").append(result.duration()).append("\n");
		sb.append("Capture Region: ").append(capture != null ? capture : "<unknown>").append("\n");
		sb.append("Icon Folder: ").append(iconFolder != null ? iconFolder : "<unknown>").append("\n");
		sb.append("Yellow tolerance: ").append(yellowTolerancePercent).append("%\n");
		sb.append("Not found cutoff: ").append(notFoundTolerancePercent).append("%\n");
		sb.append("\n");
		sb.append(String.format(Locale.ROOT, "Total: %d\n", result.totalTemplates()));
		sb.append(String.format(Locale.ROOT, "Green: %d\n", result.greenCount()));
		sb.append(String.format(Locale.ROOT, "Yellow: %d\n", result.yellow().size()));
		sb.append(String.format(Locale.ROOT, "Red: %d\n", result.red().size()));
		sb.append(String.format(Locale.ROOT, "Not Found: %d\n", result.notFound().size()));
		sb.append("\n");

		appendSection(sb, String.format(Locale.ROOT, "Yellow (within %d%% of threshold)", yellowTolerancePercent), result.yellow());
		appendSection(sb, String.format(Locale.ROOT, "Red (%d%%–%d%% below threshold)", yellowTolerancePercent, notFoundTolerancePercent), result.red());
		appendSection(sb, String.format(Locale.ROOT, "Not Found (%d%%+ below threshold)", notFoundTolerancePercent), result.notFound());
		appendSection(sb, "Green (Found)", buildGreenEntriesForLog(result));

		try {
			Files.createDirectories(logFile.getParent());
			Files.writeString(
					logFile,
					sb.toString(),
					StandardCharsets.UTF_8,
					StandardOpenOption.CREATE,
					StandardOpenOption.TRUNCATE_EXISTING,
					StandardOpenOption.WRITE
			);
		} catch (IOException e) {
			logger.warn("Failed writing icon detection log {}", logFile, e);
		}
	}

	private void appendSection(StringBuilder sb, String title, List<ResultEntry> entries) {
		sb.append(title).append("\n");
		sb.append("-".repeat(Math.max(0, title.length()))).append("\n");
		if (entries == null || entries.isEmpty()) {
			sb.append("<none>\n\n");
			return;
		}
		for (ResultEntry entry : entries) {
			double thresholdPct = entry.requiredThreshold() * 100.0d;
			double bestPct = entry.bestConfidence() * 100.0d;
			double deltaPct = entry.deltaRatio() * 100.0d;
			sb.append(String.format(Locale.ROOT,
					"%s required=%.2f%% best=%.2f%% delta=%.2f%% at=%s bounds=%s%n",
					entry.templateName(),
					thresholdPct,
					bestPct,
					deltaPct,
					formatPoint(entry.bestLocation()),
					formatBounds(entry.bestBounds())
			));
		}
		sb.append("\n");
	}

	private List<ResultEntry> buildGreenEntriesForLog(RunResult result) {
		if (result == null || result.greenTemplates() == null || result.greenTemplates().isEmpty()) {
			return List.of();
		}

		ArrayList<ResultEntry> entries = new ArrayList<>();
		for (String templateName : result.greenTemplates()) {
			if (templateName == null || templateName.isBlank()) {
				continue;
			}

			ResultEntry fromScan = greenEntriesByTemplate.get(templateName);
			if (fromScan != null) {
				entries.add(fromScan);
				continue;
			}

			BestMatch best = bestByTemplate.get(templateName);
			double bestConfidence = best != null ? best.confidence : 0.0d;
			double requiredThreshold = requiredThresholdByTemplate.computeIfAbsent(templateName, key -> templateDetector.resolveRequiredThreshold(key, null));
			IconDetectionGrader.Result grade = IconDetectionGrader.grade(
					requiredThreshold,
					bestConfidence,
					yellowTolerancePercent / 100.0d,
					notFoundTolerancePercent / 100.0d
			);
			entries.add(new ResultEntry(
					templateName,
					requiredThreshold,
					bestConfidence,
					grade.deltaRatio(),
					best != null && best.location != null ? new Point(best.location) : null,
					best != null && best.bounds != null ? new Rectangle(best.bounds) : null
			));
		}

		entries.sort(Comparator.comparing(ResultEntry::templateName));
		return List.copyOf(entries);
	}

	private String formatPoint(Point point) {
		if (point == null) {
			return "<unknown>";
		}
		return String.format(Locale.ROOT, "(%d,%d)", point.x, point.y);
	}

	private String formatBounds(Rectangle bounds) {
		if (bounds == null) {
			return "<unknown>";
		}
		return String.format(Locale.ROOT, "(%d,%d %dx%d)", bounds.x, bounds.y, bounds.width, bounds.height);
	}

	private Path resolveLogFilePath() {
		String logsDir = System.getProperty("runeSequence.log.dir");
		if (logsDir != null && !logsDir.isBlank()) {
			return Paths.get(logsDir).resolve("icon_Detection.log");
		}
		return Paths.get(System.getProperty("user.home", ".")).resolve("RuneSequence").resolve("logs").resolve("icon_Detection.log");
	}

	private void notifyProgress() {
		List<String> greens = buildSortedGreenList();
		CategorySnapshot counts = computeCategorySnapshot();
		ProgressSnapshot snapshot = new ProgressSnapshot(
				totalTemplates,
				greenTemplates.size(),
				counts.yellow,
				counts.red,
				counts.notFound,
				currentTemplate,
				greens,
				counts.yellowTemplates,
				counts.notFoundTemplates
		);
		for (Listener listener : listeners) {
			try {
				listener.onProgress(snapshot);
			} catch (Exception e) {
				logger.debug("Ignoring progress listener failure", e);
			}
		}
	}

	private void notifyCompleted(RunResult result) {
		for (Listener listener : listeners) {
			try {
				listener.onCompleted(result);
			} catch (Exception e) {
				logger.debug("Ignoring completion listener failure", e);
			}
		}
	}

	public Path getLastLogFile() {
		return lastLogFile;
	}

	private int sanitizeTolerancePercent(int value, int fallback) {
		if (value < 0 || value > 99) {
			return fallback;
		}
		return value;
	}

	private CategorySnapshot computeCategorySnapshot() {
		int yellow = 0;
		int red = 0;
		int notFound = 0;
		ArrayList<String> yellowTemplates = new ArrayList<>();
		ArrayList<String> notFoundTemplates = new ArrayList<>();

		for (String templateName : templateNames) {
			if (templateName == null || templateName.isBlank() || greenTemplates.contains(templateName)) {
				continue;
			}
			BestMatch best = bestByTemplate.get(templateName);
			double bestConfidence = best != null ? best.confidence : 0.0d;
			double requiredThreshold = templateDetector.resolveRequiredThreshold(templateName, null);
			IconDetectionGrader.Result grade = IconDetectionGrader.grade(
					requiredThreshold,
					bestConfidence,
					yellowTolerancePercent / 100.0d,
					notFoundTolerancePercent / 100.0d
			);
			switch (grade.grade()) {
				case YELLOW -> {
					yellow++;
					yellowTemplates.add(templateName);
				}
				case RED -> red++;
				case NOT_FOUND -> {
					notFound++;
					notFoundTemplates.add(templateName);
				}
				case GREEN -> {
				}
			}
		}

		yellowTemplates.sort(Comparator.naturalOrder());
		notFoundTemplates.sort(Comparator.naturalOrder());
		return new CategorySnapshot(yellow, red, notFound, List.copyOf(yellowTemplates), List.copyOf(notFoundTemplates));
	}

	private record CategorySnapshot(int yellow, int red, int notFound, List<String> yellowTemplates,
	                                List<String> notFoundTemplates) {
	}

	private List<String> buildSortedGreenList() {
		ArrayList<String> greens = new ArrayList<>(greenTemplates);
		greens.sort(Comparator.naturalOrder());
		return List.copyOf(greens);
	}

	private record OverlayHint(BestMatch best, IconDetectionGrader.Grade grade) {
	}
}
