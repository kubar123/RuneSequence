package com.lansoftprogramming.runeSequence.ui.regionSelector;

import com.lansoftprogramming.runeSequence.infrastructure.config.AppSettings;
import com.lansoftprogramming.runeSequence.infrastructure.config.ConfigManager;
import com.lansoftprogramming.runeSequence.ui.notification.DefaultNotificationService;
import com.lansoftprogramming.runeSequence.ui.notification.NotificationService;
import com.lansoftprogramming.runeSequence.ui.overlay.toast.ToastClient;
import com.lansoftprogramming.runeSequence.ui.overlay.toast.ToastManager;
import com.lansoftprogramming.runeSequence.ui.taskbar.MenuAction;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.swing.*;
import java.awt.*;

public class RegionSelectorAction implements MenuAction {

    private static final Logger logger = LoggerFactory.getLogger(RegionSelectorAction.class);
    private static final int SUCCESS_TOAST_DURATION_MS = 3400;
    private static final int ERROR_TOAST_DURATION_MS = 6800;
    private static final int TOAST_HOST_WIDTH = 420;
    private static final int TOAST_HOST_HEIGHT = 240;
    private static final int TOAST_HOST_MARGIN_X = 28;
    private static final int TOAST_HOST_MARGIN_Y = 48;
    private final ConfigManager configManager;

    public RegionSelectorAction(ConfigManager configManager) {
        this.configManager = configManager;
    }

    @Override
    public void execute() {
        SwingUtilities.invokeLater(() -> {
            RegionSelectorWindow selectorWindow = RegionSelectorWindow.selectRegion();

            if (selectorWindow == null || !selectorWindow.isSelectionMade()) {
                logger.info("Region selection was cancelled by the user.");
                return;
            }

            ToastContext toastContext = createToastContext();
            NotificationService notifications = new DefaultNotificationService(
                    toastContext.parentComponent(),
                    toastContext.toastClient()
            );

            boolean saved = false;

            try {
                Rectangle selectedRegion = selectorWindow.getSelectedRegion();
                Rectangle screenBounds = selectorWindow.getScreenBounds();

                logger.info("Region selected: {}. Saving to settings...", selectedRegion);
                AppSettings settings = configManager.getSettings();
                if (settings == null) {
                    throw new IllegalStateException("Settings are not loaded. Cannot save region.");
                }

                AppSettings.RegionSettings regionSettings = settings.getRegion();
                regionSettings.setX(selectedRegion.x);
                regionSettings.setY(selectedRegion.y);
                regionSettings.setWidth(selectedRegion.width);
                regionSettings.setHeight(selectedRegion.height);

                // Use the screen bounds from the selector for consistency
                regionSettings.setScreenWidth(screenBounds.width);
                regionSettings.setScreenHeight(screenBounds.height);

                configManager.saveSettings();
                logger.info("Region settings saved successfully.");

                notifications.showSuccess("Region saved successfully!");
                saved = true;

            } catch (Exception e) {
                logger.error("Failed to save region settings.", e);
                notifications.showError("Error saving region: " + e.getMessage());
            } finally {
                int delay = saved ? SUCCESS_TOAST_DURATION_MS : ERROR_TOAST_DURATION_MS;
                toastContext.scheduleCleanup(delay);
            }
        });
    }

    private ToastContext createToastContext() {
        for (Window window : Window.getWindows()) {
            if (window instanceof JFrame && window.isVisible()) {
                return ToastContext.reuse((JFrame) window);
            }
        }

        Rectangle bounds = GraphicsEnvironment.getLocalGraphicsEnvironment().getMaximumWindowBounds();
        int width = TOAST_HOST_WIDTH;
        int height = TOAST_HOST_HEIGHT;
        int x = bounds.x + bounds.width - width - TOAST_HOST_MARGIN_X;
        int y = bounds.y + bounds.height - height - TOAST_HOST_MARGIN_Y;

        JWindow toastWindow = new JWindow();
        toastWindow.setAlwaysOnTop(true);
        toastWindow.setFocusableWindowState(false);
        toastWindow.setBackground(new Color(0, 0, 0, 0));
        toastWindow.setSize(width, height);
        toastWindow.setLocation(x, y);
        toastWindow.setVisible(true);

        ToastManager toastManager = new ToastManager(toastWindow);
        return ToastContext.temporary(toastWindow, toastManager);
    }

    private static final class ToastContext {
        private final Component parentComponent;
        private final ToastClient toastClient;
        private final Window hostWindow;
        private final boolean temporaryHost;

        private ToastContext(Component parentComponent, ToastClient toastClient, Window hostWindow, boolean temporaryHost) {
            this.parentComponent = parentComponent;
            this.toastClient = toastClient;
            this.hostWindow = hostWindow;
            this.temporaryHost = temporaryHost;
        }

        static ToastContext reuse(JFrame host) {
            ToastManager manager = getOrCreateToastManager(host);
            return new ToastContext(host, manager, host, false);
        }

        static ToastContext temporary(JWindow host, ToastManager manager) {
            return new ToastContext(host, manager, host, true);
        }

        Component parentComponent() {
            return parentComponent;
        }

        ToastClient toastClient() {
            return toastClient;
        }

        void scheduleCleanup(int delayMillis) {
            if (!temporaryHost || hostWindow == null) {
                return;
            }

            Timer timer = new Timer(Math.max(delayMillis, 1), e -> hostWindow.dispose());
            timer.setRepeats(false);
            timer.start();
        }

        private static ToastManager getOrCreateToastManager(JFrame frame) {
            Object existing = frame.getRootPane().getClientProperty(ToastManager.class);
            if (existing instanceof ToastManager) {
                return (ToastManager) existing;
            }

            ToastManager manager = new ToastManager(frame);
            frame.getRootPane().putClientProperty(ToastManager.class, manager);
            return manager;
        }
    }
}
