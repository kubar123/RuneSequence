package com.lansoftprogramming.runeSequence.gui.actions;

import com.lansoftprogramming.runeSequence.config.ConfigManager;
import com.lansoftprogramming.runeSequence.gui.MenuAction;
import com.lansoftprogramming.runeSequence.gui.PresetManagerWindow;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.swing.*;

public class PresetManagerAction implements MenuAction {

    private static final Logger logger = LoggerFactory.getLogger(PresetManagerAction.class);
    private final ConfigManager configManager;
    private PresetManagerWindow presetManagerWindow;

    public PresetManagerAction(ConfigManager configManager) {
        this.configManager = configManager;
    }

    @Override
    public void execute() {
        SwingUtilities.invokeLater(() -> {
            if (presetManagerWindow == null || !presetManagerWindow.isVisible()) {
                logger.info("Opening Preset Manager window...");
                presetManagerWindow = new PresetManagerWindow(configManager);
            } else {
                logger.info("Preset Manager window is already open, bringing to front.");
                presetManagerWindow.toFront();
                presetManagerWindow.requestFocus();
            }
        });
    }
}