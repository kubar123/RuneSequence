package com.lansoftprogramming.runeSequence.ui.overlay;

import com.lansoftprogramming.runeSequence.core.detection.DetectionResult;
import com.lansoftprogramming.runeSequence.ui.theme.UiColorPalette;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.imageio.ImageIO;
import javax.swing.*;
import javax.swing.Timer;
import java.awt.*;
import java.awt.event.ComponentAdapter;
import java.awt.event.ComponentEvent;
import java.awt.image.BufferedImage;
import java.io.IOException;
import java.io.InputStream;
import java.util.*;
import java.util.List;
import java.util.concurrent.*;
import java.util.function.BooleanSupplier;
import java.util.function.LongSupplier;

/**
 * OverlayRenderer - Creates click-through overlay windows to draw borders around detected abilities
 */
public class OverlayRenderer {
	private static final Logger logger = LoggerFactory.getLogger(OverlayRenderer.class);
	private static final int BLINK_INTERVAL_MS = 250;
	private static final int ABILITY_INDICATOR_REPAINT_MS = 16;
	private static final long DEFAULT_ABILITY_INDICATOR_LOOP_MS = 600L;
	private static final int ABILITY_INDICATOR_MIN_LOOP_MS = 50;
	private static final int ABILITY_INDICATOR_MAX_LOOP_MS = 10_000;
	private static final String ABILITY_INDICATOR_RESOURCE_PREFIX = "animations/ability_indicator/";
	private static final int ABILITY_INDICATOR_FRAME_COUNT = 24;
	private static final int DEFAULT_DEBUG_BORDER_HIDE_MS = 5_000;

	// Border types and colors - like piano key highlighting
	public enum BorderType {
		CURRENT_GREEN(UiColorPalette.OVERLAY_CURRENT_AND, 4),        // Current abilities - thick bright green
		NEXT_RED(UiColorPalette.OVERLAY_NEXT_AND, 3),                // Next abilities - thin red
		CURRENT_OR_PURPLE(UiColorPalette.OVERLAY_CURRENT_OR, 4),     // Current OR - thick purple
		NEXT_OR_DARK_PURPLE(UiColorPalette.OVERLAY_NEXT_OR, 3);      // Next OR - thin dark purple

		public final Color color;
		public final int thickness;

		BorderType(Color color, int thickness) {
			this.color = color;
			this.thickness = thickness;
		}
	}

	private final BooleanSupplier blinkCurrentEnabled;
	private final BooleanSupplier abilityIndicatorEnabledSupplier;
	private final LongSupplier abilityIndicatorLoopDurationSupplier;
	private final boolean headless;
	private final Timer blinkTimer;
	private final Timer abilityIndicatorTimer;
	private final JWindow overlayWindow;
	private final OverlayPanel overlayPanel;
	private final ConcurrentMap<String, OverlayBorder> activeBorders;
	private final ConcurrentMap<String, AbilityIndicatorInstance> activeAbilityIndicators;
	private volatile DebugBorder activeDebugBorder;
	private final Timer debugBorderHideTimer;
	private final List<BufferedImage> abilityIndicatorFrames;
	private final ThreadPoolExecutor renderExecutor;
	private long overlayUpdateSeq = 0L;
	private long overlayRepaintSeq = 0L;
	private volatile boolean overlayVisible = false;
	private volatile boolean blinkVisible = true;
	private volatile boolean abilityIndicatorEnabled = true;
	private volatile long abilityIndicatorLoopDurationMs = DEFAULT_ABILITY_INDICATOR_LOOP_MS;
	private Set<String> lastCurrentFoundKeys = new HashSet<>();
	private Set<String> lastNextFoundKeys = new HashSet<>();

	public OverlayRenderer() {
		this(() -> false);
	}

	public OverlayRenderer(BooleanSupplier blinkCurrentEnabled) {
		this(blinkCurrentEnabled, () -> true, () -> DEFAULT_ABILITY_INDICATOR_LOOP_MS);
	}

	public OverlayRenderer(BooleanSupplier blinkCurrentEnabled,
	                       BooleanSupplier abilityIndicatorEnabledSupplier,
	                       LongSupplier abilityIndicatorLoopDurationSupplier) {
		this.blinkCurrentEnabled = blinkCurrentEnabled != null ? blinkCurrentEnabled : () -> false;
		this.abilityIndicatorEnabledSupplier = abilityIndicatorEnabledSupplier != null ? abilityIndicatorEnabledSupplier : () -> true;
		this.abilityIndicatorLoopDurationSupplier = abilityIndicatorLoopDurationSupplier != null
				? abilityIndicatorLoopDurationSupplier
				: () -> DEFAULT_ABILITY_INDICATOR_LOOP_MS;
		this.activeBorders = new ConcurrentHashMap<>();
		this.activeAbilityIndicators = new ConcurrentHashMap<>();
		this.headless = GraphicsEnvironment.isHeadless();
		if (headless) {
			this.overlayWindow = null;
			this.overlayPanel = null;
			this.blinkTimer = null;
			this.abilityIndicatorTimer = null;
			this.debugBorderHideTimer = null;
			this.abilityIndicatorFrames = List.of();
			this.renderExecutor = null;
			logger.info("OverlayRenderer initialized in headless mode");
			return;
		}

		this.overlayWindow = createOverlayWindow();
		this.overlayPanel = new OverlayPanel();
		this.blinkTimer = createBlinkTimer();
		this.abilityIndicatorFrames = loadAbilityIndicatorFrames();
		this.abilityIndicatorTimer = createAbilityIndicatorTimer();
		this.debugBorderHideTimer = createDebugBorderHideTimer();
		this.renderExecutor = createRenderExecutor();

		overlayWindow.add(overlayPanel);
		setupWindowProperties();
		blinkTimer.start();

			logger.info("OverlayRenderer initialized");
		}

	private JWindow createOverlayWindow() {
		JWindow window = new JWindow();

		// Make window click-through and always on top
		window.setAlwaysOnTop(true);
		window.setFocusableWindowState(false);
		window.setAutoRequestFocus(false);

		// Set transparent background
		window.setBackground(UiColorPalette.TRANSPARENT);

		return window;
	}

	private Timer createBlinkTimer() {
		Timer timer = new Timer(BLINK_INTERVAL_MS, e -> handleBlinkTick());
		timer.setRepeats(true);
		return timer;
	}

	private Timer createDebugBorderHideTimer() {
		Timer timer = new Timer(DEFAULT_DEBUG_BORDER_HIDE_MS, e -> enqueueRenderTask(this::clearDebugBorderInternal));
		timer.setRepeats(false);
		return timer;
	}

	private Timer createAbilityIndicatorTimer() {
		Timer timer = new Timer(ABILITY_INDICATOR_REPAINT_MS, e -> handleAbilityIndicatorTick());
		timer.setRepeats(true);
		return timer;
	}

	private ThreadPoolExecutor createRenderExecutor() {
		ThreadFactory factory = runnable -> {
			Thread thread = new Thread(runnable, "overlay-renderer");
			thread.setDaemon(true);
			return thread;
		};

		// Single worker with a tiny queue; drop oldest pending repaint to avoid backlog
		ThreadPoolExecutor executor = new ThreadPoolExecutor(
				1, 1,
				0L, TimeUnit.MILLISECONDS,
				new ArrayBlockingQueue<>(1),
				factory,
				new ThreadPoolExecutor.DiscardOldestPolicy()
		);
		executor.prestartAllCoreThreads();
		return executor;
	}

	private void setupWindowProperties() {
		// Cover entire screen initially
		GraphicsEnvironment ge = GraphicsEnvironment.getLocalGraphicsEnvironment();
		GraphicsDevice gd = ge.getDefaultScreenDevice();
		Rectangle screenBounds = gd.getDefaultConfiguration().getBounds();

		overlayWindow.setBounds(screenBounds);

		// Handle screen changes
		overlayWindow.addComponentListener(new ComponentAdapter() {
			@Override
			public void componentResized(ComponentEvent e) {
				overlayPanel.repaint();
			}
		});

		logger.debug("Overlay window bounds: {}", screenBounds);
	}

	/**
	 * Update overlays with current and next abilities
	 * Called from DetectionEngine.updateOverlays()
	 * Borders persist until next update or clearOverlays() call
	 */
	public void updateOverlays(List<DetectionResult> currentAbilities, List<DetectionResult> nextAbilities) {
		if (headless) {
			return;
		}
		List<DetectionResult> currentSnapshot = safeCopy(currentAbilities);
		List<DetectionResult> nextSnapshot = safeCopy(nextAbilities);
		enqueueRenderTask(() -> processOverlayUpdate(currentSnapshot, nextSnapshot));
	}

	private List<DetectionResult> safeCopy(List<DetectionResult> source) {
		if (source == null || source.isEmpty()) {
			return Collections.emptyList();
		}
		return new ArrayList<>(source);
	}

	private void enqueueRenderTask(Runnable task) {
		if (renderExecutor == null) {
			return;
		}
		try {
			renderExecutor.execute(task);
		} catch (RejectedExecutionException ex) {
			if (!renderExecutor.isShutdown()) {
				logger.warn("Render executor rejected task", ex);
			}
		}
	}

	private void processOverlayUpdate(List<DetectionResult> currentAbilities, List<DetectionResult> nextAbilities) {
		long updateSeq = ++overlayUpdateSeq;
		long startNanos = System.nanoTime();

		try {
			refreshAbilityIndicatorSettings();
			Set<String> desiredKeys = new HashSet<>();
			Set<String> currentFoundKeys = new HashSet<>();
			Set<String> nextFoundKeys = new HashSet<>();
			Map<String, DetectionResult> currentFoundByKey = new HashMap<>();
			boolean bordersChanged = false;
			boolean currentBordersChanged = false;

			if (currentAbilities != null) {
				for (DetectionResult result : currentAbilities) {
					BorderType borderType = determineCurrentBorderType(currentAbilities, result);
					if (upsertBorder(result, borderType)) {
						bordersChanged = true;
						currentBordersChanged = true;
					}
					if (result != null && result.found) {
						desiredKeys.add(result.templateName);
						currentFoundKeys.add(result.templateName);
						currentFoundByKey.put(result.templateName, result);
					}
				}
			}

			if (nextAbilities != null) {
				for (DetectionResult result : nextAbilities) {
					BorderType borderType = determineNextBorderType(nextAbilities, result);
					if (upsertBorder(result, borderType)) {
						bordersChanged = true;
					}
					if (result != null && result.found) {
						desiredKeys.add(result.templateName);
						nextFoundKeys.add(result.templateName);
					}
				}
			}

			boolean indicatorsChanged = startAbilityIndicatorsForPromotions(currentFoundKeys, currentFoundByKey);

			RemovalChange removalChange = removeStaleBorders(desiredKeys);
			bordersChanged = bordersChanged || removalChange.anyRemoved;
			currentBordersChanged = currentBordersChanged || removalChange.currentRemoved;
			indicatorsChanged = removeStaleAbilityIndicators(desiredKeys) || indicatorsChanged;

			if (currentBordersChanged) {
				resetBlinkState();
			}

			setOverlayVisible(hasAnyOverlays());

			if (logger.isDebugEnabled()) {
				long elapsedMicros = (System.nanoTime() - startNanos) / 1_000;
				logger.debug("OverlayRenderer.processOverlayUpdate #{} changed={} activeBorders={} ({}µs)",
						updateSeq, bordersChanged, activeBorders.size(), elapsedMicros);
			}

			lastCurrentFoundKeys = currentFoundKeys;
			lastNextFoundKeys = nextFoundKeys;

			if (bordersChanged || indicatorsChanged) {
				long repaintSeq = ++overlayRepaintSeq;
				if (logger.isDebugEnabled()) {
					logger.debug("OverlayRenderer posting repaint #{} for update #{}", repaintSeq, updateSeq);
				}
				SwingUtilities.invokeLater(overlayPanel::repaint);
			}

		} catch (Exception e) {
			logger.error("Error updating overlays", e);
		}
	}

	private BorderType determineCurrentBorderType(List<DetectionResult> currentAbilities, DetectionResult result) {
		// If multiple current abilities, it's an OR group - use purple
		//Check if result is alternative or not
		return result.isAlternative ? BorderType.CURRENT_OR_PURPLE : BorderType.CURRENT_GREEN;
	}

	private BorderType determineNextBorderType(List<DetectionResult> nextAbilities, DetectionResult result) {
		// If multiple next abilities, it's an OR group - use dark purple
		return nextAbilities.size() > 1 ? BorderType.NEXT_OR_DARK_PURPLE : BorderType.NEXT_RED;
	}

	private boolean upsertBorder(DetectionResult result, BorderType borderType) {
		if (result == null || !result.found) {
			return false;
		}

		if (result.boundingBox == null) {
			logger.warn("OverlayRenderer: found=true but boundingBox=null for {}", result.templateName);
			return false;
		}

		Rectangle bounds = calculateBorderBounds(result);
		OverlayBorder existing = activeBorders.get(result.templateName);
		if (existing != null && existing.borderType == borderType && existing.bounds.equals(bounds)) {
			return false;
		}

		OverlayBorder border = new OverlayBorder(result.templateName, bounds, borderType);
		activeBorders.put(result.templateName, border);
		return true;
	}

	private boolean startAbilityIndicatorsForPromotions(Set<String> currentFoundKeys, Map<String, DetectionResult> currentFoundByKey) {
		if (!abilityIndicatorEnabled || abilityIndicatorFrames.isEmpty() || currentFoundKeys.isEmpty() || lastNextFoundKeys.isEmpty()) {
			return false;
		}

		boolean anyStarted = false;
		long now = System.nanoTime();
		for (String key : currentFoundKeys) {
			if (!lastNextFoundKeys.contains(key) || lastCurrentFoundKeys.contains(key)) {
				continue;
			}
			DetectionResult result = currentFoundByKey.get(key);
			if (result == null || result.boundingBox == null) {
				continue;
			}
			activeAbilityIndicators.put(key, new AbilityIndicatorInstance(new Rectangle(result.boundingBox), now));
			anyStarted = true;
		}

		if (anyStarted) {
			SwingUtilities.invokeLater(() -> {
				if (abilityIndicatorTimer != null && !abilityIndicatorTimer.isRunning()) {
					abilityIndicatorTimer.start();
				}
			});
		}
		return anyStarted;
	}

	private boolean removeStaleAbilityIndicators(Set<String> desiredKeys) {
		if (activeAbilityIndicators.isEmpty()) {
			return false;
		}
		boolean removed = false;
		for (String key : new HashSet<>(activeAbilityIndicators.keySet())) {
			if (!desiredKeys.contains(key)) {
				activeAbilityIndicators.remove(key);
				removed = true;
			}
		}
		return removed;
	}

	private Rectangle calculateBorderBounds(DetectionResult result) {
		// Expand bounds slightly beyond the detection area for better visibility
		int padding = 3;

		// Use boundingBox from DetectionResult (should always exist for found results)
		if (result.boundingBox != null) {
			return new Rectangle(
					result.boundingBox.x - padding,
					result.boundingBox.y - padding,
					result.boundingBox.width + (padding * 2),
					result.boundingBox.height + (padding * 2)
			);
		} else {
			// Fallback: shouldn't happen with proper DetectionResult.found() usage
			logger.warn("DetectionResult missing boundingBox for {}", result.templateName);
			int defaultSize = 24; // Slightly larger default
			return new Rectangle(
					result.location.x - padding,
					result.location.y - padding,
					defaultSize + (padding * 2),
					defaultSize + (padding * 2)
			);
		}
	}

	/**
	 * Clear all overlay borders immediately
	 * Called from DetectionEngine.stop() or when manually clearing
	 */
	public void clearOverlays() {
		if (headless) {
			activeBorders.clear();
			activeAbilityIndicators.clear();
			return;
		}
		enqueueRenderTask(this::clearOverlaysInternal);
	}

	private void clearOverlaysInternal() {
		activeBorders.clear();
		activeAbilityIndicators.clear();
		blinkVisible = true;
		setOverlayVisible(hasAnyOverlays());
		SwingUtilities.invokeLater(overlayPanel::repaint);
		logger.debug("Cleared all overlays");
	}

	public void showDebugBorder(Rectangle bounds, Color color, int thickness, int durationMs) {
		if (headless || bounds == null || bounds.isEmpty()) {
			return;
		}
		Rectangle normalized = new Rectangle(bounds);
		enqueueRenderTask(() -> showDebugBorderInternal(normalized, color, thickness, durationMs));
	}

	public void clearDebugBorder() {
		if (headless) {
			activeDebugBorder = null;
			return;
		}
		enqueueRenderTask(this::clearDebugBorderInternal);
	}

	private void showDebugBorderInternal(Rectangle bounds, Color color, int thickness, int durationMs) {
		Color resolvedColor = color != null ? color : UiColorPalette.OVERLAY_NEXT_AND;
		int resolvedThickness = Math.max(1, thickness);
		activeDebugBorder = new DebugBorder(bounds, resolvedColor, resolvedThickness);
		setOverlayVisible(true);
		SwingUtilities.invokeLater(() -> {
			if (debugBorderHideTimer != null) {
				debugBorderHideTimer.setInitialDelay(durationMs > 0 ? durationMs : DEFAULT_DEBUG_BORDER_HIDE_MS);
				debugBorderHideTimer.restart();
			}
			overlayPanel.repaint();
		});
	}

	private void clearDebugBorderInternal() {
		activeDebugBorder = null;
		setOverlayVisible(hasAnyOverlays());
		SwingUtilities.invokeLater(overlayPanel::repaint);
	}

	private boolean hasAnyOverlays() {
		return !activeBorders.isEmpty() || !activeAbilityIndicators.isEmpty() || activeDebugBorder != null;
	}

	private RemovalChange removeStaleBorders(Set<String> desiredKeys) {
		boolean anyRemoved = false;
		boolean currentRemoved = false;

		for (String key : new HashSet<>(activeBorders.keySet())) {
			if (!desiredKeys.contains(key)) {
				OverlayBorder removed = activeBorders.remove(key);
				if (removed != null) {
					anyRemoved = true;
					if (isCurrentBorder(removed.borderType)) {
						currentRemoved = true;
					}
				}
			}
		}

		return new RemovalChange(anyRemoved, currentRemoved);
	}

	private void setOverlayVisible(boolean visible) {
		if (overlayWindow == null) {
			return;
		}
		if (overlayVisible != visible) {
			overlayVisible = visible;
			SwingUtilities.invokeLater(() -> {
				overlayWindow.setVisible(visible);
				if (visible) {
					ClickThroughWindowSupport.enable(overlayWindow);
				}
			});

			logger.debug("Overlay visibility: {}", visible);
		}
	}

	/**
	 * Shutdown the overlay renderer and clean up resources
	 */
	public void shutdown() {
		if (headless) {
			activeBorders.clear();
			activeAbilityIndicators.clear();
			return;
		}
		clearOverlays();
		renderExecutor.shutdown();
		try {
			renderExecutor.awaitTermination(300, TimeUnit.MILLISECONDS);
		} catch (InterruptedException e) {
			Thread.currentThread().interrupt();
		}
		blinkTimer.stop();
		if (abilityIndicatorTimer != null) {
			abilityIndicatorTimer.stop();
		}
		SwingUtilities.invokeLater(() -> {
			overlayWindow.setVisible(false);
			overlayWindow.dispose();
		});
		logger.info("OverlayRenderer shutdown");
	}

	private void handleBlinkTick() {
		if (!blinkCurrentEnabled.getAsBoolean()) {
			if (!blinkVisible) {
				blinkVisible = true;
				SwingUtilities.invokeLater(overlayPanel::repaint);
			}
			return;
		}

		if (!overlayVisible || !hasCurrentBorders()) {
			if (!blinkVisible) {
				blinkVisible = true;
				SwingUtilities.invokeLater(overlayPanel::repaint);
			}
			return;
		}

		blinkVisible = !blinkVisible;
		SwingUtilities.invokeLater(overlayPanel::repaint);
	}

	private void handleAbilityIndicatorTick() {
		refreshAbilityIndicatorSettings();
		if (!abilityIndicatorEnabled || !overlayVisible || abilityIndicatorFrames.isEmpty()) {
			if (abilityIndicatorTimer != null && abilityIndicatorTimer.isRunning()) {
				abilityIndicatorTimer.stop();
			}
			activeAbilityIndicators.clear();
			return;
		}

		if (activeAbilityIndicators.isEmpty()) {
			if (abilityIndicatorTimer != null && abilityIndicatorTimer.isRunning()) {
				abilityIndicatorTimer.stop();
			}
			return;
		}

		long loopMs = abilityIndicatorLoopDurationMs;
		long now = System.nanoTime();
		boolean anyExpired = false;
		for (Map.Entry<String, AbilityIndicatorInstance> entry : new HashSet<>(activeAbilityIndicators.entrySet())) {
			AbilityIndicatorInstance indicator = entry.getValue();
			long elapsedMs = (now - indicator.startedAtNanos) / 1_000_000L;
			if (elapsedMs >= loopMs) {
				activeAbilityIndicators.remove(entry.getKey());
				anyExpired = true;
			}
		}

		if (activeAbilityIndicators.isEmpty()) {
			if (abilityIndicatorTimer != null) {
				abilityIndicatorTimer.stop();
			}
		}

		if (overlayPanel != null && (!activeAbilityIndicators.isEmpty() || anyExpired)) {
			overlayPanel.repaint();
		}
	}

	private boolean hasCurrentBorders() {
		for (OverlayBorder border : activeBorders.values()) {
			if (isCurrentBorder(border.borderType)) {
				return true;
			}
		}
		return false;
	}

	private void resetBlinkState() {
		if (blinkCurrentEnabled.getAsBoolean() && hasCurrentBorders()) {
			blinkVisible = true;
		}
	}

	public void setAbilityIndicatorLoopDurationMs(long loopDurationMs) {
		long sanitized = loopDurationMs;
		if (sanitized < ABILITY_INDICATOR_MIN_LOOP_MS) {
			sanitized = ABILITY_INDICATOR_MIN_LOOP_MS;
		} else if (sanitized > ABILITY_INDICATOR_MAX_LOOP_MS) {
			sanitized = ABILITY_INDICATOR_MAX_LOOP_MS;
		}
		abilityIndicatorLoopDurationMs = sanitized;
	}

	public void setAbilityIndicatorEnabled(boolean enabled) {
		abilityIndicatorEnabled = enabled;
		if (!enabled) {
			activeAbilityIndicators.clear();
			if (abilityIndicatorTimer != null && abilityIndicatorTimer.isRunning()) {
				abilityIndicatorTimer.stop();
			}
			if (overlayPanel != null) {
				SwingUtilities.invokeLater(overlayPanel::repaint);
			}
		}
	}

	private void refreshAbilityIndicatorSettings() {
		boolean enabled = abilityIndicatorEnabledSupplier.getAsBoolean();
		if (enabled != abilityIndicatorEnabled) {
			setAbilityIndicatorEnabled(enabled);
		}
		setAbilityIndicatorLoopDurationMs(abilityIndicatorLoopDurationSupplier.getAsLong());
	}

	/**
	 * Inner class representing a single border overlay
	 */
	private static class OverlayBorder {
		final String templateName;
		final Rectangle bounds;
		final BorderType borderType;

		OverlayBorder(String templateName, Rectangle bounds, BorderType borderType) {
			this.templateName = templateName;
			this.bounds = bounds;
			this.borderType = borderType;
		}
	}

	private static class RemovalChange {
		final boolean anyRemoved;
		final boolean currentRemoved;

		RemovalChange(boolean anyRemoved, boolean currentRemoved) {
			this.anyRemoved = anyRemoved;
			this.currentRemoved = currentRemoved;
		}
	}

	private static class AbilityIndicatorInstance {
		final Rectangle bounds;
		final long startedAtNanos;

		private AbilityIndicatorInstance(Rectangle bounds, long startedAtNanos) {
			this.bounds = bounds;
			this.startedAtNanos = startedAtNanos;
		}
	}

	private static class DebugBorder {
		final Rectangle bounds;
		final Color color;
		final int thickness;

		DebugBorder(Rectangle bounds, Color color, int thickness) {
			this.bounds = bounds;
			this.color = color;
			this.thickness = thickness;
		}
	}

	/**
	 * Custom JPanel for rendering overlay borders
	 */
	private class OverlayPanel extends JPanel {

		public OverlayPanel() {
			setOpaque(false); // Transparent background
			setBackground(UiColorPalette.TRANSPARENT);
		}

		@Override
		protected void paintComponent(Graphics g) {
			super.paintComponent(g);

			Graphics2D g2d = (Graphics2D) g.create();
			try {
				// Enable antialiasing for smoother borders
				g2d.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);

				drawAbilityIndicators(g2d);
				drawDebugBorder(g2d);

				// Draw all active borders
				for (OverlayBorder border : activeBorders.values()) {
					drawBorder(g2d, border);
				}

			} finally {
				g2d.dispose();
			}
		}

		private void drawBorder(Graphics2D g2d, OverlayBorder border) {
			if (shouldSkipBorder(border)) {
				return;
			}
			g2d.setColor(border.borderType.color);
			g2d.setStroke(new BasicStroke(border.borderType.thickness,
					BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));

			// Draw border rectangle
			g2d.drawRect(border.bounds.x, border.bounds.y,
					border.bounds.width, border.bounds.height);
		}

		private boolean shouldSkipBorder(OverlayBorder border) {
			if (!blinkCurrentEnabled.getAsBoolean()) {
				return false;
			}
			return !blinkVisible && isCurrentBorder(border.borderType);
		}
	}

	private void drawDebugBorder(Graphics2D g2d) {
		DebugBorder border = activeDebugBorder;
		if (border == null) {
			return;
		}
		g2d.setColor(border.color);
		g2d.setStroke(new BasicStroke(border.thickness, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
		g2d.drawRect(border.bounds.x, border.bounds.y, border.bounds.width, border.bounds.height);
	}

	private void drawAbilityIndicators(Graphics2D g2d) {
		if (!abilityIndicatorEnabled || abilityIndicatorFrames.isEmpty() || activeAbilityIndicators.isEmpty()) {
			return;
		}

		Object oldInterpolation = g2d.getRenderingHint(RenderingHints.KEY_INTERPOLATION);
		g2d.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BILINEAR);

		try {
			long now = System.nanoTime();
			long loopMs = abilityIndicatorLoopDurationMs;
			int frameCount = abilityIndicatorFrames.size();

			for (AbilityIndicatorInstance indicator : activeAbilityIndicators.values()) {
				long elapsedMs = (now - indicator.startedAtNanos) / 1_000_000L;
				if (elapsedMs < 0 || elapsedMs >= loopMs) {
					continue;
				}
				double progress = elapsedMs / (double) loopMs;
				int frameIndex = (int) Math.floor(progress * frameCount);
				if (frameIndex < 0) {
					frameIndex = 0;
				} else if (frameIndex >= frameCount) {
					frameIndex = frameCount - 1;
				}

				BufferedImage frame = abilityIndicatorFrames.get(frameIndex);
				Rectangle bounds = indicator.bounds;
				g2d.drawImage(frame, bounds.x, bounds.y, bounds.width, bounds.height, null);
			}
		} finally {
			if (oldInterpolation != null) {
				g2d.setRenderingHint(RenderingHints.KEY_INTERPOLATION, oldInterpolation);
			}
		}
	}

	private List<BufferedImage> loadAbilityIndicatorFrames() {
		List<BufferedImage> frames = new ArrayList<>(ABILITY_INDICATOR_FRAME_COUNT);
		ClassLoader loader = OverlayRenderer.class.getClassLoader();

		for (int i = 1; i <= ABILITY_INDICATOR_FRAME_COUNT; i++) {
			String resourcePath = ABILITY_INDICATOR_RESOURCE_PREFIX + i + ".png";
			try (InputStream stream = loader.getResourceAsStream(resourcePath)) {
				if (stream == null) {
					logger.warn("Ability indicator frame not found on classpath: {}", resourcePath);
					continue;
				}
				BufferedImage image = ImageIO.read(stream);
				if (image == null) {
					logger.warn("Ability indicator frame unreadable: {}", resourcePath);
					continue;
				}
				frames.add(image);
			} catch (IOException e) {
				logger.warn("Failed loading ability indicator frame: {}", resourcePath, e);
			}
		}

		if (frames.isEmpty()) {
			logger.warn("No ability indicator frames loaded; promotion animation disabled.");
			return List.of();
		}
		if (frames.size() != ABILITY_INDICATOR_FRAME_COUNT) {
			logger.warn("Loaded {} / {} ability indicator frames; animation may appear choppy.", frames.size(), ABILITY_INDICATOR_FRAME_COUNT);
		}
		return List.copyOf(frames);
	}

	private boolean isCurrentBorder(BorderType borderType) {
		return borderType == BorderType.CURRENT_GREEN || borderType == BorderType.CURRENT_OR_PURPLE;
	}
}
