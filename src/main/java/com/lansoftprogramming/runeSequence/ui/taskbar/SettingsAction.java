package com.lansoftprogramming.runeSequence.ui.taskbar;

import com.lansoftprogramming.runeSequence.infrastructure.config.AppSettings;
import com.lansoftprogramming.runeSequence.infrastructure.config.ConfigManager;
import com.lansoftprogramming.runeSequence.ui.settings.DebugSettingsPanel;
import com.lansoftprogramming.runeSequence.ui.settings.HotkeySettingsPanel;
import com.lansoftprogramming.runeSequence.ui.settings.IconSizeSettingsPanel;
import com.lansoftprogramming.runeSequence.ui.settings.debug.IconDetectionDebugService;
import com.lansoftprogramming.runeSequence.ui.shared.AppIcon;
import com.lansoftprogramming.runeSequence.ui.theme.*;

import javax.swing.*;
import java.awt.*;
import java.awt.event.WindowAdapter;
import java.awt.event.WindowEvent;

public class SettingsAction implements MenuAction {

	private final ConfigManager configManager;
	private final IconDetectionDebugService iconDetectionDebugService;
	private JFrame settingsFrame;

	public SettingsAction(ConfigManager configManager) {
		this(configManager, null);
	}

	public SettingsAction(ConfigManager configManager, IconDetectionDebugService iconDetectionDebugService) {
		this.configManager = configManager;
		this.iconDetectionDebugService = iconDetectionDebugService;
	}

	@Override
	public void execute() {
		if (settingsFrame != null && settingsFrame.isShowing()) {
			settingsFrame.toFront();
			return;
        }

        SwingUtilities.invokeLater(() -> {
			settingsFrame = new JFrame("RuneSequence - Settings");
			settingsFrame.setName("settingsWindow");
			settingsFrame.setDefaultCloseOperation(JFrame.DISPOSE_ON_CLOSE);
			ThemedWindowDecorations.applyTitleBar(settingsFrame);
			java.util.List<Image> icons = AppIcon.loadWindowIcons();
			if (!icons.isEmpty()) {
				settingsFrame.setIconImages(icons);
			}

			ThemedPanel root = new ThemedPanel(PanelStyle.TAB_CONTENT, new BorderLayout());
			settingsFrame.setContentPane(root);

			JTabbedPane tabs = new ThemedSettingsTabbedPane();
			tabs.addTab("General", new IconSizeSettingsPanel(configManager));
			tabs.addTab("Hotkeys", new HotkeySettingsPanel(configManager));
	        tabs.addTab("Debug", new DebugSettingsPanel(iconDetectionDebugService));
			applyTabbedPaneTheme(tabs);

			root.add(tabs, BorderLayout.CENTER);
			settingsFrame.pack();

			AppSettings settings = configManager != null ? configManager.getSettings() : null;
	        boolean alwaysOnTop = settings != null
			        && settings.getUi() != null
			        && settings.getUi().isPresetManagerAlwaysOnTop();
	        settingsFrame.setAlwaysOnTop(alwaysOnTop);

				settingsFrame.setLocationRelativeTo(null);

	        settingsFrame.addWindowListener(new WindowAdapter() {
		        @Override
		        public void windowClosing(WindowEvent e) {
			        stopDebugIfRunning();
		        }

		        @Override
		        public void windowClosed(WindowEvent e) {
			        stopDebugIfRunning();
		        }

		        private void stopDebugIfRunning() {
			        if (iconDetectionDebugService != null && iconDetectionDebugService.isRunning()) {
				        iconDetectionDebugService.stop();
			        }
		        }
	        });

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

		tabs.setOpaque(true);
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

	private static final class ThemedSettingsTabbedPane extends JTabbedPane {
		@Override
		protected void paintComponent(Graphics graphics) {
			super.paintComponent(graphics);

			int tabCount = getTabCount();
			if (tabCount <= 0) {
				return;
			}

			Rectangle lastTab = getBoundsAt(tabCount - 1);
			if (lastTab == null) {
				return;
			}

			int width = getWidth();
			int height = getHeight();
			if (width <= 0 || height <= 0) {
				return;
			}

			int tabStripHeight = Math.max(0, lastTab.y + lastTab.height);
			if (tabStripHeight <= 0) {
				return;
			}

			int gapX = lastTab.x + lastTab.width;
			if (gapX >= width) {
				return;
			}

			Graphics2D g2 = graphics instanceof Graphics2D ? (Graphics2D) graphics.create() : null;
			if (g2 == null) {
				return;
			}

			try {
				g2.setColor(getBackground());
				g2.fillRect(gapX, 0, width - gapX, tabStripHeight);
			} finally {
				g2.dispose();
			}
		}
	}
}