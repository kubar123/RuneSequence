package com.lansoftprogramming.runeSequence.ui.presetManager.detail;

import com.lansoftprogramming.runeSequence.core.sequence.model.AbilitySettingsOverrides;
import com.lansoftprogramming.runeSequence.core.sequence.model.AbilityValueSanitizers;
import com.lansoftprogramming.runeSequence.core.sequence.model.EffectiveAbilityConfig;
import com.lansoftprogramming.runeSequence.core.validation.FilenameValidators;
import com.lansoftprogramming.runeSequence.ui.shared.model.AbilityItem;
import com.lansoftprogramming.runeSequence.ui.theme.*;

import javax.swing.*;
import javax.swing.border.EmptyBorder;
import java.awt.*;
import java.util.IdentityHashMap;
import java.util.Map;

class AbilityPropertiesDialog extends JDialog {

	record Result(AbilitySettingsOverrides overrides,
	              boolean applyTriggersGcdToAll,
	              boolean applyCastDurationToAll,
	              boolean applyCooldownToAll,
	              boolean applyDetectionThresholdToAll,
	              boolean applyMaskToAll) {
		boolean hasAnyApplyToAll() {
			return applyTriggersGcdToAll || applyCastDurationToAll || applyCooldownToAll || applyDetectionThresholdToAll || applyMaskToAll;
		}
	}

	interface MaskValidator {
		ValidationResult validate(String maskKey);
	}

	record ValidationResult(boolean isValid, String message) {
		static ValidationResult valid() {
			return new ValidationResult(true, null);
		}

		static ValidationResult invalid(String message) {
			return new ValidationResult(false, message);
		}
	}

	private Result result;

	private final JCheckBox triggersGcdOverride;
	private final JCheckBox triggersGcdValue;
	private final JCheckBox triggersGcdApplyAll;
	private final JLabel triggersGcdEffective;

	private final JCheckBox castDurationOverride;
	private final JSpinner castDurationValue;
	private final JCheckBox castDurationApplyAll;
	private final JLabel castDurationEffective;

	private final JCheckBox cooldownOverride;
	private final JSpinner cooldownValue;
	private final JCheckBox cooldownApplyAll;
	private final JLabel cooldownEffective;

	private final JCheckBox thresholdOverride;
	private final JSpinner thresholdValue;
	private final JCheckBox thresholdApplyAll;
	private final JLabel thresholdEffective;

	private final JCheckBox maskOverride;
	private final JTextField maskValue;
	private final ThemedTextBoxPanel maskValuePanel;
	private final JCheckBox maskApplyAll;
	private final JLabel maskEffective;

	private final JLabel validationLabel;

	private final EffectiveAbilityConfig baseAbility;
	private final AbilitySettingsOverrides rotationWideOverrides;
	private final MaskValidator maskValidator;
	private final Map<JComponent, Color> defaultBackgrounds = new IdentityHashMap<>();

	AbilityPropertiesDialog(Window owner,
	                        AbilityItem item,
	                        EffectiveAbilityConfig baseAbility,
	                        AbilitySettingsOverrides rotationWideOverrides,
	                        AbilitySettingsOverrides currentOverrides,
	                        MaskValidator maskValidator) {
		super(owner, buildTitle(item), ModalityType.APPLICATION_MODAL);
		this.baseAbility = baseAbility;
		this.rotationWideOverrides = rotationWideOverrides != null ? rotationWideOverrides : AbilitySettingsOverrides.empty();
		this.maskValidator = maskValidator;

		triggersGcdOverride = new JCheckBox();
		triggersGcdValue = new JCheckBox("Triggers GCD");
		triggersGcdApplyAll = new JCheckBox();
		triggersGcdEffective = new JLabel();

		castDurationOverride = new JCheckBox();
		castDurationValue = new JSpinner(new SpinnerNumberModel(0, 0, (int) Short.MAX_VALUE, 1));
		castDurationApplyAll = new JCheckBox();
		castDurationEffective = new JLabel();

		cooldownOverride = new JCheckBox();
		cooldownValue = new JSpinner(new SpinnerNumberModel(0, 0, (int) Short.MAX_VALUE, 1));
		cooldownApplyAll = new JCheckBox();
		cooldownEffective = new JLabel();

		thresholdOverride = new JCheckBox();
		thresholdValue = new JSpinner(new SpinnerNumberModel(0.0d, 0.0d, 1.0d, 0.01d));
		thresholdApplyAll = new JCheckBox();
		thresholdEffective = new JLabel();

		maskOverride = new JCheckBox();
		maskValue = new JTextField();
		maskValuePanel = ThemedTextBoxes.wrap(maskValue);
		maskApplyAll = new JCheckBox();
		maskEffective = new JLabel();

		validationLabel = new JLabel();
		validationLabel.setForeground(UiColorPalette.TEXT_DANGER);

		rememberDefaultBackground(castDurationValue);
		rememberDefaultBackground(cooldownValue);
		rememberDefaultBackground(thresholdValue);
		rememberDefaultBackground(maskValue);

		initLayout(item);
		loadFromOverrides(currentOverrides);
		pack();
		setMinimumSize(new Dimension(640, getPreferredSize().height));
		setLocationRelativeTo(owner);
	}

	Result getResult() {
		return result;
	}

	private static String buildTitle(AbilityItem item) {
		String name = item != null ? item.getDisplayName() : "Ability";
		return "Ability Properties \u2013 " + name;
	}

	private void initLayout(AbilityItem item) {
		ThemedPanel root = new ThemedPanel(PanelStyle.DIALOG, new BorderLayout());
		ThemedWindowDecorations.applyTitleBar(this);

		JPanel content = new JPanel(new BorderLayout(10, 10));
		content.setOpaque(false);
		content.setBorder(new EmptyBorder(12, 12, 12, 12));

		JPanel header = new JPanel(new FlowLayout(FlowLayout.LEFT, 8, 0));
		header.setOpaque(false);
		if (item != null) {
			header.add(new JLabel(item.getIcon()));
			JLabel name = new JLabel(item.getDisplayName());
			name.setFont(name.getFont().deriveFont(Font.BOLD, 14f));
			header.add(name);
		}
		JPanel northWrap = new JPanel();
		northWrap.setOpaque(false);
		northWrap.setLayout(new BoxLayout(northWrap, BoxLayout.Y_AXIS));
		northWrap.add(header);
		content.add(northWrap, BorderLayout.NORTH);

		JPanel formPanel = new JPanel(new GridBagLayout());
		formPanel.setBorder(UiColorPalette.CARD_BORDER);
		formPanel.setOpaque(false);

		GridBagConstraints headerGbc = new GridBagConstraints();
		headerGbc.gridx = 0;
		headerGbc.gridy = 0;
		headerGbc.anchor = GridBagConstraints.WEST;
		headerGbc.insets = new Insets(6, 10, 6, 10);
		headerGbc.weightx = 0.0;

		formPanel.add(new JLabel("Field"), headerGbc);
		headerGbc.gridx = 1;
		formPanel.add(new JLabel("Override"), headerGbc);
		headerGbc.gridx = 2;
		formPanel.add(new JLabel("Value"), headerGbc);
		headerGbc.gridx = 3;
		headerGbc.weightx = 1.0;
		formPanel.add(new JLabel("Effective"), headerGbc);
		headerGbc.gridx = 4;
		headerGbc.weightx = 0.0;
		JLabel applyHeader = new JLabel("Apply to all");
		applyHeader.setToolTipText("Apply this field change to all instances of the same ability in this rotation.");
		formPanel.add(applyHeader, headerGbc);

		GridBagConstraints gbc = new GridBagConstraints();
		gbc.gridx = 0;
		gbc.gridy = 1;
		gbc.anchor = GridBagConstraints.WEST;
		gbc.insets = new Insets(8, 10, 8, 10);
		gbc.weightx = 0.0;

		addRow(formPanel, gbc, "Triggers GCD", triggersGcdOverride, triggersGcdValue, triggersGcdEffective, triggersGcdApplyAll);
		gbc.gridy++;
		addRow(formPanel, gbc, "Cast Duration", castDurationOverride, castDurationValue, castDurationEffective, castDurationApplyAll);
		gbc.gridy++;
		addRow(formPanel, gbc, "Cooldown", cooldownOverride, cooldownValue, cooldownEffective, cooldownApplyAll);
		gbc.gridy++;
		addRow(formPanel, gbc, "Detection Threshold", thresholdOverride, thresholdValue, thresholdEffective, thresholdApplyAll);
		gbc.gridy++;
		addRow(formPanel, gbc, "Mask", maskOverride, maskValuePanel, maskEffective, maskApplyAll);

		content.add(formPanel, BorderLayout.CENTER);

		JPanel buttonPanel = new JPanel(new FlowLayout(FlowLayout.RIGHT, 8, 0));
		buttonPanel.setOpaque(false);
		JButton resetAllButton = new JButton("Reset All Overrides");
		JButton cancelButton = new JButton("Cancel");
		JButton okButton = new JButton("OK");
		ThemedButtons.apply(resetAllButton, ButtonStyle.DEFAULT);
		ThemedButtons.apply(cancelButton, ButtonStyle.DEFAULT);
		ThemedButtons.apply(okButton, ButtonStyle.DEFAULT);

		resetAllButton.addActionListener(e -> resetAll());
		cancelButton.addActionListener(e -> dispose());
		okButton.addActionListener(e -> onOk());

		buttonPanel.add(resetAllButton);
		buttonPanel.add(cancelButton);
		buttonPanel.add(okButton);

		JPanel south = new JPanel();
		south.setOpaque(false);
		south.setLayout(new BoxLayout(south, BoxLayout.Y_AXIS));
		validationLabel.setAlignmentX(Component.RIGHT_ALIGNMENT);
		validationLabel.setBorder(new EmptyBorder(0, 4, 6, 4));
		south.add(validationLabel);
		south.add(buttonPanel);
		content.add(south, BorderLayout.SOUTH);
		root.add(content, BorderLayout.CENTER);
		setContentPane(root);

		attachOverrideHandler(triggersGcdOverride, triggersGcdValue);
		attachOverrideHandler(castDurationOverride, castDurationValue);
		attachOverrideHandler(cooldownOverride, cooldownValue);
		attachOverrideHandler(thresholdOverride, thresholdValue);
		attachOverrideHandler(maskOverride, maskValuePanel);

		triggersGcdOverride.addActionListener(e -> updateEffectiveLabels());
		triggersGcdValue.addActionListener(e -> updateEffectiveLabels());
		castDurationOverride.addActionListener(e -> updateEffectiveLabels());
		castDurationValue.addChangeListener(e -> updateEffectiveLabels());
		cooldownOverride.addActionListener(e -> updateEffectiveLabels());
		cooldownValue.addChangeListener(e -> updateEffectiveLabels());
		thresholdOverride.addActionListener(e -> updateEffectiveLabels());
		thresholdValue.addChangeListener(e -> updateEffectiveLabels());
		maskOverride.addActionListener(e -> updateEffectiveLabels());
		maskValue.getDocument().addDocumentListener(new SimpleDocumentListener(() -> {
			clearValidationMessage();
			updateEffectiveLabels();
		}));

		installNumericValidation(castDurationValue, "Cast Duration");
		installNumericValidation(cooldownValue, "Cooldown");
		installNumericValidation(thresholdValue, "Detection Threshold");
		maskValue.getDocument().addDocumentListener(new SimpleDocumentListener(this::clearValidationMessage));
	}

	private void addRow(JPanel formPanel,
	                    GridBagConstraints gbc,
	                    String label,
	                    JCheckBox overrideCheckbox,
	                    JComponent valueComponent,
	                    JLabel effectiveLabel,
	                    JCheckBox applyAllCheckbox) {
		gbc.gridx = 0;
		JLabel nameLabel = new JLabel(label);
		nameLabel.setPreferredSize(new Dimension(160, nameLabel.getPreferredSize().height));
		formPanel.add(nameLabel, gbc);

		gbc.gridx = 1;
		formPanel.add(overrideCheckbox, gbc);

		gbc.gridx = 2;
		gbc.weightx = 0.2;
		gbc.fill = GridBagConstraints.HORIZONTAL;
		formPanel.add(valueComponent, gbc);

		gbc.gridx = 3;
		gbc.weightx = 0.6;
		effectiveLabel.setForeground(UiColorPalette.TEXT_MUTED);
		formPanel.add(effectiveLabel, gbc);

		gbc.gridx = 4;
		gbc.weightx = 0.0;
		gbc.fill = GridBagConstraints.NONE;
		applyAllCheckbox.setOpaque(false);
		formPanel.add(applyAllCheckbox, gbc);
	}

	private void attachOverrideHandler(JCheckBox overrideCheckbox, JComponent valueComponent) {
		valueComponent.setEnabled(overrideCheckbox.isSelected());
		overrideCheckbox.addActionListener(e -> valueComponent.setEnabled(overrideCheckbox.isSelected()));
	}

	private void installNumericValidation(JSpinner spinner, String label) {
		if (spinner == null) {
			return;
		}
		JComponent editor = spinner.getEditor();
		if (editor instanceof JSpinner.DefaultEditor defaultEditor) {
			JFormattedTextField textField = defaultEditor.getTextField();
			textField.setColumns(8);
			textField.setInputVerifier(new InputVerifier() {
				@Override
				public boolean verify(JComponent input) {
					return tryCommitSpinner(spinner, label, false);
				}
			});
		}
	}

	private boolean tryCommitSpinner(JSpinner spinner, String label, boolean showWarningOnClamp) {
		clearValidationFor(spinner);
		try {
			spinner.commitEdit();
		} catch (Exception e) {
			setValidationError(label + " must be a number.", spinner);
			return false;
		}

		try {
			Object value = spinner.getValue();
			if (spinner == thresholdValue) {
				double threshold = value instanceof Double d ? d : ((Number) value).doubleValue();
				double clamped = clamp(threshold, 0.0d, 1.0d);
				if (Double.compare(threshold, clamped) != 0) {
					spinner.setValue(clamped);
					if (showWarningOnClamp) {
						setValidationWarning("Detection Threshold clamped to " + clamped + ".", spinner);
					}
				}
				return true;
			}

			if (value instanceof Number num && num.longValue() < 0) {
				setValidationError(label + " must be non-negative.", spinner);
				return false;
			}

			return true;
		} catch (Exception e) {
			setValidationError(label + " value is invalid.", spinner);
			return false;
		}
	}

	private void clearValidationFor(JComponent component) {
		if (component == null) {
			return;
		}
		component.setToolTipText(null);
		restoreDefaultBackground(component);
		if (component instanceof JTextField field) {
			field.setOpaque(false);
		}
	}

	private void setValidationError(String message, JComponent component) {
		validationLabel.setText(message != null ? message : "");
		validationLabel.setForeground(UiColorPalette.TEXT_DANGER);
		if (component != null) {
			component.setToolTipText(message);
			component.setBackground(UiColorPalette.INPUT_INVALID_BACKGROUND);
			if (component instanceof JTextField field) {
				field.setOpaque(true);
			}
			component.requestFocusInWindow();
		}
		Toolkit.getDefaultToolkit().beep();
	}

	private void setValidationWarning(String message, JComponent component) {
		validationLabel.setText(message != null ? message : "");
		validationLabel.setForeground(UiColorPalette.TEXT_MUTED);
		if (component != null) {
			component.setToolTipText(message);
		}
	}

	private void clearValidationMessage() {
		validationLabel.setText("");
		validationLabel.setToolTipText(null);
	}

	private void rememberDefaultBackground(JComponent component) {
		if (component == null) {
			return;
		}
		defaultBackgrounds.putIfAbsent(component, component.getBackground());
	}

	private void restoreDefaultBackground(JComponent component) {
		if (component == null) {
			return;
		}
		Color background = defaultBackgrounds.get(component);
		if (background != null) {
			component.setBackground(background);
		}
	}

	private void loadFromOverrides(AbilitySettingsOverrides overrides) {
		AbilitySettingsOverrides current = overrides != null ? overrides : AbilitySettingsOverrides.empty();

		Boolean triggersGcd = current.getTriggersGcdOverride().orElse(rotationWideOverrides.getTriggersGcdOverride().orElse(null));
		triggersGcdOverride.setSelected(triggersGcd != null);
		triggersGcdValue.setSelected(Boolean.TRUE.equals(triggersGcd));
		triggersGcdValue.setEnabled(triggersGcd != null);

		Short castDuration = current.getCastDurationOverride().orElse(rotationWideOverrides.getCastDurationOverride().orElse(null));
		castDurationOverride.setSelected(castDuration != null);
		castDurationValue.setValue(castDuration != null ? (int) castDuration : baseCastDuration());
		castDurationValue.setEnabled(castDuration != null);

		Short cooldown = current.getCooldownOverride().orElse(rotationWideOverrides.getCooldownOverride().orElse(null));
		cooldownOverride.setSelected(cooldown != null);
		cooldownValue.setValue(cooldown != null ? (int) cooldown : baseCooldown());
		cooldownValue.setEnabled(cooldown != null);

		Double threshold = current.getDetectionThresholdOverride().orElse(rotationWideOverrides.getDetectionThresholdOverride().orElse(null));
		thresholdOverride.setSelected(threshold != null);
		thresholdValue.setValue(threshold != null ? threshold : baseDetectionThreshold());
		thresholdValue.setEnabled(threshold != null);

		String mask = current.getMaskOverride().orElse(rotationWideOverrides.getMaskOverride().orElse(null));
		maskOverride.setSelected(mask != null);
		maskValue.setText(mask != null ? mask : baseMask());
		maskValue.setEnabled(mask != null);

		triggersGcdApplyAll.setSelected(rotationWideOverrides.getTriggersGcdOverride().isPresent());
		castDurationApplyAll.setSelected(rotationWideOverrides.getCastDurationOverride().isPresent());
		cooldownApplyAll.setSelected(rotationWideOverrides.getCooldownOverride().isPresent());
		thresholdApplyAll.setSelected(rotationWideOverrides.getDetectionThresholdOverride().isPresent());
		maskApplyAll.setSelected(rotationWideOverrides.getMaskOverride().isPresent());
		updateEffectiveLabels();
	}

	private void updateEffectiveLabels() {
		clearValidationMessage();
		boolean baseTriggersGcd = baseTriggersGcd();
		boolean effectiveTriggersGcd = triggersGcdOverride.isSelected()
				? triggersGcdValue.isSelected()
				: baseTriggersGcd;
		updateEffectiveLabel(triggersGcdEffective, effectiveTriggersGcd, triggersGcdOverride.isSelected(),
				rotationWideOverrides.getTriggersGcdOverride().isPresent());

		int baseCastDuration = baseCastDuration();
		int effectiveCastDuration = castDurationOverride.isSelected()
				? (Integer) castDurationValue.getValue()
				: baseCastDuration;
		updateEffectiveLabel(castDurationEffective, effectiveCastDuration, castDurationOverride.isSelected(),
				rotationWideOverrides.getCastDurationOverride().isPresent());

		int baseCooldown = baseCooldown();
		int effectiveCooldown = cooldownOverride.isSelected()
				? (Integer) cooldownValue.getValue()
				: baseCooldown;
		updateEffectiveLabel(cooldownEffective, effectiveCooldown, cooldownOverride.isSelected(),
				rotationWideOverrides.getCooldownOverride().isPresent());

		double baseThreshold = baseDetectionThreshold();
		double effectiveThreshold = thresholdOverride.isSelected()
				? clamp(((Number) thresholdValue.getValue()).doubleValue(), 0.0d, 1.0d)
				: baseThreshold;
		updateEffectiveLabel(thresholdEffective, effectiveThreshold, thresholdOverride.isSelected(),
				rotationWideOverrides.getDetectionThresholdOverride().isPresent());

		String baseMask = baseMask();
		String effectiveMask = maskOverride.isSelected()
				? maskValue.getText().trim()
				: baseMask;
		updateEffectiveLabel(maskEffective, effectiveMask.isEmpty() ? "\u2014" : effectiveMask, maskOverride.isSelected(),
				rotationWideOverrides.getMaskOverride().isPresent());
	}

	private void updateEffectiveLabel(JLabel label, Object effectiveValue, boolean overridden, boolean hasRotationWideOverride) {
		String state;
		if (overridden) {
			state = "Custom";
		} else if (hasRotationWideOverride) {
			state = "Inherited (Rotation)";
		} else {
			state = "Inherited (Global)";
		}
		label.setText("Effective: " + effectiveValue + " \u2013 " + state);
		label.setForeground(overridden ? UiColorPalette.TEXT_SUCCESS : UiColorPalette.TEXT_MUTED);
	}

	private void resetAll() {
		triggersGcdOverride.setSelected(false);
		triggersGcdValue.setSelected(baseTriggersGcd());
		triggersGcdValue.setEnabled(false);
		triggersGcdApplyAll.setSelected(false);

		castDurationOverride.setSelected(false);
		castDurationValue.setValue(baseCastDuration());
		castDurationValue.setEnabled(false);
		castDurationApplyAll.setSelected(false);

		cooldownOverride.setSelected(false);
		cooldownValue.setValue(baseCooldown());
		cooldownValue.setEnabled(false);
		cooldownApplyAll.setSelected(false);

		thresholdOverride.setSelected(false);
		thresholdValue.setValue(baseDetectionThreshold());
		thresholdValue.setEnabled(false);
		thresholdApplyAll.setSelected(false);

		maskOverride.setSelected(false);
		maskValue.setText(baseMask());
		maskValue.setEnabled(false);
		maskApplyAll.setSelected(false);

		updateEffectiveLabels();
	}

	private void onOk() {
		if (!tryCommitSpinner(castDurationValue, "Cast Duration", false)) {
			return;
		}
		if (!tryCommitSpinner(cooldownValue, "Cooldown", false)) {
			return;
		}
		if (!tryCommitSpinner(thresholdValue, "Detection Threshold", true)) {
			return;
		}

		String resolvedMask = resolveMaskOverride();
		if (resolvedMask == null) {
			return;
		}
		double threshold = ((Number) thresholdValue.getValue()).doubleValue();

		AbilitySettingsOverrides.Builder builder = AbilitySettingsOverrides.builder();

		if (triggersGcdOverride.isSelected()) {
			builder.triggersGcd(triggersGcdValue.isSelected());
		}
		if (castDurationOverride.isSelected()) {
			builder.castDuration((short) ((Integer) castDurationValue.getValue()).intValue());
		}
		if (cooldownOverride.isSelected()) {
			builder.cooldown((short) ((Integer) cooldownValue.getValue()).intValue());
		}
		if (thresholdOverride.isSelected()) {
			builder.detectionThreshold(AbilityValueSanitizers.clampFiniteOrDefault(threshold, 0.0d, 1.0d, 0.0d));
		}
		if (maskOverride.isSelected()) {
			builder.mask(resolvedMask);
		}

		result = new Result(
				builder.build(),
				triggersGcdApplyAll.isSelected(),
				castDurationApplyAll.isSelected(),
				cooldownApplyAll.isSelected(),
				thresholdApplyAll.isSelected(),
				maskApplyAll.isSelected()
		);
		dispose();
	}

	private String resolveMaskOverride() {
		clearValidationFor(maskValue);
		if (!maskOverride.isSelected()) {
			return "";
		}

		String mask = maskValue.getText() != null ? maskValue.getText().trim() : "";
		if (mask.isEmpty()) {
			return "";
		}
		if (FilenameValidators.containsPathSeparator(mask)) {
			setValidationError("Mask must be a simple file name (no folders).", maskValue);
			return null;
		}

		if (maskValidator != null) {
			ValidationResult result = maskValidator.validate(mask);
			if (result != null && !result.isValid()) {
				String message = result.message() != null ? result.message() : "Mask is invalid.";
				setValidationError(message, maskValue);
				return null;
			}
		}

		return mask;
	}

	private boolean containsPathSeparator(String value) {
		return FilenameValidators.containsPathSeparator(value);
	}

	private double clamp(double value, double min, double max) {
		return AbilityValueSanitizers.clampFiniteOrDefault(value, min, max, min);
	}

	private boolean baseTriggersGcd() {
		return baseAbility == null || baseAbility.isTriggersGcd();
	}

	private int baseCastDuration() {
		return baseAbility != null ? baseAbility.getCastDuration() : 0;
	}

	private int baseCooldown() {
		return baseAbility != null ? baseAbility.getCooldown() : 0;
	}

	private double baseDetectionThreshold() {
		if (baseAbility == null) {
			return 0.0d;
		}
		return baseAbility.getDetectionThreshold().orElse(0.0d);
	}

	private String baseMask() {
		if (baseAbility == null) {
			return "";
		}
		return baseAbility.getMask().orElse("");
	}

	private interface DocumentUpdate {
		void onUpdate();
	}

	private static final class SimpleDocumentListener implements javax.swing.event.DocumentListener {
		private final DocumentUpdate update;

		private SimpleDocumentListener(DocumentUpdate update) {
			this.update = update;
		}

		@Override
		public void insertUpdate(javax.swing.event.DocumentEvent e) {
			update.onUpdate();
		}

		@Override
		public void removeUpdate(javax.swing.event.DocumentEvent e) {
			update.onUpdate();
		}

		@Override
		public void changedUpdate(javax.swing.event.DocumentEvent e) {
			update.onUpdate();
		}
	}
}
