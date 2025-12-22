package com.lansoftprogramming.runeSequence.infrastructure.hotkey;

import org.jnativehook.GlobalScreen;
import org.jnativehook.NativeHookException;
import org.jnativehook.keyboard.NativeKeyEvent;
import org.jnativehook.keyboard.NativeKeyListener;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.*;
import java.util.concurrent.atomic.AtomicReference;

public class HotkeyManager {
	private static final Logger logger = LoggerFactory.getLogger(HotkeyManager.class);

	// listeners are per-instance; the native hook and listener are process-wide
	private final List<HotkeyListener> listeners = new ArrayList<>();
	private static volatile boolean nativeInitialized = false;
	private static volatile NativeHotkeyListener nativeListener;
	private static final Object HOOK_LOCK = new Object();

	private final AtomicReference<Map<HotkeyEvent, List<KeyChord>>> hotkeyBindingsRef;

	public HotkeyManager(Map<HotkeyEvent, List<KeyChord>> hotkeyBindings) {
		this.hotkeyBindingsRef = new AtomicReference<>((hotkeyBindings != null)
				? hotkeyBindings
				: Collections.emptyMap());
	}

	public void refreshBindings(Map<HotkeyEvent, List<KeyChord>> hotkeyBindings) {
		hotkeyBindingsRef.set((hotkeyBindings != null)
				? hotkeyBindings
				: Collections.emptyMap());
	}

	public void initialize() {
		if (nativeInitialized) return;
		synchronized (HOOK_LOCK) {
			if (nativeInitialized) return;
			try {
				if (!GlobalScreen.isNativeHookRegistered()) {
					// silence JNativeHook's own logging
					java.util.logging.LogManager.getLogManager().reset();
					GlobalScreen.registerNativeHook();
				}
				if (nativeListener == null) {
					nativeListener = new NativeHotkeyListener(this::notifyListeners, hotkeyBindingsRef);
					GlobalScreen.addNativeKeyListener(nativeListener);
				}
				nativeInitialized = true;

				Runtime.getRuntime().addShutdownHook(new Thread(() -> {
					try {
						shutdown();
					} catch (Exception ignored) {
					}
				}));
			} catch (NativeHookException e) {
				throw new RuntimeException("Could not initialize hotkey system", e);
			}
		}
	}

	public void addListener(HotkeyListener listener) {
		listeners.add(listener);
	}

	public void removeListener(HotkeyListener listener) {
		listeners.remove(listener);
	}

	public void shutdown() {
		if (!nativeInitialized) return;
		synchronized (HOOK_LOCK) {
			if (!nativeInitialized) return;
			try {
				if (nativeListener != null) {
					GlobalScreen.removeNativeKeyListener(nativeListener);
					nativeListener = null;
				}
				if (GlobalScreen.isNativeHookRegistered()) {
					GlobalScreen.unregisterNativeHook();
				}
			} catch (NativeHookException ignored) {
			} finally {
				nativeInitialized = false;
			}
		}
	}

	private void notifyListeners(HotkeyEvent event) {
		for (HotkeyListener listener : listeners) {
			try {
				switch (event) {
					case START_SEQUENCE -> listener.onStartSequence();
					case RESTART_SEQUENCE -> listener.onRestartSequence();
				}
			} catch (Exception ignored) {
			}
		}
	}

	private static final class NativeHotkeyListener implements NativeKeyListener {
		private final EnumSet<ModifierKey> activeModifiers = EnumSet.noneOf(ModifierKey.class);
		private final Notifier notifier;
		private final AtomicReference<Map<HotkeyEvent, List<KeyChord>>> bindingsRef;

		NativeHotkeyListener(Notifier notifier, AtomicReference<Map<HotkeyEvent, List<KeyChord>>> bindingsRef) {
			this.notifier = notifier;
			this.bindingsRef = bindingsRef;
		}

		@Override
		public void nativeKeyPressed(NativeKeyEvent e) {
			ModifierKey mk = ModifierKey.fromKeyCode(e.getKeyCode());
			if (mk != null) {
				activeModifiers.add(mk);
				return;
			}
			KeyChord chord = new KeyChord(activeModifiers, e.getKeyCode());
			Map<HotkeyEvent, List<KeyChord>> bindings = bindingsRef.get();
			for (Map.Entry<HotkeyEvent, List<KeyChord>> en : bindings.entrySet()) {
				List<KeyChord> chords = en.getValue();
				if (chords != null && chords.contains(chord)) {
					if (logger.isDebugEnabled()) {
						EnumSet<ModifierKey> snapshot = activeModifiers.isEmpty()
								? EnumSet.noneOf(ModifierKey.class)
								: EnumSet.copyOf(activeModifiers);
						logger.debug("HotkeyManager: Hotkey pressed for event {} key={} modifiers={}",
								en.getKey(), NativeKeyEvent.getKeyText(e.getKeyCode()), snapshot);
					}
					notifier.notify(en.getKey());
				}
			}
		}

		@Override
		public void nativeKeyReleased(NativeKeyEvent e) {
			ModifierKey mk = ModifierKey.fromKeyCode(e.getKeyCode());
			if (mk != null) {
				activeModifiers.remove(mk);
			}
		}

		@Override
		public void nativeKeyTyped(NativeKeyEvent e) {
		}
	}

	@FunctionalInterface
	private interface Notifier {
		void notify(HotkeyEvent event);
	}
}
