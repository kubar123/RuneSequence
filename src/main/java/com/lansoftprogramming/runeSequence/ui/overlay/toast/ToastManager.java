package com.lansoftprogramming.runeSequence.ui.overlay.toast;

import org.slf4j.Logger;

import javax.swing.*;
import javax.swing.Timer;
import java.awt.*;
import java.awt.event.ComponentAdapter;
import java.awt.event.ComponentEvent;
import java.awt.event.WindowAdapter;
import java.awt.event.WindowEvent;
import java.util.*;
import java.util.List;

/**
 * High-level API for showing stacked toast notifications anchored to a frame's layered pane.
 */
public class ToastManager implements ToastClient {
	private static final int MAX_VISIBLE = 3;
	private static final int RIGHT_MARGIN = 28;
	private static final int BOTTOM_MARGIN = 36;
	private static final int MIN_WIDTH = 320;
	private static final int MAX_WIDTH = 420;
	private static final int VERTICAL_GAP = 12;
	private static final int ENTRY_SLIDE = 22;
	private static final int ANIMATION_INTERVAL = 15;
	private static final int ANIMATION_DURATION = 180;

	private final Window owner;
	private final JLayeredPane layeredPane;
	private final JPanel overlay;
	private final Deque<ToastRequest> pendingQueue;
	private final List<ToastHandle> activeToasts;

	public ToastManager(JFrame owner) {
		this(owner, owner.getLayeredPane());
	}

	public ToastManager(JDialog owner) {
		this(owner, owner.getLayeredPane());
	}

	public ToastManager(JWindow owner) {
		this(owner, owner.getLayeredPane());
	}

	public ToastManager(Window owner, JLayeredPane layeredPane) {
		this.owner = Objects.requireNonNull(owner, "owner");
		this.layeredPane = Objects.requireNonNull(layeredPane, "layeredPane");
		this.pendingQueue = new ArrayDeque<>();
		this.activeToasts = new ArrayList<>();
		this.overlay = createOverlay();
		installOverlay();
	}

	public static ToastClient loggingFallback(Logger logger) {
		return new LoggingToastClient(logger);
	}

	@Override
	public void success(String message) {
		show(ToastType.SUCCESS, message, null);
	}

	@Override
	public void success(String message, String hiddenMessage) {
		show(ToastType.SUCCESS, message, hiddenMessage);
	}

	@Override
	public void info(String message) {
		show(ToastType.INFO, message, null);
	}

	@Override
	public void info(String message, String hiddenMessage) {
		show(ToastType.INFO, message, hiddenMessage);
	}

	@Override
	public void warn(String message) {
		show(ToastType.WARNING, message, null);
	}

	@Override
	public void warn(String message, String hiddenMessage) {
		show(ToastType.WARNING, message, hiddenMessage);
	}

	@Override
	public void error(String message) {
		show(ToastType.ERROR, message, null);
	}

	@Override
	public void error(String message, String hiddenMessage) {
		show(ToastType.ERROR, message, hiddenMessage);
	}

	public void show(ToastType type, String message, String hiddenMessage) {
		Objects.requireNonNull(type, "type");
		Objects.requireNonNull(message, "message");
		Runnable task = () -> {
			if (!owner.isDisplayable()) {
				pendingQueue.clear();
				return;
			}
			pendingQueue.addLast(new ToastRequest(type, message, hiddenMessage));
			processQueue();
		};

		if (SwingUtilities.isEventDispatchThread()) {
			task.run();
		} else {
			SwingUtilities.invokeLater(task);
		}
	}

	@Override
	public void clearAll() {
		Runnable task = () -> {
			for (ToastHandle handle : new ArrayList<>(activeToasts)) {
				handle.dismiss(false);
			}
			pendingQueue.clear();
		};

		if (SwingUtilities.isEventDispatchThread()) {
			task.run();
		} else {
			SwingUtilities.invokeLater(task);
		}
	}

	private JPanel createOverlay() {
		JPanel panel = new JPanel(null);
		panel.setOpaque(false);
		panel.setFocusable(false);
		panel.setRequestFocusEnabled(false);
		return panel;
	}

	private void installOverlay() {
		overlay.setBounds(0, 0, layeredPane.getWidth(), layeredPane.getHeight());
		layeredPane.add(overlay, JLayeredPane.POPUP_LAYER);
		layeredPane.addComponentListener(new ComponentAdapter() {
			@Override
			public void componentResized(ComponentEvent e) {
				overlay.setBounds(0, 0, layeredPane.getWidth(), layeredPane.getHeight());
				layoutToasts(false);
			}
		});

		owner.addWindowListener(new WindowAdapter() {
			@Override
			public void windowClosed(WindowEvent e) {
				clearAll();
				layeredPane.remove(overlay);
			}
		});
	}

	private void processQueue() {
		assertEventThread();
		while (activeToasts.size() < MAX_VISIBLE && !pendingQueue.isEmpty()) {
			ToastRequest request = pendingQueue.pollFirst();
			if (request == null) {
				continue;
			}
			ToastHandle handle = new ToastHandle(request);
			activeToasts.add(0, handle);
			overlay.add(handle.panel);
			overlay.setComponentZOrder(handle.panel, 0);
		}
		layoutToasts(true);
	}

	private void layoutToasts(boolean animateExisting) {
		assertEventThread();
		if (overlay.getWidth() == 0 || overlay.getHeight() == 0) {
			return;
		}

		int totalHeight = 0;
		for (ToastHandle handle : activeToasts) {
			totalHeight += handle.getPreferredHeight();
		}
		if (!activeToasts.isEmpty()) {
			totalHeight += VERTICAL_GAP * (activeToasts.size() - 1);
		}

		int availableHeight = overlay.getHeight();
		int startY = Math.max(16, availableHeight - BOTTOM_MARGIN - totalHeight);
		int anchorX = overlay.getWidth() - RIGHT_MARGIN;

		int currentY = startY;
		for (ToastHandle handle : activeToasts) {
			Dimension preferred = handle.panel.getPreferredSize();
			int width = clamp(preferred.width, MIN_WIDTH, MAX_WIDTH);
			int height = handle.getPreferredHeight();
			int x = anchorX - width;
			handle.updateTarget(new Rectangle(x, currentY, width, height), animateExisting);
			currentY += height + VERTICAL_GAP;
		}
		overlay.revalidate();
		overlay.repaint();
	}

	private int clamp(int value, int min, int max) {
		return Math.max(min, Math.min(value, max));
	}

	private void retireToast(ToastHandle handle) {
		activeToasts.remove(handle);
		overlay.remove(handle.panel);
		overlay.revalidate();
		overlay.repaint();
		processQueue();
	}

	private void assertEventThread() {
		if (!SwingUtilities.isEventDispatchThread()) {
			throw new IllegalStateException("ToastManager operations must run on the EDT");
		}
	}

	private static final class ToastRequest {
		private final ToastType type;
		private final String message;
		private final String hiddenMessage;

		private ToastRequest(ToastType type, String message, String hiddenMessage) {
			this.type = type;
			this.message = message;
			this.hiddenMessage = hiddenMessage;
		}
	}

	private final class ToastHandle {
		private final ToastPanel panel;
		private final ToastType type;
		private final long displayMillis;
		private Timer fadeTimer;
		private Timer moveTimer;
		private Timer lifeTimer;
		private boolean entering;
		private boolean closing;

		private ToastHandle(ToastRequest request) {
			this.type = request.type;
			this.panel = new ToastPanel(request.type, request.message, request.hiddenMessage);
			this.panel.setDismissRequest(() -> dismiss(true));
			this.displayMillis = request.type.getDisplayMillis();
			this.entering = true;
			this.closing = false;
			Dimension preferred = panel.getPreferredSize();
			panel.setSize(preferred);
			panel.setOpacity(0f);
		}

		private int getPreferredHeight() {
			return panel.getPreferredSize().height;
		}

		private void updateTarget(Rectangle target, boolean animateExisting) {
			panel.setSize(target.width, target.height);

			if (entering) {
				Point entryPoint = new Point(target.x, target.y + ENTRY_SLIDE);
				panel.setLocation(entryPoint);
				animateOpacity(panel.getOpacity(), 1f, () -> startLifetime());
				animateMove(panel.getLocation(), target.getLocation(), true);
				entering = false;
			} else if (animateExisting && !closing) {
				animateMove(panel.getLocation(), target.getLocation(), false);
			} else {
				panel.setLocation(target.getLocation());
			}
		}

		private void startLifetime() {
			cancelTimer(lifeTimer);
			lifeTimer = new Timer((int) displayMillis, e -> dismiss(true));
			lifeTimer.setRepeats(false);
			lifeTimer.start();
		}

		private void dismiss(boolean animated) {
			if (closing) {
				return;
			}
			closing = true;
			cancelTimer(lifeTimer);
			if (animated) {
				animateOpacity(panel.getOpacity(), 0f, this::retire);
				animateMove(panel.getLocation(), new Point(panel.getX(), panel.getY() + ENTRY_SLIDE), false);
			} else {
				retire();
			}
		}

		private void retire() {
			cancelTimer(fadeTimer);
			cancelTimer(moveTimer);
			closing = true;
			retireToast(this);
		}

		private void animateOpacity(float from, float to, Runnable onComplete) {
			cancelTimer(fadeTimer);
			int steps = Math.max(1, ANIMATION_DURATION / ANIMATION_INTERVAL);
			float delta = (to - from) / steps;
			fadeTimer = new Timer(ANIMATION_INTERVAL, null);
			fadeTimer.addActionListener(e -> {
				float next = panel.getOpacity() + delta;
				boolean done = (delta >= 0 && next >= to) || (delta < 0 && next <= to);
				if (done) {
					panel.setOpacity(to);
					fadeTimer.stop();
					if (onComplete != null) {
						onComplete.run();
					}
				} else {
					panel.setOpacity(next);
				}
			});
			fadeTimer.start();
		}

		private void animateMove(Point from, Point to, boolean ensureComplete) {
			cancelTimer(moveTimer);
			int steps = Math.max(1, ANIMATION_DURATION / ANIMATION_INTERVAL);
			double deltaX = (to.getX() - from.getX()) / steps;
			double deltaY = (to.getY() - from.getY()) / steps;
			moveTimer = new Timer(ANIMATION_INTERVAL, null);
			moveTimer.addActionListener(e -> {
				Point current = panel.getLocation();
				double nextX = current.getX() + deltaX;
				double nextY = current.getY() + deltaY;
				boolean doneX = (deltaX >= 0 && nextX >= to.getX()) || (deltaX < 0 && nextX <= to.getX());
				boolean doneY = (deltaY >= 0 && nextY >= to.getY()) || (deltaY < 0 && nextY <= to.getY());
				if (doneX && doneY) {
					panel.setLocation(to);
					moveTimer.stop();
					if (ensureComplete && !closing && panel.getOpacity() < 1f) {
						panel.setOpacity(1f);
					}
				} else {
					panel.setLocation((int) Math.round(nextX), (int) Math.round(nextY));
				}
			});
			moveTimer.start();
		}

		private void cancelTimer(Timer timer) {
			if (timer != null) {
				timer.stop();
			}
		}
	}

	private static final class LoggingToastClient implements ToastClient {
		private final Logger logger;

		private LoggingToastClient(Logger logger) {
			this.logger = Objects.requireNonNull(logger, "logger");
		}

		@Override
		public void success(String message) {
			logger.info("SUCCESS: {}", message);
		}

		@Override
		public void success(String message, String hiddenMessage) {
			logger.info("SUCCESS: {} - {}", message, hiddenMessage);
		}

		@Override
		public void info(String message) {
			logger.info("INFO: {}", message);
		}

		@Override
		public void info(String message, String hiddenMessage) {
			logger.info("INFO: {} - {}", message, hiddenMessage);
		}

		@Override
		public void warn(String message) {
			logger.warn("WARN: {}", message);
		}

		@Override
		public void warn(String message, String hiddenMessage) {
			logger.warn("WARN: {} - {}", message, hiddenMessage);
		}

		@Override
		public void error(String message) {
			logger.error("ERROR: {}", message);
		}

		@Override
		public void error(String message, String hiddenMessage) {
			logger.error("ERROR: {} - {}", message, hiddenMessage);
		}
	}
}
