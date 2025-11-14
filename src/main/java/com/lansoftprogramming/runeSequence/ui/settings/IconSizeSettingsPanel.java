package com.lansoftprogramming.runeSequence.ui.settings;

import com.lansoftprogramming.runeSequence.infrastructure.config.AppSettings;
import com.lansoftprogramming.runeSequence.infrastructure.config.ConfigManager;
import com.lansoftprogramming.runeSequence.infrastructure.config.ScalingConverter;

import javax.swing.*;
import javax.swing.border.EmptyBorder;
import java.awt.*;
import java.io.IOException;
import java.util.Arrays;

public class IconSizeSettingsPanel extends JPanel {
	private final ConfigManager configManager;
	private final int[] supportedSizes;
	private final JSpinner sizeSpinner;
	private final JLabel statusLabel;

	public IconSizeSettingsPanel(ConfigManager configManager) {
		this.configManager = configManager;
		this.supportedSizes = ScalingConverter.getAllSizes();
		if (supportedSizes.length == 0) {
			throw new IllegalStateException("No supported icon sizes configured.");
		}

		setLayout(new BorderLayout());
		setBorder(new EmptyBorder(15, 15, 15, 15));

		int currentSize = resolveNearestSize(getConfiguredIconSize());
		sizeSpinner = new JSpinner(new SpinnerNumberModel(
				currentSize,
				supportedSizes[0],
				supportedSizes[supportedSizes.length - 1],
				1
		));

		statusLabel = new JLabel(" ");
		statusLabel.setForeground(Color.GRAY);

		add(createFormPanel(), BorderLayout.CENTER);
		add(statusLabel, BorderLayout.SOUTH);
	}

	private JPanel createFormPanel() {
		JPanel formPanel = new JPanel();
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
		gbc.gridwidth = 1;
		gbc.anchor = GridBagConstraints.EAST;
		gbc.gridx = 1;
		JButton saveButton = new JButton("Save");
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

		statusLabel.setForeground(Color.GRAY);

		AppSettings settings = configManager.getSettings();
		if (settings.getUi() == null) {
			settings.setUi(new AppSettings.UiSettings());
		}
		settings.getUi().setIconSize(resolvedSize);

		try {
			configManager.saveSettings();
			statusLabel.setForeground(new Color(0, 128, 0));
			if (adjusted) {
				statusLabel.setText("Saved icon size (adjusted to nearest): " + resolvedSize + " px");
			} else {
				statusLabel.setText("Saved icon size: " + resolvedSize + " px");
			}
		} catch (IOException ex) {
			statusLabel.setForeground(Color.RED);
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
}
