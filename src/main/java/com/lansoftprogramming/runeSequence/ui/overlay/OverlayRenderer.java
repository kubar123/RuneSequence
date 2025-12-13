package com.lansoftprogramming.runeSequence.ui.overlay;

import com.lansoftprogramming.runeSequence.core.detection.DetectionResult;
import com.lansoftprogramming.runeSequence.ui.theme.UiColorPalette;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.swing.*;
import javax.swing.Timer;
import java.awt.*;
import java.awt.event.ComponentAdapter;
import java.awt.event.ComponentEvent;
import java.util.*;
import java.util.List;
import java.util.concurrent.*;
import java.util.function.BooleanSupplier;

/**
 * OverlayRenderer - Creates click-through overlay windows to draw borders around detected abilities
 */
public class OverlayRenderer {
	private static final Logger logger = LoggerFactory.getLogger(OverlayRenderer.class);
	private static final int BLINK_INTERVAL_MS = 450;

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
	private final boolean headless;
	private final Timer blinkTimer;
	private final JWindow overlayWindow;
	private final OverlayPanel overlayPanel;
	private final ConcurrentMap<String, OverlayBorder> activeBorders;
	private final ThreadPoolExecutor renderExecutor;
	private long overlayUpdateSeq = 0L;
	private long overlayRepaintSeq = 0L;
	private volatile boolean overlayVisible = false;
	private volatile boolean blinkVisible = true;

	public OverlayRenderer() {
		this(() -> false);
	}

	public OverlayRenderer(BooleanSupplier blinkCurrentEnabled) {
		this.blinkCurrentEnabled = blinkCurrentEnabled != null ? blinkCurrentEnabled : () -> false;
		this.activeBorders = new ConcurrentHashMap<>();
		this.headless = GraphicsEnvironment.isHeadless();
		if (headless) {
			this.overlayWindow = null;
			this.overlayPanel = null;
			this.blinkTimer = null;
			this.renderExecutor = null;
			logger.info("OverlayRenderer initialized in headless mode");
			return;
		}

		this.overlayWindow = createOverlayWindow();
		this.overlayPanel = new OverlayPanel();
		this.blinkTimer = createBlinkTimer();
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
			Set<String> desiredKeys = new HashSet<>();
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
					}
				}
			}

			RemovalChange removalChange = removeStaleBorders(desiredKeys);
			bordersChanged = bordersChanged || removalChange.anyRemoved;
			currentBordersChanged = currentBordersChanged || removalChange.currentRemoved;

			if (currentBordersChanged) {
				resetBlinkState();
			}

			boolean shouldShow = !activeBorders.isEmpty();
			setOverlayVisible(shouldShow);

			if (logger.isDebugEnabled()) {
				long elapsedMicros = (System.nanoTime() - startNanos) / 1_000;
				logger.debug("OverlayRenderer.processOverlayUpdate #{} changed={} activeBorders={} ({}µs)",
						updateSeq, bordersChanged, activeBorders.size(), elapsedMicros);
			}

			if (bordersChanged) {
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
			System.err.println("OverlayRenderer: ERROR - found=true but boundingBox=null for " + result.templateName);
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
			return;
		}
		enqueueRenderTask(this::clearOverlaysInternal);
	}

	private void clearOverlaysInternal() {
		activeBorders.clear();
		blinkVisible = true;
		setOverlayVisible(false);
		SwingUtilities.invokeLater(overlayPanel::repaint);
		logger.debug("Cleared all overlays");
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
			SwingUtilities.invokeLater(() -> overlayWindow.setVisible(visible));

			logger.debug("Overlay visibility: {}", visible);
		}
	}

	/**
	 * Shutdown the overlay renderer and clean up resources
	 */
	public void shutdown() {
		if (headless) {
			activeBorders.clear();
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

	private boolean isCurrentBorder(BorderType borderType) {
		return borderType == BorderType.CURRENT_GREEN || borderType == BorderType.CURRENT_OR_PURPLE;
	}
}
