package com.lansoftprogramming.runeSequence.infrastructure.hotkey;

import org.jnativehook.GlobalScreen;
import org.jnativehook.NativeHookException;
import org.jnativehook.keyboard.NativeKeyEvent;
import org.jnativehook.keyboard.NativeKeyListener;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.List;

public class HotkeyManager {
	private static final Logger logger = LoggerFactory.getLogger(HotkeyManager.class);
	private final List<HotkeyListener> listeners = new ArrayList<>();
	private NativeHotkeyListener nativeListener;
	private boolean initialized = false;

	public void initialize() {
		if (initialized) return;
		java.util.logging.LogManager.getLogManager().reset();
		try {
			GlobalScreen.registerNativeHook();
			nativeListener = new NativeHotkeyListener();
			GlobalScreen.addNativeKeyListener(nativeListener);
			initialized = true;
			logger.info("Hotkey system initialized");
		} catch (NativeHookException e) {
			logger.error("Failed to initialize native hook", e);
			throw new RuntimeException("Could not initialize hotkey system", e);
		}
	}

	public void addListener(HotkeyListener listener) {
		listeners.add(listener);
	}

	public void removeListener(HotkeyListener listener) {
		listeners.remove(listener);
	}

	public void shutdown() {
		if (!initialized) return;

		try {
			GlobalScreen.removeNativeKeyListener(nativeListener);
			GlobalScreen.unregisterNativeHook();
			initialized = false;
			logger.info("Hotkey system shut down");
		} catch (NativeHookException e) {
			logger.error("Error shutting down hotkey system", e);
		}
	}

	private void notifyListeners(HotkeyEvent event) {
		for (HotkeyListener listener : listeners) {
			try {
				switch (event) {
					case START_SEQUENCE -> listener.onStartSequence();
					case RESTART_SEQUENCE -> listener.onRestartSequence();
				}
			} catch (Exception e) {
				logger.error("Error notifying hotkey listener", e);
			}
		}
	}

	public enum HotkeyEvent {
		START_SEQUENCE,
		RESTART_SEQUENCE
	}

	public interface HotkeyListener {
		default void onStartSequence() {}
		default void onRestartSequence() {}
	}

	// CHANGED CLASS NAME
	private class NativeHotkeyListener implements NativeKeyListener {
		private boolean ctrlPressed = false;

		@Override
		public void nativeKeyPressed(NativeKeyEvent e) {
			if (e.getKeyCode() == NativeKeyEvent.VC_CONTROL) {
				ctrlPressed = true;
			} else if (ctrlPressed) {
				switch (e.getKeyCode()) {
					case NativeKeyEvent.VC_F1 -> {
						logger.info("CTRL+F1 pressed - Starting sequence");
						notifyListeners(HotkeyEvent.START_SEQUENCE);
					}
					case NativeKeyEvent.VC_F2 -> {
						logger.info("CTRL+F2 pressed - Restarting sequence");
						notifyListeners(HotkeyEvent.RESTART_SEQUENCE);
					}
				}
			}
		}

		@Override
		public void nativeKeyReleased(NativeKeyEvent e) {
			if (e.getKeyCode() == NativeKeyEvent.VC_CONTROL) {
				ctrlPressed = false;
			}
		}

		@Override
		public void nativeKeyTyped(NativeKeyEvent e) {
			// Not used
		}
	}
}