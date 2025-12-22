package com.lansoftprogramming.runeSequence.ui.regionSelector;

import com.lansoftprogramming.runeSequence.infrastructure.config.ConfigManager;
import com.lansoftprogramming.runeSequence.ui.notification.DefaultNotificationService;
import com.lansoftprogramming.runeSequence.ui.notification.NotificationService;
import com.lansoftprogramming.runeSequence.ui.overlay.toast.ToastManager;
import com.lansoftprogramming.runeSequence.ui.taskbar.MenuAction;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.swing.*;
import java.awt.*;

public class RegionSelectorAction implements MenuAction {

    private static final Logger logger = LoggerFactory.getLogger(RegionSelectorAction.class);
    private final ConfigManager configManager;
	private JFrame regionsFrame;

    public RegionSelectorAction(ConfigManager configManager) {
        this.configManager = configManager;
    }

    @Override
    public void execute() {
	    if (regionsFrame != null && regionsFrame.isShowing()) {
		    regionsFrame.toFront();
		    return;
	    }

	    SwingUtilities.invokeLater(() -> {
		    regionsFrame = new RegionManagerWindow(configManager, this::createNotificationService);
		    regionsFrame.addWindowListener(new java.awt.event.WindowAdapter() {
			    @Override
			    public void windowClosed(java.awt.event.WindowEvent e) {
				    regionsFrame = null;
                }
		    });
		    regionsFrame.setVisible(true);
	    });
    }

	private NotificationService createNotificationService() {
		JFrame host = resolveVisibleFrame();
		if (host == null) {
			return new DefaultNotificationService((Component) null);
		}
		return new DefaultNotificationService(host, getOrCreateToastManager(host));
    }

	private JFrame resolveVisibleFrame() {
		if (regionsFrame != null && regionsFrame.isVisible()) {
			return regionsFrame;
		}
        for (Window window : Window.getWindows()) {
	        if (window instanceof JFrame frame && frame.isVisible()) {
		        return frame;
	        }
        }
		return null;
	}

	private static ToastManager getOrCreateToastManager(JFrame frame) {
		Object existing = frame.getRootPane().getClientProperty(ToastManager.class);
		if (existing instanceof ToastManager manager) {
			return manager;
		}

		ToastManager manager = new ToastManager(frame);
		frame.getRootPane().putClientProperty(ToastManager.class, manager);
		return manager;
	}
}