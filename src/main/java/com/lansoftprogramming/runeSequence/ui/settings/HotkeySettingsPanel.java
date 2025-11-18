package com.lansoftprogramming.runeSequence.ui.settings;

import com.lansoftprogramming.runeSequence.infrastructure.config.AppSettings;
import com.lansoftprogramming.runeSequence.infrastructure.config.ConfigManager;

import javax.swing.*;
import javax.swing.border.EmptyBorder;
import java.awt.*;
import java.awt.event.InputEvent;
import java.awt.event.KeyEvent;
import java.io.IOException;
import java.util.*;
import java.util.List;
import java.util.function.Consumer;
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
	private final KeyCapturePopup keyCapturePopup = new KeyCapturePopup();

	private static final Map<String, String> ACTION_LABELS = buildActionLabels();
	private static final Color INVALID_FIELD_BACKGROUND = new Color(255, 230, 230);
	private static final Set<String> MODIFIER_TOKENS = Set.of("CTRL", "CONTROL", "SHIFT", "ALT", "META");
	private static final Set<Integer> MODIFIER_KEY_CODES = Set.of(
			KeyEvent.VK_CONTROL,
			KeyEvent.VK_SHIFT,
			KeyEvent.VK_ALT,
			KeyEvent.VK_META,
			KeyEvent.VK_ALT_GRAPH);

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
				formPanel.add(row.customEditor(), gbc);

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
		JLabel helper = new JLabel("Tip: click Capture to record a combo; separate multiple bindings with commas.");
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
			int modifierCount = tokens.size() - nonModifierCount;
			if (modifierCount == 0) {
				return new ValidationResult(Collections.emptyList(),
						actionLabel + " hotkey \"" + trimmed + "\" must include at least one modifier key.");
			}
			if (modifierCount > 2) {
				return new ValidationResult(Collections.emptyList(),
						actionLabel + " hotkey \"" + trimmed + "\" supports at most two modifiers.");
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

	private static boolean isModifierKeyCode(int keyCode) {
		return MODIFIER_KEY_CODES.contains(keyCode);
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

	private static String normalizeKeyToken(String keyText) {
		if (keyText == null) {
			return null;
		}
		String trimmed = keyText.trim();
		return trimmed.isEmpty() ? null : trimmed;
	}

	private final class CustomBindingEditor extends JPanel {
		private final JTextField field;
		private final JButton captureButton;

		private CustomBindingEditor(AppSettings.HotkeySettings.Binding binding) {
			super(new BorderLayout(4, 0));
			this.field = new JTextField(formatBinding(binding.getUser()));
			this.field.setColumns(20);
			this.field.setEditable(false);
			this.field.setFocusable(false);
			this.captureButton = new JButton("Capture");
			captureButton.setToolTipText("Open a menu to record a keybind");
			captureButton.setMargin(new Insets(2, 8, 2, 8));
			captureButton.setFocusable(false);
			captureButton.addActionListener(e -> beginCapture());
			add(field, BorderLayout.CENTER);
			add(captureButton, BorderLayout.EAST);
		}

		private void beginCapture() {
			if (!captureButton.isEnabled()) {
				return;
			}
			keyCapturePopup.open(captureButton, tokens -> applyCaptured(tokens));
		}

		private void applyCaptured(List<String> tokens) {
			String formatted = formatBinding(List.of(tokens));
			field.setText(formatted);
			field.requestFocusInWindow();
			field.selectAll();
			markFieldValidity(field, true);
		}

		private void setEditorEnabled(boolean enabled) {
			field.setEnabled(enabled);
			captureButton.setEnabled(enabled);
		}

		private JTextField field() {
			return field;
		}
	}

	private final class KeyCapturePopup extends JPopupMenu implements KeyEventDispatcher {
		private final JLabel infoLabel = new JLabel("Press a key combination (ESC to cancel).");
		private Consumer<List<String>> completion;
		private boolean listening;

		private KeyCapturePopup() {
			infoLabel.setBorder(new EmptyBorder(6, 12, 6, 12));
			add(infoLabel);
		}

		void open(Component invoker, Consumer<List<String>> onComplete) {
			if (invoker == null) {
				return;
			}
			if (isVisible()) {
				setVisible(false);
			}
			this.completion = onComplete;
			infoLabel.setForeground(UIManager.getColor("Label.foreground"));
			infoLabel.setText("Press a key combination (ESC to cancel).");
			show(invoker, 0, invoker.getHeight());
			KeyboardFocusManager.getCurrentKeyboardFocusManager().addKeyEventDispatcher(this);
			listening = true;
		}

		@Override
		public void setVisible(boolean visible) {
			super.setVisible(visible);
			if (!visible) {
				stopListening();
			}
		}

		private void stopListening() {
			if (listening) {
				KeyboardFocusManager.getCurrentKeyboardFocusManager().removeKeyEventDispatcher(this);
				listening = false;
			}
			completion = null;
		}

		@Override
		public boolean dispatchKeyEvent(KeyEvent e) {
			if (!isVisible()) {
				return false;
			}
			if (e.getID() != KeyEvent.KEY_PRESSED) {
				return true;
			}
			if (e.getKeyCode() == KeyEvent.VK_ESCAPE) {
				setVisible(false);
				return true;
			}
			if (isModifierKeyCode(e.getKeyCode())) {
				infoLabel.setText("Finish with a non-modifier key.");
				return true;
			}
			List<String> tokens = translateEvent(e);
			if (tokens == null) {
				infoLabel.setText("Use 1-2 modifiers plus a final key.");
				Toolkit.getDefaultToolkit().beep();
				return true;
			}
			if (completion != null) {
				completion.accept(tokens);
			}
			setVisible(false);
			return true;
		}

		private List<String> translateEvent(KeyEvent event) {
			int modifiers = event.getModifiersEx();
			List<String> tokens = new ArrayList<>();
			addModifierToken(modifiers, InputEvent.CTRL_DOWN_MASK, "CTRL", tokens);
			addModifierToken(modifiers, InputEvent.ALT_DOWN_MASK, "ALT", tokens);
			addModifierToken(modifiers, InputEvent.SHIFT_DOWN_MASK, "SHIFT", tokens);
			addModifierToken(modifiers, InputEvent.META_DOWN_MASK, "META", tokens);
			if ((modifiers & InputEvent.ALT_GRAPH_DOWN_MASK) != 0 && !tokens.contains("ALT")) {
				tokens.add("ALT");
			}
			if (tokens.isEmpty() || tokens.size() > 2) {
				return null;
			}
			String keyToken = normalizeKeyToken(KeyEvent.getKeyText(event.getKeyCode()));
			if (keyToken == null) {
				return null;
			}
			List<String> result = new ArrayList<>(tokens);
			result.add(keyToken);
			return result;
		}

		private void addModifierToken(int modifiers, int mask, String label, List<String> tokens) {
			if ((modifiers & mask) != 0 && !tokens.contains(label)) {
				tokens.add(label);
			}
		}
	}

	private final class Row {
		private final AppSettings.HotkeySettings.Binding binding;
		private final JLabel actionLabel;
		private final JLabel defaultLabel;
		private final CustomBindingEditor customEditor;
		private final JCheckBox enableCheck;

		private Row(AppSettings.HotkeySettings.Binding binding) {
			this.binding = binding;
			this.actionLabel = new JLabel(resolveActionLabel(binding.getAction()));
			this.defaultLabel = new JLabel(renderDefault(binding));
			this.customEditor = new CustomBindingEditor(binding);
			this.enableCheck = createCheck(binding);
			enableCheck.addActionListener(e -> customEditor.setEditorEnabled(enableCheck.isSelected()));
			customEditor.setEditorEnabled(binding.isUserEnabled());
		}

		private static String renderDefault(AppSettings.HotkeySettings.Binding binding) {
			String formatted = formatBinding(binding.getGlobal());
			return formatted.isBlank() ? "Not set" : formatted;
		}

		private static JCheckBox createCheck(AppSettings.HotkeySettings.Binding binding) {
			JCheckBox checkBox = new JCheckBox();
			checkBox.setSelected(binding.isUserEnabled());
			return checkBox;
		}

		private AppSettings.HotkeySettings.Binding binding() {
			return binding;
		}

		private JLabel actionLabel() {
			return actionLabel;
		}

		private JLabel defaultLabel() {
			return defaultLabel;
		}

		private JTextField customField() {
			return customEditor.field();
		}

		private CustomBindingEditor customEditor() {
			return customEditor;
		}

		private JCheckBox enableCheck() {
			return enableCheck;
		}
	}
}
