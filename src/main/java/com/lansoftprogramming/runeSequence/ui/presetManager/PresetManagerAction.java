package com.lansoftprogramming.runeSequence.ui.presetManager;

import com.lansoftprogramming.runeSequence.application.SequenceRunService;
import com.lansoftprogramming.runeSequence.infrastructure.config.ConfigManager;
import com.lansoftprogramming.runeSequence.ui.settings.debug.IconDetectionDebugService;
import com.lansoftprogramming.runeSequence.ui.taskbar.MenuAction;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.swing.*;

public class PresetManagerAction implements MenuAction {

    private static final Logger logger = LoggerFactory.getLogger(PresetManagerAction.class);
    private final ConfigManager configManager;
    private final SequenceRunService sequenceRunService;
    private final IconDetectionDebugService iconDetectionDebugService;
    private PresetManagerWindow presetManagerWindow;

    public PresetManagerAction(ConfigManager configManager,
                               SequenceRunService sequenceRunService,
                               IconDetectionDebugService iconDetectionDebugService) {
        this.configManager = configManager;
        this.sequenceRunService = sequenceRunService;
        this.iconDetectionDebugService = iconDetectionDebugService;
    }

    @Override
    public void execute() {
        Runnable openWindow = () -> {
            if (presetManagerWindow == null || !presetManagerWindow.isVisible()) {
                logger.info("Opening Preset Manager window...");
                presetManagerWindow = new PresetManagerWindowBuilder(configManager, sequenceRunService, iconDetectionDebugService).buildAndShow();
                if (presetManagerWindow == null) {
                    logger.error("Preset Manager failed to initialize.");
                }
            } else {
                logger.info("Preset Manager window is already open, bringing to front.");
                presetManagerWindow.toFront();
                presetManagerWindow.requestFocus();
            }
        };

        if (SwingUtilities.isEventDispatchThread()) {
            openWindow.run();
        } else {
            SwingUtilities.invokeLater(openWindow);
        }
    }
}