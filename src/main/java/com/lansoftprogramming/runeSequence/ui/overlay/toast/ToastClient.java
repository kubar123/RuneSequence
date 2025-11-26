package com.lansoftprogramming.runeSequence.ui.overlay.toast;

public interface ToastClient {
	void success(String message);

	void success(String message, String hiddenMessage);

	void info(String message);

	void info(String message, String hiddenMessage);

	void warn(String message);

	void warn(String message, String hiddenMessage);

	void error(String message);

	void error(String message, String hiddenMessage);

	default void clearAll() {
		// no-op for clients that do not support lifecycle
	}
}
