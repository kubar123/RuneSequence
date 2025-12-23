package com.lansoftprogramming.runeSequence.ui.notification;

import com.lansoftprogramming.runeSequence.ui.overlay.toast.ToastClient;
import com.lansoftprogramming.runeSequence.ui.theme.ThemedDialogs;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.swing.*;
import java.awt.*;

/**
 * Swing-oriented implementation that prefers the toast overlay when available
 * and falls back to standard dialogs.
 */
public class DefaultNotificationService implements NotificationService {
	private static final String DEFAULT_TITLE = "RuneSequence";
	private static final Logger logger = LoggerFactory.getLogger(DefaultNotificationService.class);

	private final Component parentComponent;
	private final ToastClient toastClient;

	public DefaultNotificationService(Component parentComponent, ToastClient toastClient) {
		this.parentComponent = parentComponent;
		this.toastClient = toastClient;
	}

	public DefaultNotificationService(Component parentComponent) {
		this(parentComponent, null);
	}

	@Override
	public void showInfo(String message) {
		if (toastClient != null) {
			runOnEdt(() -> toastClient.info(message));
			return;
		}
		showDialog(DEFAULT_TITLE, message);
	}

	@Override
	public void showSuccess(String message) {
		if (toastClient != null) {
			runOnEdt(() -> toastClient.success(message));
			return;
		}
		showDialog(DEFAULT_TITLE, message);
	}

	@Override
	public void showWarning(String message) {
		if (toastClient != null) {
			runOnEdt(() -> toastClient.warn(message));
			return;
		}
		showDialog(DEFAULT_TITLE, message);
	}

	@Override
	public void showError(String message) {
		if (toastClient != null) {
			runOnEdt(() -> toastClient.error(message));
			return;
		}
		showDialog(DEFAULT_TITLE, message);
	}

	@Override
	public boolean showConfirmDialog(String title, String message) {
		return invokeAndReturn(() -> ThemedDialogs.showConfirmDialog(
				parentComponent,
				title != null ? title : DEFAULT_TITLE,
				message
		));
	}

	private void showDialog(String title, String message) {
		runOnEdt(() -> ThemedDialogs.showMessageDialog(
				parentComponent,
				title != null ? title : DEFAULT_TITLE,
				message
		));
	}

	private void runOnEdt(Runnable runnable) {
		if (SwingUtilities.isEventDispatchThread()) {
			runnable.run();
		} else {
			SwingUtilities.invokeLater(runnable);
		}
	}

	private boolean invokeAndReturn(DialogSupplier supplier) {
		if (SwingUtilities.isEventDispatchThread()) {
			return supplier.get();
		}
		final boolean[] result = new boolean[1];
		try {
			long startNanos = System.nanoTime();
			if (logger.isDebugEnabled()) {
				logger.debug("DefaultNotificationService.invokeAndReturn blocking on EDT for dialog");
			}
			SwingUtilities.invokeAndWait(() -> result[0] = supplier.get());
			if (logger.isDebugEnabled()) {
				long elapsedMs = (System.nanoTime() - startNanos) / 1_000_000;
				logger.debug("DefaultNotificationService.invokeAndReturn dialog completed in {}ms", elapsedMs);
			}
		} catch (Exception e) {
			logger.warn("DefaultNotificationService.invokeAndReturn failed; returning NO_OPTION", e);
			return false;
		}
		return result[0];
	}

	private interface DialogSupplier {
		boolean get();
	}
}