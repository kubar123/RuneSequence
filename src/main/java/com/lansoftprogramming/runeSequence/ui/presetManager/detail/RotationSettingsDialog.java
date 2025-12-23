package com.lansoftprogramming.runeSequence.ui.presetManager.detail;

import com.lansoftprogramming.runeSequence.infrastructure.config.RotationConfig;
import com.lansoftprogramming.runeSequence.infrastructure.config.dto.PresetAbilitySettings;
import com.lansoftprogramming.runeSequence.infrastructure.config.dto.PresetRotationDefaults;
import com.lansoftprogramming.runeSequence.ui.theme.ButtonStyle;
import com.lansoftprogramming.runeSequence.ui.theme.ThemedButtons;
import com.lansoftprogramming.runeSequence.ui.theme.UiColorPalette;

import javax.swing.*;
import javax.swing.border.EmptyBorder;
import java.awt.*;

/**
 * Dialog for rotation-wide ability defaults. These settings apply as defaults to all abilities
 * in the rotation unless overridden per-instance.
 */
public class RotationSettingsDialog extends JDialog {
	private final JCheckBox ezkOverrideCheckbox;
	private final JCheckBox ezkEnabledCheckbox;
	private final JCheckBox alwaysGBargeOverrideCheckbox;
	private final JCheckBox alwaysGBargeEnabledCheckbox;
	private final RotationConfig.PresetData presetData;

	public RotationSettingsDialog(Window owner, String rotationName, RotationConfig.PresetData presetData) {
		super(owner, buildTitle(rotationName), ModalityType.APPLICATION_MODAL);
		this.presetData = presetData;

		ezkOverrideCheckbox = new JCheckBox("Override");
		ezkEnabledCheckbox = new JCheckBox("Enabled");
		alwaysGBargeOverrideCheckbox = new JCheckBox("Override");
		alwaysGBargeEnabledCheckbox = new JCheckBox("Enabled");

		initLayout(rotationName);
		loadFromDefaults(snapshotDefaults(presetData));
		pack();
		setMinimumSize(new Dimension(420, getPreferredSize().height));
		setLocationRelativeTo(owner);
	}

	private static String buildTitle(String rotationName) {
		String name = rotationName != null && !rotationName.isBlank() ? rotationName.trim() : "Unnamed Rotation";
		return "Rotation Settings \u2013 " + name;
	}

	private void initLayout(String rotationName) {
		JPanel content = new JPanel();
		content.setLayout(new BorderLayout(10, 10));
		content.setBorder(new EmptyBorder(12, 12, 12, 12));

		JLabel scopeLabel = new JLabel("These settings apply as defaults to all abilities in this rotation.");
		scopeLabel.setForeground(UiColorPalette.TEXT_MUTED);
		content.add(scopeLabel, BorderLayout.NORTH);

		JPanel formPanel = new JPanel();
		formPanel.setLayout(new GridBagLayout());
		formPanel.setBorder(UiColorPalette.CARD_BORDER);
		formPanel.setBackground(UIManager.getColor("Panel.background"));

		GridBagConstraints gbc = new GridBagConstraints();
		gbc.gridx = 0;
		gbc.gridy = 0;
		gbc.anchor = GridBagConstraints.WEST;
		gbc.insets = new Insets(8, 10, 8, 10);
		gbc.weightx = 1.0;
		gbc.fill = GridBagConstraints.HORIZONTAL;

		formPanel.add(createRow("EZK", ezkOverrideCheckbox, ezkEnabledCheckbox), gbc);
		gbc.gridy++;
		formPanel.add(createRow("Always GBarge", alwaysGBargeOverrideCheckbox, alwaysGBargeEnabledCheckbox), gbc);

		content.add(formPanel, BorderLayout.CENTER);

		JPanel buttonPanel = new JPanel(new FlowLayout(FlowLayout.RIGHT, 8, 0));
		JButton resetButton = new JButton("Reset to Global Defaults");
		JButton cancelButton = new JButton("Cancel");
		JButton okButton = new JButton("OK");
		ThemedButtons.apply(resetButton, ButtonStyle.DEFAULT);
		ThemedButtons.apply(cancelButton, ButtonStyle.DEFAULT);
		ThemedButtons.apply(okButton, ButtonStyle.DEFAULT);

		resetButton.addActionListener(e -> resetToGlobal());
		cancelButton.addActionListener(e -> dispose());
		okButton.addActionListener(e -> {
			applyToPreset();
			dispose();
		});

		buttonPanel.add(resetButton);
		buttonPanel.add(cancelButton);
		buttonPanel.add(okButton);

		content.add(buttonPanel, BorderLayout.SOUTH);
		setContentPane(content);

		attachOverrideHandlers(ezkOverrideCheckbox, ezkEnabledCheckbox);
		attachOverrideHandlers(alwaysGBargeOverrideCheckbox, alwaysGBargeEnabledCheckbox);
	}

	private JPanel createRow(String label, JCheckBox overrideCheckbox, JCheckBox enabledCheckbox) {
		JPanel row = new JPanel(new FlowLayout(FlowLayout.LEFT, 10, 0));
		row.setOpaque(false);

		JLabel nameLabel = new JLabel(label);
		nameLabel.setPreferredSize(new Dimension(160, nameLabel.getPreferredSize().height));

		row.add(nameLabel);
		row.add(overrideCheckbox);
		row.add(enabledCheckbox);
		return row;
	}

	private void attachOverrideHandlers(JCheckBox overrideCheckbox, JCheckBox enabledCheckbox) {
		enabledCheckbox.setEnabled(overrideCheckbox.isSelected());
		overrideCheckbox.addActionListener(e -> enabledCheckbox.setEnabled(overrideCheckbox.isSelected()));
	}

	private PresetRotationDefaults snapshotDefaults(RotationConfig.PresetData presetData) {
		PresetAbilitySettings abilitySettings = presetData != null ? presetData.getAbilitySettings() : null;
		PresetRotationDefaults defaults = abilitySettings != null ? abilitySettings.getRotationDefaults() : null;
		if (defaults == null) {
			return null;
		}
		PresetRotationDefaults out = new PresetRotationDefaults();
		out.setEzk(defaults.getEzk());
		out.setAlwaysGBarge(defaults.getAlwaysGBarge());
		return out.isEmpty() ? null : out;
	}

	private void loadFromDefaults(PresetRotationDefaults defaults) {
		Boolean ezk = defaults != null ? defaults.getEzk() : null;
		ezkOverrideCheckbox.setSelected(ezk != null);
		ezkEnabledCheckbox.setSelected(Boolean.TRUE.equals(ezk));
		ezkEnabledCheckbox.setEnabled(ezk != null);

		Boolean alwaysGBarge = defaults != null ? defaults.getAlwaysGBarge() : null;
		alwaysGBargeOverrideCheckbox.setSelected(alwaysGBarge != null);
		alwaysGBargeEnabledCheckbox.setSelected(Boolean.TRUE.equals(alwaysGBarge));
		alwaysGBargeEnabledCheckbox.setEnabled(alwaysGBarge != null);
	}

	private void applyToPreset() {
		if (presetData == null) {
			return;
		}

		PresetRotationDefaults defaults = buildDefaultsFromForm();

		PresetAbilitySettings abilitySettings = presetData.getAbilitySettings();
		if (abilitySettings == null) {
			abilitySettings = new PresetAbilitySettings();
		}
		abilitySettings.setRotationDefaults(defaults);
		presetData.setAbilitySettings(abilitySettings.isEmpty() ? null : abilitySettings);
	}

	private void resetToGlobal() {
		loadFromDefaults(null);
	}

	private PresetRotationDefaults buildDefaultsFromForm() {
		PresetRotationDefaults defaults = new PresetRotationDefaults();
		if (ezkOverrideCheckbox.isSelected()) {
			defaults.setEzk(ezkEnabledCheckbox.isSelected());
		}
		if (alwaysGBargeOverrideCheckbox.isSelected()) {
			defaults.setAlwaysGBarge(alwaysGBargeEnabledCheckbox.isSelected());
		}
		return defaults.isEmpty() ? null : defaults;
	}
}
