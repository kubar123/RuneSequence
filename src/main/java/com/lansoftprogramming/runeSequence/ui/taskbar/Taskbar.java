package com.lansoftprogramming.runeSequence.ui.taskbar;

import com.lansoftprogramming.runeSequence.ui.shared.AppIcon;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.awt.*;

public class Taskbar {

    private static final Logger logger = LoggerFactory.getLogger(Taskbar.class);
    private TrayIcon trayIcon;
    private SystemTray tray;

    public void initialize() {
        if (!SystemTray.isSupported()) {
            logger.error("System tray is not supported on this platform.");
            return;
        }

        final PopupMenu popup = new PopupMenu();

        tray = SystemTray.getSystemTray();
        Dimension trayIconSize = tray.getTrayIconSize();

        Image image = AppIcon.loadForTray(trayIconSize);
        if (image == null) {
            logger.error("Could not find tray icon resources under /icon/.");
            return;
        }
        trayIcon = new TrayIcon(image, "RuneSequence", popup);
        trayIcon.setImageAutoSize(false);

        MenuItem exitItem = new MenuItem("Exit");
        exitItem.addActionListener(e -> {
            dispose();
            System.exit(0);
        });
        popup.add(exitItem);

        try {
            tray.add(trayIcon);
            logger.info("Taskbar icon added.");
        } catch (AWTException e) {
            logger.error("TrayIcon could not be added.", e);
        }
    }

    public void dispose() {
        if (tray == null || trayIcon == null) {
            return;
        }
        try {
            tray.remove(trayIcon);
        } catch (Exception e) {
            logger.debug("Failed to remove tray icon.", e);
        } finally {
            trayIcon = null;
            tray = null;
        }
    }

    public void addMenuItem(String label, MenuAction action) {
        if (trayIcon == null || trayIcon.getPopupMenu() == null) {
            logger.warn("Taskbar not initialized, cannot add menu item.");
            return;
        }
        MenuItem menuItem = new MenuItem(label);
        menuItem.addActionListener(e -> action.execute());
        trayIcon.getPopupMenu().insert(menuItem, 0);
    }

    public void addSeparator() {
        if (trayIcon == null || trayIcon.getPopupMenu() == null) {
            logger.warn("Taskbar not initialized, cannot add separator.");
            return;
        }
        trayIcon.getPopupMenu().insertSeparator(0);
    }
}
