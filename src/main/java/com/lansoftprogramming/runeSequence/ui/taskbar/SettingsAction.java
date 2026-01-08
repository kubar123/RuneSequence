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
import java.util.function.Consumer;

public class SettingsAction implements MenuAction {

	private final ConfigManager configManager;
	private final IconDetectionDebugService iconDetectionDebugService;
	private JFrame settingsFrame;
	private JTabbedPane tabs;
	private Consumer<AppSettings> settingsSaveListener;

	private static boolean isDebugToolsEnabled() {
		return Boolean.getBoolean("runeSequence.debugTools");
	}

	private boolean shouldShowDebugTab(AppSettings settings) {
		if (isDebugToolsEnabled()) {
			return true;
		}
		return settings != null
				&& settings.getUi() != null
				&& settings.getUi().isShowDebugOptions();
	}

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

			tabs = new ThemedSettingsTabbedPane();
			tabs.addTab("General", new IconSizeSettingsPanel(configManager));
			tabs.addTab("Hotkeys", new HotkeySettingsPanel(configManager));
			refreshDebugTab(configManager != null ? configManager.getSettings() : null, true);
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
					uninstallSettingsListener();
			        stopDebugIfRunning();
		        }

		        @Override
		        public void windowClosed(WindowEvent e) {
					uninstallSettingsListener();
			        stopDebugIfRunning();
		        }

		        private void stopDebugIfRunning() {
			        if (iconDetectionDebugService != null && iconDetectionDebugService.isRunning()) {
				        iconDetectionDebugService.stop();
			        }
		        }
	        });

			installSettingsListener();
			settingsFrame.setVisible(true);
		});
	}

	private void installSettingsListener() {
		if (configManager == null || settingsSaveListener != null) {
			return;
		}
		settingsSaveListener = settings -> SwingUtilities.invokeLater(() -> {
			refreshDebugTab(settings, false);
			if (tabs != null) {
				applyTabbedPaneTheme(tabs);
			}
		});
		configManager.addSettingsSaveListener(settingsSaveListener);
	}

	private void uninstallSettingsListener() {
		if (configManager == null || settingsSaveListener == null) {
			return;
		}
		configManager.removeSettingsSaveListener(settingsSaveListener);
		settingsSaveListener = null;
		tabs = null;
		settingsFrame = null;
	}

	private void refreshDebugTab(AppSettings settings, boolean initialRender) {
		if (tabs == null) {
			return;
		}

		boolean shouldShow = shouldShowDebugTab(settings);
		int debugIndex = findTabIndexByTitle(tabs, "Debug");

		if (shouldShow) {
			if (debugIndex >= 0) {
				return;
			}
			tabs.addTab("Debug", new DebugSettingsPanel(configManager, iconDetectionDebugService));
			if (!initialRender) {
				int idx = findTabIndexByTitle(tabs, "Debug");
				if (idx >= 0) {
					tabs.setSelectedIndex(idx);
				}
			}
		} else {
			if (debugIndex < 0) {
				return;
			}
			tabs.removeTabAt(debugIndex);
		}
	}

	private static int findTabIndexByTitle(JTabbedPane tabs, String title) {
		if (tabs == null || title == null) {
			return -1;
		}
		for (int i = 0; i < tabs.getTabCount(); i++) {
			if (title.equals(tabs.getTitleAt(i))) {
				return i;
			}
		}
		return -1;
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

		updateTabbedPaneColors(tabs);
		installTabbedPaneThemeListener(tabs);
	}

	private static void installTabbedPaneThemeListener(JTabbedPane tabs) {
		final String key = "runeSequence.settingsTabs.themeListener";
		Object existing = tabs.getClientProperty(key);
		if (existing instanceof javax.swing.event.ChangeListener) {
			return;
		}
		javax.swing.event.ChangeListener listener = e -> updateTabbedPaneColors(tabs);
		tabs.putClientProperty(key, listener);
		tabs.addChangeListener(listener);
	}

	private static void updateTabbedPaneColors(JTabbedPane tabs) {
		if (tabs == null) {
			return;
		}
		Theme theme = ThemeManager.getTheme();
		Color background = UiColorPalette.UI_CARD_BACKGROUND;
		Color activeForeground = theme != null ? theme.getTextPrimaryColor() : UiColorPalette.UI_TEXT_COLOR;
		Color inactiveForeground = theme != null ? theme.getTextMutedColor() : UiColorPalette.DIALOG_MESSAGE_TEXT;

		int selected = tabs.getSelectedIndex();
		for (int i = 0; i < tabs.getTabCount(); i++) {
			tabs.setBackgroundAt(i, background);
			tabs.setForegroundAt(i, i == selected ? activeForeground : inactiveForeground);
		}
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
