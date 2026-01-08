package com.lansoftprogramming.runeSequence.ui.settings;

import com.lansoftprogramming.runeSequence.infrastructure.config.AppSettings;
import com.lansoftprogramming.runeSequence.infrastructure.config.ConfigManager;
import com.lansoftprogramming.runeSequence.infrastructure.config.ScalingConverter;
import com.lansoftprogramming.runeSequence.ui.theme.*;

import javax.swing.*;
import javax.swing.border.CompoundBorder;
import javax.swing.border.EmptyBorder;
import java.awt.*;
import java.io.IOException;
import java.util.Arrays;

public class IconSizeSettingsPanel extends ThemedPanel {
	private final ConfigManager configManager;
	private final int[] supportedSizes;
	private final JSpinner sizeSpinner;
	private final JCheckBox blinkCurrentCheck;
	private final JCheckBox abilityIndicatorEnabledCheck;
	private final JSpinner abilityIndicatorLoopMsSpinner;
	private final JCheckBox channeledWaitTooltipsCheck;
	private final JCheckBox showDebugOptionsCheck;
	private final JCheckBox autoSaveCheck;
	private final JCheckBox alwaysOnTopCheck;
	private final JLabel statusLabel;

	public IconSizeSettingsPanel(ConfigManager configManager) {
		super(PanelStyle.TAB_CONTENT, new BorderLayout());
		this.configManager = configManager;
		this.supportedSizes = ScalingConverter.getAllSizes();
		if (supportedSizes.length == 0) {
			throw new IllegalStateException("No supported icon sizes configured.");
		}

		setBorder(new CompoundBorder(getBorder(), new EmptyBorder(15, 15, 15, 15)));

		int currentSize = resolveNearestSize(getConfiguredIconSize());
		sizeSpinner = new JSpinner(new SpinnerNumberModel(
				currentSize,
				supportedSizes[0],
				supportedSizes[supportedSizes.length - 1],
				1
		));

		blinkCurrentCheck = new JCheckBox("Blink current ability highlights on overlay");
		blinkCurrentCheck.setSelected(resolveBlinkPreference());
		blinkCurrentCheck.setOpaque(false);

		abilityIndicatorEnabledCheck = new JCheckBox("Play ability indicator animation when NEXT becomes CURRENT");
		abilityIndicatorEnabledCheck.setSelected(resolveAbilityIndicatorEnabledPreference());
		abilityIndicatorEnabledCheck.setOpaque(false);

		long loopMs = resolveAbilityIndicatorLoopMsPreference();
		abilityIndicatorLoopMsSpinner = new JSpinner(new SpinnerNumberModel(
				(int) Math.max(50, Math.min(10_000, loopMs)),
				50,
				10_000,
				25
		));
		abilityIndicatorLoopMsSpinner.setEnabled(abilityIndicatorEnabledCheck.isSelected());
		abilityIndicatorEnabledCheck.addActionListener(e -> abilityIndicatorLoopMsSpinner.setEnabled(abilityIndicatorEnabledCheck.isSelected()));

		channeledWaitTooltipsCheck = new JCheckBox("Show \"wait\" tooltip at mouse while channeling");
		channeledWaitTooltipsCheck.setSelected(resolveChanneledWaitTooltipsPreference());
		channeledWaitTooltipsCheck.setOpaque(false);

		showDebugOptionsCheck = new JCheckBox("Show debug options");
		showDebugOptionsCheck.setSelected(resolveShowDebugOptionsPreference());
		showDebugOptionsCheck.setOpaque(false);

		autoSaveCheck = new JCheckBox("Auto-save rotations when switching presets");
		autoSaveCheck.setSelected(resolveAutoSavePreference());
		autoSaveCheck.setOpaque(false);

		alwaysOnTopCheck = new JCheckBox("Keep main windows always on top");
		alwaysOnTopCheck.setSelected(resolveAlwaysOnTopPreference());
		alwaysOnTopCheck.setOpaque(false);

		statusLabel = new JLabel(" ");
		statusLabel.setForeground(UiColorPalette.TEXT_MUTED);
		statusLabel.setOpaque(false);

		add(createFormPanel(), BorderLayout.CENTER);
		add(statusLabel, BorderLayout.SOUTH);
	}

	private JPanel createFormPanel() {
		JPanel formPanel = new JPanel();
		formPanel.setOpaque(false);
		formPanel.setLayout(new GridBagLayout());
		GridBagConstraints gbc = new GridBagConstraints();
		gbc.insets = new Insets(4, 4, 4, 4);
		gbc.gridx = 0;
		gbc.gridy = 0;
		gbc.anchor = GridBagConstraints.WEST;

		formPanel.add(new JLabel("Ability icon size (px):"), gbc);

		gbc.gridx = 1;
		formPanel.add(sizeSpinner, gbc);

		gbc.gridx = 0;
		gbc.gridy++;
		gbc.gridwidth = 2;
		JLabel helpLabel = new JLabel("Supported sizes: " + Arrays.toString(supportedSizes));
		helpLabel.setFont(helpLabel.getFont().deriveFont(Font.ITALIC, helpLabel.getFont().getSize() - 1f));
		formPanel.add(helpLabel, gbc);

		gbc.gridy++;
		gbc.gridx = 0;
		gbc.gridwidth = 2;
		formPanel.add(blinkCurrentCheck, gbc);

		gbc.gridy++;
		gbc.gridx = 0;
		gbc.gridwidth = 2;
		formPanel.add(abilityIndicatorEnabledCheck, gbc);

		gbc.gridy++;
		gbc.gridwidth = 1;
		gbc.gridx = 0;
		gbc.anchor = GridBagConstraints.WEST;
		formPanel.add(new JLabel("Ability indicator loop duration (ms):"), gbc);

		gbc.gridx = 1;
		formPanel.add(abilityIndicatorLoopMsSpinner, gbc);

		gbc.gridy++;
		gbc.gridx = 0;
		gbc.gridwidth = 2;
		formPanel.add(channeledWaitTooltipsCheck, gbc);

		gbc.gridy++;
		gbc.gridx = 0;
		gbc.gridwidth = 2;
		formPanel.add(showDebugOptionsCheck, gbc);

		gbc.gridy++;
		gbc.gridx = 0;
		gbc.gridwidth = 2;
		formPanel.add(autoSaveCheck, gbc);

		gbc.gridy++;
		gbc.gridx = 0;
		gbc.gridwidth = 2;
		formPanel.add(alwaysOnTopCheck, gbc);

		gbc.gridy++;
		gbc.gridwidth = 1;
		gbc.anchor = GridBagConstraints.EAST;
		gbc.gridx = 1;
		JButton saveButton = new JButton("Save");
		ThemedButtons.apply(saveButton, ButtonStyle.DEFAULT);
		saveButton.addActionListener(e -> handleSave());
		formPanel.add(saveButton, gbc);

		return formPanel;
	}

	private int getConfiguredIconSize() {
		AppSettings settings = configManager.getSettings();
		if (settings != null && settings.getUi() != null && settings.getUi().getIconSize() > 0) {
			return settings.getUi().getIconSize();
		}
		return 45;
	}

	private void handleSave() {
		int requestedSize = ((Number) sizeSpinner.getValue()).intValue();
		int resolvedSize = resolveNearestSize(requestedSize);
		boolean adjusted = resolvedSize != requestedSize;

		if (adjusted) {
			sizeSpinner.setValue(resolvedSize);
		}

		statusLabel.setForeground(UiColorPalette.TEXT_MUTED);

		AppSettings settings = configManager.getSettings();
		if (settings.getUi() == null) {
			settings.setUi(new AppSettings.UiSettings());
		}
		settings.getUi().setIconSize(resolvedSize);
		settings.getUi().setPresetManagerAlwaysOnTop(alwaysOnTopCheck.isSelected());
		settings.getUi().setBlinkCurrentAbilities(blinkCurrentCheck.isSelected());
		settings.getUi().setAbilityIndicatorEnabled(abilityIndicatorEnabledCheck.isSelected());
		settings.getUi().setAbilityIndicatorLoopMs(((Number) abilityIndicatorLoopMsSpinner.getValue()).longValue());
		settings.getUi().setChanneledWaitTooltipsEnabled(channeledWaitTooltipsCheck.isSelected());
		settings.getUi().setShowDebugOptions(showDebugOptionsCheck.isSelected());

		if (settings.getRotation() == null) {
			settings.setRotation(new AppSettings.RotationSettings());
		}
		settings.getRotation().setAutoSaveOnSwitch(autoSaveCheck.isSelected());

		try {
			configManager.saveSettings();
			statusLabel.setForeground(UiColorPalette.TEXT_SUCCESS);
			String sizeMessage = adjusted
					? "Saved icon size (adjusted to nearest): " + resolvedSize + " px"
					: "Saved icon size: " + resolvedSize + " px";
			String message = sizeMessage
					+ "; auto-save rotations " + rotationStateLabel()
					+ "; always-on-top " + alwaysOnTopStateLabel()
					+ "; blinking current highlights " + blinkStateLabel()
					+ "; ability indicator animation " + abilityIndicatorStateLabel()
					+ " (" + abilityIndicatorLoopMsLabel() + "ms)"
					+ "; channel wait tooltips " + channeledWaitTooltipsStateLabel()
					+ "; debug options " + showDebugOptionsStateLabel();
			statusLabel.setText(message);
		} catch (IOException ex) {
			statusLabel.setForeground(UiColorPalette.TEXT_DANGER);
			statusLabel.setText("Failed to save settings: " + ex.getMessage());
		}
	}

	private int resolveNearestSize(int requestedSize) {
		int nearest = supportedSizes[0];
		int minDiff = Math.abs(requestedSize - nearest);

		for (int size : supportedSizes) {
			int diff = Math.abs(requestedSize - size);
			if (diff < minDiff) {
				minDiff = diff;
				nearest = size;
			}
		}

		return nearest;
	}

	private boolean resolveAutoSavePreference() {
		AppSettings settings = configManager.getSettings();
		if (settings != null && settings.getRotation() != null) {
			return settings.getRotation().isAutoSaveOnSwitch();
		}
		return false;
	}

	private boolean resolveAlwaysOnTopPreference() {
		AppSettings settings = configManager.getSettings();
		if (settings != null && settings.getUi() != null) {
			return settings.getUi().isPresetManagerAlwaysOnTop();
		}
		return false;
	}

	private boolean resolveBlinkPreference() {
		AppSettings settings = configManager.getSettings();
		if (settings != null && settings.getUi() != null) {
			return settings.getUi().isBlinkCurrentAbilities();
		}
		return false;
	}

	private boolean resolveShowDebugOptionsPreference() {
		AppSettings settings = configManager.getSettings();
		if (settings != null && settings.getUi() != null) {
			return settings.getUi().isShowDebugOptions();
		}
		return false;
	}

	private boolean resolveAbilityIndicatorEnabledPreference() {
		AppSettings settings = configManager.getSettings();
		if (settings != null && settings.getUi() != null) {
			return settings.getUi().isAbilityIndicatorEnabled();
		}
		return true;
	}

	private long resolveAbilityIndicatorLoopMsPreference() {
		AppSettings settings = configManager.getSettings();
		if (settings != null && settings.getUi() != null) {
			return settings.getUi().getAbilityIndicatorLoopMs();
		}
		return 600;
	}

	private boolean resolveChanneledWaitTooltipsPreference() {
		AppSettings settings = configManager.getSettings();
		if (settings != null && settings.getUi() != null) {
			return settings.getUi().isChanneledWaitTooltipsEnabled();
		}
		return true;
	}

	private String rotationStateLabel() {
		return autoSaveCheck.isSelected() ? "enabled" : "disabled";
	}

	private String alwaysOnTopStateLabel() {
		return alwaysOnTopCheck.isSelected() ? "enabled" : "disabled";
	}

	private String blinkStateLabel() {
		return blinkCurrentCheck.isSelected() ? "enabled" : "disabled";
	}

	private String abilityIndicatorStateLabel() {
		return abilityIndicatorEnabledCheck.isSelected() ? "enabled" : "disabled";
	}

	private String abilityIndicatorLoopMsLabel() {
		return String.valueOf(((Number) abilityIndicatorLoopMsSpinner.getValue()).longValue());
	}

		private String channeledWaitTooltipsStateLabel() {
			return channeledWaitTooltipsCheck.isSelected() ? "enabled" : "disabled";
		}

		private String showDebugOptionsStateLabel() {
			return showDebugOptionsCheck.isSelected() ? "shown" : "hidden";
		}
	}
