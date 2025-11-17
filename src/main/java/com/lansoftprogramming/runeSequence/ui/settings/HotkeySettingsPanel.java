package com.lansoftprogramming.runeSequence.ui.settings;

import com.lansoftprogramming.runeSequence.infrastructure.config.AppSettings;
import com.lansoftprogramming.runeSequence.infrastructure.config.ConfigManager;

import javax.swing.*;
import javax.swing.border.EmptyBorder;
import java.awt.*;
import java.io.IOException;
import java.util.*;
import java.util.List;
import java.util.stream.Collectors;

/**
 * Allows users to view the default hotkey bindings and provide custom bindings that
 * are persisted to {@link AppSettings.HotkeySettings}.
 */
public class HotkeySettingsPanel extends JPanel {

	private final ConfigManager configManager;
	private final List<Row> rows = new ArrayList<>();
	private final JLabel statusLabel = new JLabel(" ");
	private final Color defaultFieldBackground;

	private static final Map<String, String> ACTION_LABELS = buildActionLabels();
	private static final Color INVALID_FIELD_BACKGROUND = new Color(255, 230, 230);
	private static final Set<String> MODIFIER_TOKENS = Set.of("CTRL", "CONTROL", "SHIFT", "ALT", "META");

	public HotkeySettingsPanel(ConfigManager configManager) {
		this.configManager = Objects.requireNonNull(configManager, "configManager");
		Color uiColor = UIManager.getColor("TextField.background");
		this.defaultFieldBackground = uiColor != null ? uiColor : Color.WHITE;
		setLayout(new BorderLayout());
		setBorder(new EmptyBorder(15, 15, 15, 15));

		add(buildScrollableForm(), BorderLayout.CENTER);
		add(buildFooter(), BorderLayout.SOUTH);
	}

	private JScrollPane buildScrollableForm() {
		JPanel formPanel = new JPanel(new GridBagLayout());
		GridBagConstraints gbc = new GridBagConstraints();
		gbc.insets = new Insets(4, 4, 4, 4);
		gbc.gridy = 0;
		gbc.anchor = GridBagConstraints.WEST;

		// Header row
		gbc.gridx = 0;
		formPanel.add(new JLabel("Action"), gbc);
		gbc.gridx = 1;
		formPanel.add(new JLabel("Default binding(s)"), gbc);
		gbc.gridx = 2;
		formPanel.add(new JLabel("Custom binding(s)"), gbc);
		gbc.gridx = 3;
		formPanel.add(new JLabel("Use custom"), gbc);

		AppSettings.HotkeySettings hotkeySettings = ensureHotkeySettings();
		List<AppSettings.HotkeySettings.Binding> bindings = hotkeySettings.getBindings();
		if (bindings == null) {
			bindings = new ArrayList<>();
			hotkeySettings.setBindings(bindings);
		}

		if (bindings.isEmpty()) {
			gbc.gridy++;
			gbc.gridx = 0;
			gbc.gridwidth = 4;
			JLabel emptyLabel = new JLabel("No hotkey bindings were found in the current settings.");
			emptyLabel.setForeground(Color.GRAY);
			formPanel.add(emptyLabel, gbc);
		} else {
			for (AppSettings.HotkeySettings.Binding binding : bindings) {
				Row row = new Row(binding);
				rows.add(row);

				gbc.gridy++;

				gbc.gridx = 0;
				gbc.gridwidth = 1;
				formPanel.add(row.actionLabel(), gbc);

				gbc.gridx = 1;
				formPanel.add(row.defaultLabel(), gbc);

				gbc.gridx = 2;
				gbc.weightx = 1.0;
				gbc.fill = GridBagConstraints.HORIZONTAL;
				formPanel.add(row.customField(), gbc);

				gbc.gridx = 3;
				gbc.weightx = 0;
				gbc.fill = GridBagConstraints.NONE;
				formPanel.add(row.enableCheck(), gbc);
				markFieldValidity(row.customField(), true);
			}
		}

		gbc.gridy++;
		gbc.gridx = 0;
		gbc.gridwidth = 4;
		JLabel helper = new JLabel("Tip: separate multiple bindings with commas, e.g. \"Ctrl+F1, Alt+P\".");
		helper.setFont(helper.getFont().deriveFont(Font.ITALIC, helper.getFont().getSize() - 1f));
		formPanel.add(helper, gbc);

		JScrollPane scrollPane = new JScrollPane(formPanel);
		scrollPane.setBorder(BorderFactory.createEmptyBorder());
		return scrollPane;
	}

	private JPanel buildFooter() {
		JPanel footer = new JPanel(new BorderLayout());
		statusLabel.setForeground(Color.GRAY);
		footer.add(statusLabel, BorderLayout.CENTER);

		JButton saveButton = new JButton("Save hotkeys");
		saveButton.addActionListener(e -> handleSave());
		JPanel buttonPanel = new JPanel(new FlowLayout(FlowLayout.RIGHT, 0, 0));
		buttonPanel.add(saveButton);
		footer.add(buttonPanel, BorderLayout.EAST);
		return footer;
	}

	private void handleSave() {
		statusLabel.setForeground(Color.GRAY);
		AppSettings settings = configManager.getSettings();
		if (settings == null) {
			statusLabel.setForeground(Color.RED);
			statusLabel.setText("Settings are not loaded.");
			return;
		}

		boolean hasError = false;
		String firstError = null;

		for (Row row : rows) {
			AppSettings.HotkeySettings.Binding binding = row.binding();
			boolean enabled = row.enableCheck().isSelected();
			binding.setUserEnabled(enabled);

			ValidationResult result = parseCustomBindings(row.customField().getText(), row.actionLabel().getText());
			boolean valid = result.errorMessage() == null;
			markFieldValidity(row.customField(), valid);

			if (!valid) {
				hasError = true;
				if (firstError == null) {
					firstError = result.errorMessage();
				}
				continue;
			}

			binding.setUser(result.bindings());
		}

		if (hasError) {
			statusLabel.setForeground(Color.RED);
			statusLabel.setText(firstError);
			return;
		}

		try {
			configManager.saveSettings();
			statusLabel.setForeground(new Color(0, 128, 0));
			statusLabel.setText("Hotkey settings saved.");
		} catch (IOException ex) {
			statusLabel.setForeground(Color.RED);
			statusLabel.setText("Failed to save hotkeys: " + ex.getMessage());
		}
	}

	private AppSettings.HotkeySettings ensureHotkeySettings() {
		AppSettings settings = configManager.getSettings();
		if (settings.getHotkeys() == null) {
			settings.setHotkeys(new AppSettings.HotkeySettings());
		}
		return settings.getHotkeys();
	}

	private ValidationResult parseCustomBindings(String rawInput, String actionLabel) {
		if (rawInput == null || rawInput.isBlank()) {
			return new ValidationResult(Collections.emptyList(), null);
		}
		String[] sequences = rawInput.split("[,\\n]");
		List<List<String>> parsed = new ArrayList<>();
		for (String seq : sequences) {
			String trimmed = seq.trim();
			if (trimmed.isEmpty()) {
				continue;
			}
			List<String> tokens = parseSingleSequence(trimmed);
			if (tokens.isEmpty()) {
				continue;
			}

			int nonModifierCount = countNonModifierTokens(tokens);
			if (nonModifierCount == 0) {
				return new ValidationResult(Collections.emptyList(),
						actionLabel + " hotkey \"" + trimmed + "\" must include a key (e.g. F1, A).");
			}
			if (nonModifierCount > 1) {
				return new ValidationResult(Collections.emptyList(),
						actionLabel + " hotkey \"" + trimmed + "\" can only include one non-modifier key.");
			}
			if (isModifierToken(tokens.get(tokens.size() - 1))) {
				return new ValidationResult(Collections.emptyList(),
						actionLabel + " hotkey \"" + trimmed + "\" must end with a key.");
			}

			parsed.add(tokens);
		}
		return new ValidationResult(parsed.isEmpty() ? Collections.emptyList() : parsed, null);
	}

	private static List<String> parseSingleSequence(String sequence) {
		if (sequence == null) {
			return Collections.emptyList();
		}
		String trimmed = sequence.trim();
		if (trimmed.isEmpty()) {
			return Collections.emptyList();
		}
		String[] pieces = trimmed.split("\\+");
		List<String> tokens = new ArrayList<>();
		for (String piece : pieces) {
			String token = piece.trim();
			if (!token.isEmpty()) {
				tokens.add(token);
			}
		}
		return tokens;
	}

	private static String formatBinding(List<List<String>> bindings) {
		if (bindings == null || bindings.isEmpty()) {
			return "";
		}
		return bindings.stream()
				.map(seq -> seq.stream()
						.filter(token -> token != null && !token.isBlank())
						.map(String::trim)
						.collect(Collectors.joining(" + ")))
				.filter(s -> !s.isBlank())
				.collect(Collectors.joining(", "));
	}

	private static int countNonModifierTokens(List<String> tokens) {
		int count = 0;
		for (String token : tokens) {
			if (!isModifierToken(token)) {
				count++;
			}
		}
		return count;
	}

	private static boolean isModifierToken(String token) {
		if (token == null) {
			return false;
		}
		String normalized = token.trim().toUpperCase(Locale.ROOT);
		return MODIFIER_TOKENS.contains(normalized);
	}

	private void markFieldValidity(JTextField field, boolean valid) {
		field.setBackground(valid ? defaultFieldBackground : INVALID_FIELD_BACKGROUND);
	}

	private static String resolveActionLabel(String action) {
		if (action == null) {
			return "Unknown action";
		}
		return ACTION_LABELS.getOrDefault(action, action);
	}

	private static Map<String, String> buildActionLabels() {
		Map<String, String> labels = new LinkedHashMap<>();
		labels.put("detection.start", "Start detection");
		labels.put("detection.restart", "Restart detection");
		return labels;
	}

	private record ValidationResult(List<List<String>> bindings, String errorMessage) {
	}

	private record Row(AppSettings.HotkeySettings.Binding binding,
	                   JLabel actionLabel,
	                   JLabel defaultLabel,
	                   JTextField customField,
	                   JCheckBox enableCheck) {

		private Row(AppSettings.HotkeySettings.Binding binding) {
			this(binding,
					new JLabel(resolveActionLabel(binding.getAction())),
					new JLabel(renderDefault(binding)),
					createField(binding),
					createCheck(binding));
			enableCheck.addActionListener(e -> customField.setEnabled(enableCheck.isSelected()));
			customField.setEnabled(binding.isUserEnabled());
		}

		private static String renderDefault(AppSettings.HotkeySettings.Binding binding) {
			String formatted = formatBinding(binding.getGlobal());
			return formatted.isBlank() ? "Not set" : formatted;
		}

		private static JTextField createField(AppSettings.HotkeySettings.Binding binding) {
			JTextField field = new JTextField(formatBinding(binding.getUser()));
			field.setColumns(20);
			return field;
		}

		private static JCheckBox createCheck(AppSettings.HotkeySettings.Binding binding) {
			JCheckBox checkBox = new JCheckBox();
			checkBox.setSelected(binding.isUserEnabled());
			return checkBox;
		}
	}
}