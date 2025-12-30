package com.lansoftprogramming.runeSequence.ui.taskbar;

import com.lansoftprogramming.runeSequence.infrastructure.config.ConfigManager;
import com.lansoftprogramming.runeSequence.ui.settings.HotkeySettingsPanel;
import com.lansoftprogramming.runeSequence.ui.settings.IconSizeSettingsPanel;
import com.lansoftprogramming.runeSequence.ui.theme.*;

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
	        ThemedWindowDecorations.applyTitleBar(settingsFrame);

	        ThemedPanel root = new ThemedPanel(PanelStyle.TAB_CONTENT, new BorderLayout());
	        settingsFrame.setContentPane(root);

			JTabbedPane tabs = new JTabbedPane();
			tabs.addTab("General", new IconSizeSettingsPanel(configManager));
			tabs.addTab("Hotkeys", new HotkeySettingsPanel(configManager));
	        applyTabbedPaneTheme(tabs);

	        root.add(tabs, BorderLayout.CENTER);
			settingsFrame.pack();
			settingsFrame.setLocationRelativeTo(null);
			settingsFrame.setVisible(true);
		});
	}

	private static void applyTabbedPaneTheme(JTabbedPane tabs) {
		if (tabs == null) {
			return;
		}

		Theme theme = ThemeManager.getTheme();
		Color background = UiColorPalette.UI_CARD_BACKGROUND;
		Color activeForeground = theme != null ? theme.getTextPrimaryColor() : UiColorPalette.UI_TEXT_COLOR;
		Color inactiveForeground = theme != null ? theme.getTextMutedColor() : UiColorPalette.DIALOG_MESSAGE_TEXT;

		tabs.setOpaque(false);
		tabs.setBorder(UiColorPalette.CARD_BORDER);
		tabs.setBackground(background);
		tabs.setForeground(activeForeground);

		Runnable applyPerTabColors = () -> {
			int selected = tabs.getSelectedIndex();
			for (int i = 0; i < tabs.getTabCount(); i++) {
				tabs.setBackgroundAt(i, background);
				tabs.setForegroundAt(i, i == selected ? activeForeground : inactiveForeground);
			}
		};

		applyPerTabColors.run();
		tabs.addChangeListener(e -> applyPerTabColors.run());
	}
}