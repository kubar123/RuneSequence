package com.lansoftprogramming.runeSequence.ui.regionSelector;

import com.lansoftprogramming.runeSequence.infrastructure.config.AppSettings;
import com.lansoftprogramming.runeSequence.infrastructure.config.ConfigManager;
import com.lansoftprogramming.runeSequence.ui.notification.DefaultNotificationService;
import com.lansoftprogramming.runeSequence.ui.notification.NotificationService;
import com.lansoftprogramming.runeSequence.ui.taskbar.MenuAction;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.swing.*;
import java.awt.*;

public class RegionSelectorAction implements MenuAction {

    private static final Logger logger = LoggerFactory.getLogger(RegionSelectorAction.class);
    private final ConfigManager configManager;
    private final NotificationService notifications;

    public RegionSelectorAction(ConfigManager configManager) {
        this.configManager = configManager;
        this.notifications = new DefaultNotificationService(null);
    }

    @Override
    public void execute() {
        SwingUtilities.invokeLater(() -> {
            RegionSelectorWindow selectorWindow = RegionSelectorWindow.selectRegion();

            if (selectorWindow != null && selectorWindow.isSelectionMade()) {
                try {
                    Rectangle selectedRegion = selectorWindow.getSelectedRegion();
                    Rectangle screenBounds = selectorWindow.getScreenBounds();

                    logger.info("Region selected: {}. Saving to settings...", selectedRegion);
                    AppSettings settings = configManager.getSettings();
                    if (settings == null) {
                        logger.error("Settings are not loaded. Cannot save region.");
                        return;
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

                    // Optional: provide user feedback
                    notifications.showSuccess("Region saved successfully!");

                } catch (Exception e) {
                    logger.error("Failed to save region settings.", e);
                    notifications.showError("Error saving region: " + e.getMessage());
                }
            } else {
                logger.info("Region selection was cancelled by the user.");
            }
        });
    }
}