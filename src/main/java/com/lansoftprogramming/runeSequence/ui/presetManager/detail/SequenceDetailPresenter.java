package com.lansoftprogramming.runeSequence.ui.presetManager.detail;

import com.lansoftprogramming.runeSequence.core.sequence.model.AbilitySettingsOverrides;
import com.lansoftprogramming.runeSequence.core.sequence.model.EffectiveAbilityConfig;
import com.lansoftprogramming.runeSequence.core.sequence.parser.TooltipGrammar;
import com.lansoftprogramming.runeSequence.infrastructure.config.AbilityConfig;
import com.lansoftprogramming.runeSequence.infrastructure.config.RotationConfig;
import com.lansoftprogramming.runeSequence.infrastructure.config.dto.PresetAbilitySettings;
import com.lansoftprogramming.runeSequence.infrastructure.config.dto.PresetRotationDefaults;
import com.lansoftprogramming.runeSequence.ui.notification.NotificationService;
import com.lansoftprogramming.runeSequence.ui.presetManager.drag.handler.AbilityDragController;
import com.lansoftprogramming.runeSequence.ui.presetManager.drag.model.DragPreviewModel;
import com.lansoftprogramming.runeSequence.ui.presetManager.drag.model.DropPreview;
import com.lansoftprogramming.runeSequence.ui.presetManager.model.SequenceElement;
import com.lansoftprogramming.runeSequence.ui.presetManager.service.AbilityOverridesService;
import com.lansoftprogramming.runeSequence.ui.presetManager.service.ExpressionBuilder;
import com.lansoftprogramming.runeSequence.ui.shared.model.AbilityItem;
import com.lansoftprogramming.runeSequence.ui.shared.model.TooltipItem;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.swing.*;
import java.awt.*;
import java.awt.event.WindowAdapter;
import java.awt.event.WindowEvent;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;

class SequenceDetailPresenter implements AbilityDragController.DragCallback {
	private static final Logger logger = LoggerFactory.getLogger(SequenceDetailPresenter.class);

	private final SequenceDetailService detailService;
	private final AbilityOverridesService overridesService;
	private final AbilityFlowView flowView;
	private final View view;
	private final ExpressionBuilder expressionBuilder;
	private final NotificationService notifications;

	private List<SequenceElement> currentElements;
	private List<SequenceElement> previewElements;
	private final List<SequenceDetailPanel.SaveListener> saveListeners;
	private RotationConfig.PresetData currentPreset;
	private String currentPresetId;
	private String loadedSequenceName;
	private String loadedExpression;
	private List<SequenceElement> loadedElements;
	private Map<String, AbilitySettingsOverrides> loadedOverrides;
	private PresetRotationDefaults loadedRotationDefaults;
	private Map<String, AbilitySettingsOverrides> loadedPerAbilityOverrides;
	private Map<String, AbilitySettingsOverrides> currentPerAbilityOverrides;
	private List<SequenceElement> originalElementsBeforeDrag;
	private SequenceElement draggedAbilityElement;
	private DragPreviewModel currentPreview;
	private boolean isHighlightActive;
	private boolean isDragOutsidePanel;
	private boolean isCurrentDragFromPalette;

	SequenceDetailPresenter(SequenceDetailService detailService,
	                        AbilityOverridesService overridesService,
	                        AbilityFlowView flowView,
	                        View view,
	                        NotificationService notifications) {
		this.detailService = detailService;
		this.overridesService = overridesService;
		this.flowView = flowView;
		this.view = view;
		this.notifications = notifications;
		this.expressionBuilder = new ExpressionBuilder();
		this.currentElements = new ArrayList<>();
		this.previewElements = new ArrayList<>();
		this.saveListeners = new ArrayList<>();
		this.flowView.attachDragController(this);
		this.flowView.setTooltipEditHandler(this::editTooltipAt);
		this.flowView.setAbilityPropertiesHandler(this::openAbilityPropertiesAt);
		this.loadedSequenceName = "";
		this.loadedExpression = "";
		this.loadedElements = new ArrayList<>();
		this.loadedOverrides = Map.of();
		this.loadedRotationDefaults = null;
		this.loadedPerAbilityOverrides = Map.of();
		this.currentPerAbilityOverrides = new java.util.LinkedHashMap<>();
	}

	void addSaveListener(SequenceDetailPanel.SaveListener listener) {
		if (listener != null) {
			saveListeners.add(listener);
		}
	}

	void loadSequence(RotationConfig.PresetData presetData) {
		loadSequence(null, presetData);
	}

	void loadSequence(String presetId, RotationConfig.PresetData presetData) {
		if (presetData == null) {
			clear();
			return;
		}

		this.currentPresetId = presetId;
		this.currentPreset = presetData;
		this.draggedAbilityElement = null;
		view.setSequenceName(presetData.getName());

		loadedSequenceName = presetData.getName() != null ? presetData.getName() : "";
		loadedExpression = presetData.getExpression() != null ? presetData.getExpression() : "";

		Map<String, AbilitySettingsOverrides> overridesByLabel = overridesService.toDomainOverrides(presetData.getAbilitySettings());
		Map<String, AbilitySettingsOverrides> perAbilityOverrides = overridesService.toDomainPerAbilityOverrides(presetData.getAbilitySettings());
		List<SequenceElement> parsedElements = detailService.parseSequenceExpression(loadedExpression, overridesByLabel);
		loadedElements = parsedElements != null ? new ArrayList<>(parsedElements) : new ArrayList<>();
		loadedOverrides = overridesService.extractOverridesByLabel(loadedElements);
		logUnusedOverrides(overridesByLabel, loadedOverrides);
		loadedRotationDefaults = overridesService.extractRotationDefaults(presetData.getAbilitySettings());
		loadedPerAbilityOverrides = new java.util.LinkedHashMap<>(perAbilityOverrides);
		currentPerAbilityOverrides = new java.util.LinkedHashMap<>(loadedPerAbilityOverrides);
		currentElements = new ArrayList<>(loadedElements);
		previewElements = new ArrayList<>(loadedElements);
		refreshFlowView();

		long abilityCount = currentElements.stream().filter(SequenceElement::isAbility).count();
		logger.debug("Loaded sequence: {} with {} abilities", presetData.getName(), abilityCount);
	}

	private void logUnusedOverrides(Map<String, AbilitySettingsOverrides> overridesByLabel,
	                               Map<String, AbilitySettingsOverrides> appliedOverrides) {
		if (overridesByLabel == null || overridesByLabel.isEmpty()) {
			return;
		}
		Map<String, AbilitySettingsOverrides> applied = appliedOverrides != null ? appliedOverrides : Map.of();

		List<String> unused = new ArrayList<>();
		for (String label : overridesByLabel.keySet()) {
			if (label == null || label.isBlank()) {
				continue;
			}
			if (!applied.containsKey(label)) {
				unused.add(label);
			}
		}

		if (unused.isEmpty()) {
			return;
		}

		int showLimit = 8;
		List<String> sample = unused.size() > showLimit ? unused.subList(0, showLimit) : unused;
		logger.warn("Ignoring {} per-instance overrides with no matching label in expression (sample={})", unused.size(), sample);
	}

	void clear() {
		view.setSequenceName("");
		currentElements.clear();
		previewElements.clear();
		currentPreset = null;
		currentPresetId = null;
		loadedSequenceName = "";
		loadedExpression = "";
		loadedElements = new ArrayList<>();
		loadedOverrides = Map.of();
		loadedRotationDefaults = null;
		draggedAbilityElement = null;
		flowView.setModificationIndicators(false, Set.of());
		flowView.renderSequenceElements(currentElements);
	}

	boolean hasUnsavedChanges() {
		String currentName = view.getSequenceName();
		String trimmedCurrentName = currentName != null ? currentName.trim() : "";
		String trimmedLoadedName = loadedSequenceName != null ? loadedSequenceName.trim() : "";

		if (!trimmedCurrentName.equals(trimmedLoadedName)) {
			return true;
		}

		String currentExpression = expressionBuilder.buildExpression(currentElements);
		String loadedExpr = loadedExpression != null ? loadedExpression : "";
		if (!currentExpression.equals(loadedExpr)) {
			return true;
		}

		Map<String, AbilitySettingsOverrides> currentOverrides = overridesService.extractOverridesByLabel(currentElements);
		Map<String, AbilitySettingsOverrides> baselineOverrides = loadedOverrides != null ? loadedOverrides : Map.of();
		if (!currentOverrides.equals(baselineOverrides)) {
			return true;
		}

		PresetRotationDefaults currentRotationDefaults = overridesService.extractRotationDefaults(
				currentPreset != null ? currentPreset.getAbilitySettings() : null
		);
		PresetRotationDefaults baselineRotationDefaults = loadedRotationDefaults;
		if (baselineRotationDefaults == null) {
			return currentRotationDefaults != null;
		}
		if (!baselineRotationDefaults.equals(currentRotationDefaults)) {
			return true;
		}

		Map<String, AbilitySettingsOverrides> baselinePerAbility = loadedPerAbilityOverrides != null ? loadedPerAbilityOverrides : Map.of();
		Map<String, AbilitySettingsOverrides> currentPerAbility = currentPerAbilityOverrides != null ? currentPerAbilityOverrides : Map.of();
		return !baselinePerAbility.equals(currentPerAbility);
	}

	void saveSequence() {
		String sequenceName = view.getSequenceName();
		String trimmedName = sequenceName != null ? sequenceName.trim() : "";

		currentElements = overridesService.normalizeAbilityElements(currentElements);
		previewElements = new ArrayList<>(currentElements);

		String expression = expressionBuilder.buildExpression(currentElements);
		PresetAbilitySettings abilitySettings = overridesService.buildAbilitySettings(currentElements);
		PresetRotationDefaults currentRotationDefaults = overridesService.extractRotationDefaults(
				currentPreset != null ? currentPreset.getAbilitySettings() : null
		);
		abilitySettings = overridesService.applyRotationDefaults(abilitySettings, currentRotationDefaults);
		abilitySettings = overridesService.applyPerAbilityOverrides(abilitySettings, currentPerAbilityOverrides);
		Map<String, AbilitySettingsOverrides> currentOverrides = overridesService.extractOverridesByLabel(currentElements);

		SequenceDetailService.SaveOutcome outcome = detailService.saveSequence(
				currentPresetId,
				currentPreset,
				trimmedName,
				expression,
				abilitySettings
		);

		if (!outcome.isSuccess()) {
			String message = outcome.getMessage() != null ? outcome.getMessage() : "Failed to save sequence.";
			logger.debug("Save attempt failed: {}", message);
			if (notifications != null) {
				if (outcome.isValidationFailure()) {
					notifications.showWarning(message);
				} else {
					notifications.showError(message);
				}
			}
			return;
		}

		SequenceDetailService.SaveResult result = outcome.getResult();
		if (result == null) {
			logger.warn("Save succeeded but result was null. No updates applied.");
			return;
		}

		currentPresetId = result.getPresetId();
		currentPreset = result.getPresetData();
		if (currentPreset != null) {
			currentPreset.setName(trimmedName);
			currentPreset.setExpression(expression);
			currentPreset.setAbilitySettings(abilitySettings);
		}

		loadedSequenceName = trimmedName;
		loadedExpression = expression;
		loadedElements = new ArrayList<>(currentElements);
		loadedOverrides = new java.util.LinkedHashMap<>(currentOverrides);
		loadedRotationDefaults = currentRotationDefaults;
		loadedPerAbilityOverrides = currentPerAbilityOverrides != null
				? new java.util.LinkedHashMap<>(currentPerAbilityOverrides)
				: Map.of();

		view.setSequenceName(trimmedName);
		notifySaveListeners(result);

		String successMessage = outcome.getMessage() != null ? outcome.getMessage() : "Sequence saved successfully.";
		if (notifications != null) {
			notifications.showSuccess(successMessage);
		}
	}

	void discardChanges() {
		if (currentPreset != null) {
			currentPreset.setName(loadedSequenceName);
			currentPreset.setExpression(loadedExpression);
			PresetAbilitySettings abilitySettings = overridesService.buildAbilitySettings(loadedElements);
			abilitySettings = overridesService.applyRotationDefaults(abilitySettings, loadedRotationDefaults);
			abilitySettings = overridesService.applyPerAbilityOverrides(abilitySettings, loadedPerAbilityOverrides);
			currentPreset.setAbilitySettings(abilitySettings);
		}

		currentElements = new ArrayList<>(loadedElements);
		previewElements = new ArrayList<>(loadedElements);
		currentPerAbilityOverrides = loadedPerAbilityOverrides != null
				? new java.util.LinkedHashMap<>(loadedPerAbilityOverrides)
				: new java.util.LinkedHashMap<>();
		view.setSequenceName(loadedSequenceName);
		refreshFlowView();
	}

	void startPaletteDrag(AbilityItem item, JPanel card, Point startPoint) {
		flowView.startPaletteDrag(item, card, startPoint);
	}

	void openRotationSettings() {
		if (currentPreset == null) {
			if (notifications != null) {
				notifications.showWarning("No rotation selected.");
			}
			return;
		}
		view.showRotationSettings(currentPresetId, currentPreset);
		refreshFlowView();
	}

	@Override
	public void onDragStart(AbilityItem item, boolean isFromPalette, int abilityIndex) {
		logger.info("Drag start: key={}, abilityIndex={}", item.getKey(), abilityIndex);
		flowView.resetPreview();
		flowView.setDragOutsidePanel(false);
		isHighlightActive = false;
		isDragOutsidePanel = false;
		isCurrentDragFromPalette = isFromPalette;
		draggedAbilityElement = null;
		// Keep a pristine copy so cancellation can restore the view state.
		originalElementsBeforeDrag = new ArrayList<>(currentElements);
		if (!isFromPalette) {
			if (item instanceof TooltipItem) {
				int removalIndex = resolveTooltipElementIndex(abilityIndex);
				logger.info("Drag start resolved tooltip removalIndex={} (cardIndex={})", removalIndex, abilityIndex);
				if (removalIndex >= 0) {
					List<SequenceElement> working = new ArrayList<>(currentElements);
					previewElements = expressionBuilder.removeTooltipAt(working, removalIndex);
				} else {
					logger.warn("Failed to resolve tooltip index for drag start: abilityIndex={}", abilityIndex);
					previewElements = new ArrayList<>(currentElements);
				}
			} else {
				int removalIndex = resolveElementIndexForDrag(item, abilityIndex);
				logger.info("Drag start resolved removalIndex={} for key={} (abilityIndex={})",
						removalIndex, item.getKey(), abilityIndex);
				logAbilityOrder("Before removal", currentElements);
				if (removalIndex >= 0) {
					List<SequenceElement> working = new ArrayList<>(currentElements);
					if (removalIndex < currentElements.size()) {
						draggedAbilityElement = currentElements.get(removalIndex);
					}
					String beforeExpr = expressionBuilder.buildExpression(working);
					previewElements = expressionBuilder.removeAbilityAt(working, removalIndex);
					String afterExpr = expressionBuilder.buildExpression(previewElements);
					logger.info("Removed ability occurrence: key={}, before='{}', after='{}'",
							item.getKey(), beforeExpr, afterExpr);
				} else {
					logger.warn("Failed to resolve element index for drag start: item={}, abilityIndex={}",
							item.getKey(), abilityIndex);
					previewElements = new ArrayList<>(currentElements);
				}
				logAbilityOrder("After removal", previewElements);
			}
		} else {
			previewElements = new ArrayList<>(currentElements);
		}
	}

	@Override
	public void onDragMove(AbilityItem draggedItem, DragPreviewModel previewModel) {
		currentPreview = previewModel;
		isHighlightActive = false;

		Point cursorPos = previewModel != null ? previewModel.getCursorInFlowPanel() : null;
		if (cursorPos != null) {
			Point detailPanelPoint = SwingUtilities.convertPoint(flowView, cursorPos, view.asComponent());
			isDragOutsidePanel = !view.asComponent().contains(detailPanelPoint);
		} else {
			isDragOutsidePanel = false;
		}

		flowView.setDragOutsidePanel(isDragOutsidePanel);

		if (!isDragOutsidePanel && previewModel != null && previewModel.isValid()) {
			isHighlightActive = flowView.applyPreviewModel(previewModel);
		} else {
			flowView.applyPreviewModel(null);
		}
	}

	@Override
	public void onDragEnd(AbilityItem draggedItem, boolean commit) {
		flowView.resetPreview();
		boolean isFromSequence = !previewElements.equals(currentElements);
		boolean droppedInTrash = !commit && isFromSequence && isDragOutsidePanel;
		DropPreview dropPreview = currentPreview != null ? currentPreview.getDropPreview() : null;

		if (commit || droppedInTrash) {
			if (droppedInTrash) {
				currentElements = new ArrayList<>(previewElements);
				previewElements = new ArrayList<>(currentElements);
				long abilityCount = currentElements.stream().filter(SequenceElement::isAbility).count();
				updateExpression();
				logger.info("Deleted ability via trash drop: key={}, newAbilityCount={}", draggedItem.getKey(), abilityCount);
			} else if (isHighlightActive && currentPreview != null && currentPreview.isValid()) {
				logger.info(
						"Commit insert: item={}, type={}, insertIndex={}, zone={}, dropSide={}, fromSequence={}, currentElementCount={}",
						draggedItem.getKey(),
						draggedItem.getClass().getSimpleName(),
						dropPreview != null ? dropPreview.getInsertIndex() : -1,
						dropPreview != null ? dropPreview.getZoneType() : null,
						dropPreview != null ? dropPreview.getDropSide() : null,
						isFromSequence,
						currentElements.size()
				);

				if (draggedItem instanceof ClipboardInsertItem clipboardItem && dropPreview != null) {
					currentElements = expressionBuilder.insertSequence(
							new ArrayList<>(previewElements),
							clipboardItem.getElements(),
							dropPreview.getInsertIndex(),
							dropPreview.getZoneType(),
							dropPreview.getDropSide()
					);
				} else if (draggedItem instanceof TooltipItem tooltipItem && dropPreview != null) {
					String tooltipMessage = tooltipItem.getMessage();
					if (isCurrentDragFromPalette) {
						String input = promptForTooltipText("New tooltip");
						if (input == null) {
							logger.info("Tooltip insert cancelled by user.");
							tooltipMessage = null;
						}
						if (tooltipMessage != null && !TooltipGrammar.isValidTooltipMessage(input)) {
							if (notifications != null) {
								notifications.showWarning("Tooltip text cannot contain →, +, or / characters.");
							} else {
								Toolkit.getDefaultToolkit().beep();
							}
							logger.info("Tooltip insert rejected due to invalid content.");
							tooltipMessage = null;
						}
						if (tooltipMessage != null) {
							tooltipMessage = input;
						}
					}
					if (tooltipMessage == null) {
						currentElements = new ArrayList<>(currentElements);
						previewElements = new ArrayList<>(currentElements);
					} else {
						currentElements = expressionBuilder.insertTooltip(
								new ArrayList<>(previewElements),
								tooltipMessage,
								dropPreview.getInsertIndex(),
								dropPreview.getZoneType(),
								dropPreview.getDropSide()
						);
					}
				} else if (dropPreview != null) {
					SequenceElement abilityElement = draggedAbilityElement != null
							? draggedAbilityElement
							: SequenceElement.ability(draggedItem.getKey());
					currentElements = expressionBuilder.insertAbility(
							new ArrayList<>(previewElements),
							abilityElement,
							dropPreview.getInsertIndex(),
							dropPreview.getZoneType(),
							dropPreview.getDropSide()
					);
				}
				previewElements = new ArrayList<>(currentElements);
				updateExpression();
			} else {
				currentElements = new ArrayList<>(currentElements);
				previewElements = new ArrayList<>(currentElements);
				logger.info(
					"Commit skipped: highlightActive={}, previewValid={}, currentElementCount={}",
					isHighlightActive,
					currentPreview != null && currentPreview.isValid(),
					currentElements.size()
				);
			}
		} else {
			// Restore original state on any cancellation (including right-click cancel or drop outside).
			List<SequenceElement> restore = originalElementsBeforeDrag != null
				? new ArrayList<>(originalElementsBeforeDrag)
				: new ArrayList<>(currentElements);
			logger.info(
				"Drag cancelled: restoring original sequence (count={}, item={})",
				restore.size(),
				draggedItem.getKey()
			);
			currentElements = restore;
			previewElements = new ArrayList<>(currentElements);
			updateExpression();
		}

		isHighlightActive = false;
		isDragOutsidePanel = false;
		isCurrentDragFromPalette = false;
		currentPreview = null;
		originalElementsBeforeDrag = null;
		draggedAbilityElement = null;
		flowView.setDragOutsidePanel(false);
		refreshFlowView();
	}

	@Override
	public List<SequenceElement> getCurrentElements() {
		return previewElements;
	}

	@Override
	public Component[] getAllCards() {
		return flowView.getAbilityCardArray();
	}

	private void notifySaveListeners(SequenceDetailService.SaveResult result) {
		for (SequenceDetailPanel.SaveListener listener : saveListeners) {
			try {
				listener.onSequenceSaved(result);
			} catch (Exception listenerException) {
				logger.warn("Save listener threw exception", listenerException);
			}
		}
	}

	private void updateExpression() {
		if (currentPreset != null) {
			String newExpression = expressionBuilder.buildExpression(currentElements);
			currentPreset.setExpression(newExpression);
			PresetRotationDefaults rotationDefaults = overridesService.extractRotationDefaults(currentPreset.getAbilitySettings());
			PresetAbilitySettings abilitySettings = overridesService.buildAbilitySettings(currentElements);
			abilitySettings = overridesService.applyRotationDefaults(abilitySettings, rotationDefaults);
			abilitySettings = overridesService.applyPerAbilityOverrides(abilitySettings, currentPerAbilityOverrides);
			currentPreset.setAbilitySettings(abilitySettings);
			logger.debug("Updated expression: {}", newExpression);
		}
	}

	private void editTooltipAt(int elementIndex) {
		if (elementIndex < 0 || elementIndex >= currentElements.size()) {
			return;
		}
		SequenceElement element = currentElements.get(elementIndex);
		if (!element.isTooltip()) {
			return;
		}

		String currentMessage = element.getValue();
		String input = promptForTooltipText(currentMessage);

		if (input == null) {
			return;
		}
		ExpressionBuilder.TooltipEditResult editResult = expressionBuilder.editTooltipAt(currentElements, elementIndex, input);
		ExpressionBuilder.TooltipEditStatus status = editResult.status();

		if (status == ExpressionBuilder.TooltipEditStatus.INVALID) {
			if (notifications != null) {
				notifications.showWarning("Tooltip text cannot contain →, +, or / characters.");
			} else {
				Toolkit.getDefaultToolkit().beep();
			}
			return;
		}

		if (status == ExpressionBuilder.TooltipEditStatus.SKIPPED) {
			return;
		}

		List<SequenceElement> updated = new ArrayList<>(editResult.elements());
			currentElements = updated;
			previewElements = new ArrayList<>(updated);
			updateExpression();
			refreshFlowView();
		}

	private String promptForTooltipText(String initialValue) {
		JTextField inputField = new JTextField(initialValue != null ? initialValue : "", 26);
		JOptionPane optionPane = new JOptionPane(
				inputField,
				JOptionPane.PLAIN_MESSAGE,
				JOptionPane.OK_CANCEL_OPTION
		);

		JDialog dialog = optionPane.createDialog(view.asComponent(), "Tooltip Properties");
		dialog.addWindowListener(new WindowAdapter() {
			@Override
			public void windowOpened(WindowEvent e) {
				inputField.requestFocusInWindow();
				inputField.selectAll();
			}
		});

		dialog.setVisible(true);
		Object value = optionPane.getValue();
		if (value instanceof Integer option && option == JOptionPane.OK_OPTION) {
			return inputField.getText();
		}
		return null;
	}

	private void openAbilityPropertiesAt(int elementIndex) {
		if (elementIndex < 0 || elementIndex >= currentElements.size()) {
			return;
		}
		SequenceElement element = currentElements.get(elementIndex);
		if (!element.isAbility()) {
			return;
		}

		String abilityKey = element.getResolvedAbilityKey();
		if (abilityKey == null || abilityKey.isBlank()) {
			return;
		}

		AbilityItem item = detailService.createAbilityItem(abilityKey);
		if (item == null) {
			return;
		}

		AbilityConfig.AbilityData rawBaseAbility = detailService.resolveBaseAbilityData(abilityKey);
		AbilitySettingsOverrides rotationWideOverrides = currentPerAbilityOverrides != null
				? currentPerAbilityOverrides.get(abilityKey)
				: null;
		EffectiveAbilityConfig baseAbility = rawBaseAbility != null
				? detailService.resolveEffectiveAbilityConfig(abilityKey, rotationWideOverrides)
				: null;
		AbilitySettingsOverrides currentOverridesForInstance = element.getAbilitySettingsOverrides();

		Window owner = SwingUtilities.getWindowAncestor(view.asComponent());
		AbilityPropertiesDialog dialog = new AbilityPropertiesDialog(
				owner,
				item,
				baseAbility,
				rotationWideOverrides,
				currentOverridesForInstance,
				detailService.createMaskValidator()
		);
		dialog.setVisible(true);

		AbilityPropertiesDialog.Result result = dialog.getResult();
		if (result == null) {
			return;
		}

		AbilitySettingsOverrides updatedOverrides = result.overrides();
		if (updatedOverrides != null && updatedOverrides.isEmpty()) {
			updatedOverrides = null;
		}

		AbilitySettingsOverrides updatedRotationWideOverrides = rotationWideOverrides;
		AbilitySettingsOverrides updatedInstanceOverrides = updatedOverrides;
		if (result.hasAnyApplyToAll()) {
			updatedRotationWideOverrides = applyRotationWideOverrides(rotationWideOverrides, updatedOverrides, result);
			updatedInstanceOverrides = stripAppliedRotationFields(updatedOverrides, result);
		}

		if (currentPerAbilityOverrides == null) {
			currentPerAbilityOverrides = new java.util.LinkedHashMap<>();
		}
		if (updatedRotationWideOverrides == null || updatedRotationWideOverrides.isEmpty()) {
			currentPerAbilityOverrides.remove(abilityKey);
		} else {
			currentPerAbilityOverrides.put(abilityKey, updatedRotationWideOverrides);
		}

		List<SequenceElement> updated = new ArrayList<>(currentElements);
		updated.set(elementIndex, element.withOverrides(updatedInstanceOverrides != null && updatedInstanceOverrides.isEmpty() ? null : updatedInstanceOverrides));

		currentElements = overridesService.normalizeAbilityElements(updated);
		previewElements = new ArrayList<>(currentElements);
		updateExpression();
		refreshFlowView();
	}

	private void refreshFlowView() {
		boolean sequenceWideModified = overridesService.extractRotationDefaults(
				currentPreset != null ? currentPreset.getAbilitySettings() : null
		) != null;
		Set<String> modifiedAbilityKeys = currentPerAbilityOverrides != null
				? Set.copyOf(currentPerAbilityOverrides.keySet())
				: Set.of();
		flowView.setModificationIndicators(sequenceWideModified, modifiedAbilityKeys);
		flowView.renderSequenceElements(currentElements);
	}

	private AbilitySettingsOverrides applyRotationWideOverrides(AbilitySettingsOverrides existingRotationWide,
	                                                           AbilitySettingsOverrides sourceOverrides,
	                                                           AbilityPropertiesDialog.Result result) {
		AbilitySettingsOverrides existing = existingRotationWide != null ? existingRotationWide : AbilitySettingsOverrides.empty();
		AbilitySettingsOverrides source = sourceOverrides != null ? sourceOverrides : AbilitySettingsOverrides.empty();

		AbilitySettingsOverrides.Builder builder = AbilitySettingsOverrides.builder();
		existing.getTypeOverride().ifPresent(builder::type);
		existing.getLevelOverride().ifPresent(builder::level);
		existing.getTriggersGcdOverride().ifPresent(builder::triggersGcd);
		existing.getCastDurationOverride().ifPresent(builder::castDuration);
		existing.getCooldownOverride().ifPresent(builder::cooldown);
		existing.getDetectionThresholdOverride().ifPresent(builder::detectionThreshold);
		existing.getMaskOverride().ifPresent(builder::mask);

		if (result.applyTriggersGcdToAll()) {
			source.getTriggersGcdOverride().ifPresentOrElse(builder::triggersGcd, () -> builder.triggersGcd(null));
		} else if (existing.getTriggersGcdOverride().isPresent()) {
			builder.triggersGcd(null);
		}
		if (result.applyCastDurationToAll()) {
			source.getCastDurationOverride().ifPresentOrElse(builder::castDuration, () -> builder.castDuration(null));
		} else if (existing.getCastDurationOverride().isPresent()) {
			builder.castDuration(null);
		}
		if (result.applyCooldownToAll()) {
			source.getCooldownOverride().ifPresentOrElse(builder::cooldown, () -> builder.cooldown(null));
		} else if (existing.getCooldownOverride().isPresent()) {
			builder.cooldown(null);
		}
		if (result.applyDetectionThresholdToAll()) {
			source.getDetectionThresholdOverride().ifPresentOrElse(builder::detectionThreshold, () -> builder.detectionThreshold(null));
		} else if (existing.getDetectionThresholdOverride().isPresent()) {
			builder.detectionThreshold(null);
		}
		if (result.applyMaskToAll()) {
			source.getMaskOverride().ifPresentOrElse(builder::mask, () -> builder.mask(null));
		} else if (existing.getMaskOverride().isPresent()) {
			builder.mask(null);
		}

		AbilitySettingsOverrides out = builder.build();
		return out.isEmpty() ? null : out;
	}

	private AbilitySettingsOverrides stripAppliedRotationFields(AbilitySettingsOverrides instanceOverrides,
	                                                           AbilityPropertiesDialog.Result result) {
		if (instanceOverrides == null || instanceOverrides.isEmpty()) {
			return null;
		}
		AbilitySettingsOverrides.Builder builder = AbilitySettingsOverrides.builder();
		instanceOverrides.getTypeOverride().ifPresent(builder::type);
		instanceOverrides.getLevelOverride().ifPresent(builder::level);
		if (!result.applyTriggersGcdToAll()) {
			instanceOverrides.getTriggersGcdOverride().ifPresent(builder::triggersGcd);
		}
		if (!result.applyCastDurationToAll()) {
			instanceOverrides.getCastDurationOverride().ifPresent(builder::castDuration);
		}
		if (!result.applyCooldownToAll()) {
			instanceOverrides.getCooldownOverride().ifPresent(builder::cooldown);
		}
		if (!result.applyDetectionThresholdToAll()) {
			instanceOverrides.getDetectionThresholdOverride().ifPresent(builder::detectionThreshold);
		}
		if (!result.applyMaskToAll()) {
			instanceOverrides.getMaskOverride().ifPresent(builder::mask);
		}
		AbilitySettingsOverrides out = builder.build();
		return out.isEmpty() ? null : out;
	}

	private int resolveElementIndexForDrag(AbilityItem item, int abilityIndex) {
//		if (elementIndex >= 0 && elementIndex < currentElements.size()) {
//			SequenceElement candidate = currentElements.get(elementIndex);
//			if (candidate.isAbility() && candidate.getValue().equals(item.getKey())) {
//				return elementIndex;
//			}
//			logger.debug("Element index mismatch:, candidate={}, expectedKey={}", candidate, item.getKey());
//		}

		if (abilityIndex >= 0) {
			int abilityCounter = 0;
			for (int i = 0; i < currentElements.size(); i++) {
				SequenceElement element = currentElements.get(i);
				if (!element.isAbility()) {
					continue;
				}
				if (abilityCounter == abilityIndex) {
					if (!abilityKeyOf(element).equals(item.getKey())) {
						logger.warn("Ability index points to mismatched key: expected={}, found={}, abilityIndex={}",
								item.getKey(), element.getAbilityKey(), abilityIndex);
					}
					return i;
				}
				abilityCounter++;
			}
		}

		for (int i = 0; i < currentElements.size(); i++) {
			SequenceElement element = currentElements.get(i);
			if (element.isAbility() && abilityKeyOf(element).equals(item.getKey())) {
				return i;
			}
		}

		return -1;
	}

	private int resolveTooltipElementIndex(int cardIndex) {
		if (cardIndex < 0) {
			return -1;
		}
		Component[] cards = flowView.getAbilityCardArray();
		if (cardIndex >= 0 && cardIndex < cards.length && cards[cardIndex] instanceof JComponent card) {
			Object rawIndex = card.getClientProperty("elementIndex");
			if (rawIndex instanceof Integer idx) {
				if (idx >= 0 && idx < currentElements.size() && currentElements.get(idx).isTooltip()) {
					return idx;
				}
			}
		}
		for (int i = 0; i < currentElements.size(); i++) {
			if (currentElements.get(i).isTooltip()) {
				return i;
			}
		}
		return -1;
	}

	private String abilityKeyOf(SequenceElement element) {
		if (element == null) {
			return "";
		}
		String abilityKey = element.getResolvedAbilityKey();
		return abilityKey != null ? abilityKey : "";
	}

	private void logAbilityOrder(String label, List<SequenceElement> elements) {
		StringBuilder sb = new StringBuilder(label).append(": ");
		int counter = 0;
		for (int i = 0; i < elements.size(); i++) {
			SequenceElement element = elements.get(i);
			if (!element.isAbility()) {
				continue;
			}
			if (counter > 0) {
				sb.append(" | ");
			}
			String labelValue = element.isAbility() ? element.formatAbilityToken() : element.getValue();
			sb.append(counter).append("->").append(labelValue).append("(elemIdx=").append(i).append(")");
			counter++;
		}
		logger.info(sb.toString());
	}

	interface View {
		void setSequenceName(String name);

		String getSequenceName();

		JComponent asComponent();

		void showRotationSettings(String presetId, RotationConfig.PresetData presetData);
	}
}