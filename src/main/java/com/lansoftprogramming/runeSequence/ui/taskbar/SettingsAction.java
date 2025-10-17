package com.lansoftprogramming.runeSequence.ui.taskbar;

import javax.swing.*;
import java.awt.*;

public class SettingsAction implements MenuAction {

    private JFrame settingsFrame;

    @Override
    public void execute() {
        if (settingsFrame != null && settingsFrame.isShowing()) {
            settingsFrame.toFront();
            return;
        }

        SwingUtilities.invokeLater(() -> {
            settingsFrame = new JFrame("Settings");
            settingsFrame.setDefaultCloseOperation(JFrame.DISPOSE_ON_CLOSE);
            settingsFrame.setLayout(new BorderLayout());
            settingsFrame.add(new JLabel("Settings window placeholder.", SwingConstants.CENTER), BorderLayout.CENTER);
            settingsFrame.pack();
            settingsFrame.setLocationRelativeTo(null);
            settingsFrame.setVisible(true);
        });
    }
}