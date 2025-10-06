package com.lansoftprogramming.runeSequence.gui;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.swing.*;
import java.awt.*;
import java.net.URL;

public class Taskbar {

    private static final Logger logger = LoggerFactory.getLogger(Taskbar.class);
    private TrayIcon trayIcon;

    public void initialize() {
        if (!SystemTray.isSupported()) {
            logger.error("System tray is not supported on this platform.");
            return;
        }

        final PopupMenu popup = new PopupMenu();

        URL iconUrl = getClass().getResource("/icon.png");
        if (iconUrl == null) {
            logger.error("Could not find icon resource: icon.png");
            return;
        }
        Image image = new ImageIcon(iconUrl).getImage();
        trayIcon = new TrayIcon(image, "RuneSequence", popup);
        trayIcon.setImageAutoSize(true);

        SystemTray tray = SystemTray.getSystemTray();

        MenuItem exitItem = new MenuItem("Exit");
        exitItem.addActionListener(e -> {
            tray.remove(trayIcon);
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