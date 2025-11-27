package com.lansoftprogramming.runeSequence.ui.notification;

/**
 * Central interface for user-facing notifications across the application.
 * Implementations may render toasts, dialogs, or other UI affordances,
 * but callers should not depend on the underlying mechanism.
 */
public interface NotificationService {
	void showInfo(String message);

	void showSuccess(String message);

	void showWarning(String message);

	void showError(String message);

	/**
	 * Presents a confirmation dialog with Yes/No options.
	 * @return {@code true} if the user selects Yes; {@code false} otherwise.
	 */
	boolean showConfirmDialog(String title, String message);
}
