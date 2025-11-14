package com.lansoftprogramming.runeSequence.ui.taskbar;

import com.lansoftprogramming.runeSequence.infrastructure.config.ConfigManager;
import com.lansoftprogramming.runeSequence.ui.settings.IconSizeSettingsPanel;

import javax.swing.*;
import java.awt.*;

public class SettingsAction implements MenuAction {

	private final ConfigManager configManager;
	private JFrame settingsFrame;

	public SettingsAction(ConfigManager configManager) {
		this.configManager = configManager;
	}

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
			settingsFrame.add(new IconSizeSettingsPanel(configManager), BorderLayout.CENTER);
			settingsFrame.pack();
			settingsFrame.setLocationRelativeTo(null);
			settingsFrame.setVisible(true);
		});
	}
}
