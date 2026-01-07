package com.lansoftprogramming.runeSequence.ui.shared.window;

import com.lansoftprogramming.runeSequence.infrastructure.config.AppSettings;
import com.lansoftprogramming.runeSequence.infrastructure.config.ConfigManager;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.swing.*;
import java.awt.*;
import java.awt.event.ComponentAdapter;
import java.awt.event.ComponentEvent;
import java.awt.event.WindowAdapter;
import java.awt.event.WindowEvent;
import java.io.IOException;
import java.lang.ref.WeakReference;
import java.util.Objects;
import java.util.concurrent.CopyOnWriteArrayList;

public final class WindowPlacementSupport {
	private static final Logger logger = LoggerFactory.getLogger(WindowPlacementSupport.class);
	private static final CopyOnWriteArrayList<TrackedWindow> trackedWindows = new CopyOnWriteArrayList<>();

	private WindowPlacementSupport() {
	}

	public enum WindowId {
		PRESET_MANAGER,
		SETTINGS,
		REGION_MANAGER
	}

	public static boolean restore(ConfigManager configManager, WindowId id, JFrame frame) {
		Objects.requireNonNull(id, "id");
		if (configManager == null || frame == null) {
			return false;
		}

		AppSettings.UiSettings.WindowPlacement placement = resolvePlacement(configManager, id);
		if (placement == null || !placement.hasBounds()) {
			return false;
		}

		Rectangle bounds = new Rectangle(
				placement.getX(),
				placement.getY(),
				placement.getWidth(),
				placement.getHeight()
		);

		if (!isOnScreen(bounds)) {
			logger.debug("Skipping restore for {} because bounds were off-screen: {}", id, bounds);
			return false;
		}

		frame.setBounds(bounds);
		if (placement.isMaximized()) {
			frame.setExtendedState(frame.getExtendedState() | Frame.MAXIMIZED_BOTH);
		}
		return true;
	}

	public static void install(ConfigManager configManager, WindowId id, JFrame frame) {
		Objects.requireNonNull(id, "id");
		if (configManager == null || frame == null) {
			return;
		}

		TrackedWindow tracked = new TrackedWindow(configManager, id, frame);
		track(tracked);

		Timer debounce = new Timer(750, e -> persist(configManager, id, frame));
		debounce.setRepeats(false);

		ComponentAdapter componentListener = new ComponentAdapter() {
			@Override
			public void componentMoved(ComponentEvent e) {
				debounce.restart();
			}

			@Override
			public void componentResized(ComponentEvent e) {
				debounce.restart();
			}
		};

		frame.addComponentListener(componentListener);

		WindowAdapter windowListener = new WindowAdapter() {
			@Override
			public void windowClosing(WindowEvent e) {
				persist(configManager, id, frame);
			}

			@Override
			public void windowClosed(WindowEvent e) {
				debounce.stop();
				trackedWindows.remove(tracked);
				frame.removeComponentListener(componentListener);
				frame.removeWindowListener(this);
			}
		};

		frame.addWindowListener(windowListener);
	}

	public static void flushAll() {
		for (TrackedWindow tracked : trackedWindows) {
			JFrame frame = tracked.frameRef.get();
			if (frame == null) {
				trackedWindows.remove(tracked);
				continue;
			}
			persist(tracked.configManager, tracked.id, frame);
		}
	}

	private static void persist(ConfigManager configManager, WindowId id, JFrame frame) {
		if (configManager == null || frame == null || !frame.isDisplayable()) {
			return;
		}

		AppSettings settings = configManager.getSettings();
		if (settings == null) {
			return;
		}
		if (settings.getUi() == null) {
			settings.setUi(new AppSettings.UiSettings());
		}

		AppSettings.UiSettings.WindowPlacement placement = resolvePlacement(configManager, id);
		if (placement == null) {
			return;
		}

		boolean maximized = (frame.getExtendedState() & Frame.MAXIMIZED_BOTH) == Frame.MAXIMIZED_BOTH;
		boolean changed = false;
		if (placement.isMaximized() != maximized) {
			placement.setMaximized(maximized);
			changed = true;
		}

		if (!maximized) {
			Rectangle bounds = frame.getBounds();
			changed |= updateInt(placement.getX(), bounds.x, placement::setX);
			changed |= updateInt(placement.getY(), bounds.y, placement::setY);
			changed |= updateInt(placement.getWidth(), bounds.width, placement::setWidth);
			changed |= updateInt(placement.getHeight(), bounds.height, placement::setHeight);
		}

		if (!changed) {
			return;
		}

		try {
			configManager.saveSettings();
		} catch (IOException e) {
			logger.debug("Failed to persist window placement for {}", id, e);
		}
	}

	private static boolean updateInt(Integer current, int next, java.util.function.Consumer<Integer> setter) {
		if (current != null && current == next) {
			return false;
		}
		setter.accept(next);
		return true;
	}

	private static AppSettings.UiSettings.WindowPlacement resolvePlacement(ConfigManager configManager, WindowId id) {
		AppSettings settings = configManager.getSettings();
		if (settings == null) {
			return null;
		}
		AppSettings.UiSettings ui = settings.getUi();
		if (ui == null) {
			ui = new AppSettings.UiSettings();
			settings.setUi(ui);
		}

		AppSettings.UiSettings.WindowSettings windows = ui.getWindows();
		return switch (id) {
			case PRESET_MANAGER -> windows.getPresetManager();
			case SETTINGS -> windows.getSettings();
			case REGION_MANAGER -> windows.getRegionManager();
		};
	}

	private static boolean isOnScreen(Rectangle bounds) {
		if (bounds == null || bounds.width <= 0 || bounds.height <= 0) {
			return false;
		}

		GraphicsEnvironment environment = GraphicsEnvironment.getLocalGraphicsEnvironment();
		for (GraphicsDevice device : environment.getScreenDevices()) {
			GraphicsConfiguration config = device.getDefaultConfiguration();
			if (config != null && config.getBounds() != null && config.getBounds().intersects(bounds)) {
				return true;
			}
		}
		return false;
	}

	private static void track(TrackedWindow tracked) {
		for (TrackedWindow existing : trackedWindows) {
			if (existing.isSame(tracked)) {
				return;
			}
		}
		trackedWindows.add(tracked);
	}

	private static final class TrackedWindow {
		private final ConfigManager configManager;
		private final WindowId id;
		private final WeakReference<JFrame> frameRef;

		private TrackedWindow(ConfigManager configManager, WindowId id, JFrame frame) {
			this.configManager = configManager;
			this.id = id;
			this.frameRef = new WeakReference<>(frame);
		}

		private boolean isSame(TrackedWindow other) {
			if (other == null) {
				return false;
			}
			JFrame a = frameRef.get();
			JFrame b = other.frameRef.get();
			if (a == null || b == null) {
				return false;
			}
			return a == b && configManager == other.configManager && id == other.id;
		}
	}
}
