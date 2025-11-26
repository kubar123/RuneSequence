package com.lansoftprogramming.runeSequence.ui.taskbar;

import com.lansoftprogramming.runeSequence.infrastructure.config.ConfigManager;
import com.lansoftprogramming.runeSequence.ui.settings.HotkeySettingsPanel;
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

			JTabbedPane tabs = new JTabbedPane();
			tabs.addTab("General", new IconSizeSettingsPanel(configManager));
			tabs.addTab("Hotkeys", new HotkeySettingsPanel(configManager));

			settingsFrame.add(tabs, BorderLayout.CENTER);
			settingsFrame.pack();
			settingsFrame.setLocationRelativeTo(null);
			settingsFrame.setVisible(true);
		});
	}
}
