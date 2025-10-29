package com.lansoftprogramming.runeSequence.ui.presetManager.detail;

import com.lansoftprogramming.runeSequence.infrastructure.config.RotationConfig;
import com.lansoftprogramming.runeSequence.ui.presetManager.drag.handler.AbilityDragController;
import com.lansoftprogramming.runeSequence.ui.presetManager.drag.model.DropPreview;
import com.lansoftprogramming.runeSequence.ui.presetManager.model.SequenceElement;
import com.lansoftprogramming.runeSequence.ui.presetManager.service.ExpressionBuilder;
import com.lansoftprogramming.runeSequence.ui.shared.model.AbilityItem;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.swing.*;
import java.awt.*;
import java.util.ArrayList;
import java.util.List;

class SequenceDetailPresenter implements AbilityDragController.DragCallback {
	private static final Logger logger = LoggerFactory.getLogger(SequenceDetailPresenter.class);

	private final SequenceDetailService detailService;
	private final AbilityFlowView flowView;
	private final View view;
	private final ExpressionBuilder expressionBuilder;

	private List<SequenceElement> currentElements;
	private List<SequenceElement> previewElements;
	private final List<SequenceDetailPanel.SaveListener> saveListeners;
	private RotationConfig.PresetData currentPreset;
	private String currentPresetId;
	private String loadedSequenceName;
	private String loadedExpression;
	private List<SequenceElement> loadedElements;
	private DropPreview currentPreview;
	private boolean isHighlightActive;
	private boolean isDragOutsidePanel;

	SequenceDetailPresenter(SequenceDetailService detailService, AbilityFlowView flowView, View view) {
		this.detailService = detailService;
		this.flowView = flowView;
		this.view = view;
		this.expressionBuilder = new ExpressionBuilder();
		this.currentElements = new ArrayList<>();
		this.previewElements = new ArrayList<>();
		this.saveListeners = new ArrayList<>();
		this.flowView.attachDragController(this);
		this.loadedSequenceName = "";
		this.loadedExpression = "";
		this.loadedElements = new ArrayList<>();
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
		view.setSequenceName(presetData.getName());

		loadedSequenceName = presetData.getName() != null ? presetData.getName() : "";
		loadedExpression = presetData.getExpression() != null ? presetData.getExpression() : "";

		List<SequenceElement> parsedElements = detailService.parseSequenceExpression(loadedExpression);
		loadedElements = parsedElements != null ? new ArrayList<>(parsedElements) : new ArrayList<>();
		currentElements = new ArrayList<>(loadedElements);
		previewElements = new ArrayList<>(loadedElements);
		flowView.renderSequenceElements(currentElements);

		long abilityCount = currentElements.stream().filter(SequenceElement::isAbility).count();
		logger.debug("Loaded sequence: {} with {} abilities", presetData.getName(), abilityCount);
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
		flowView.renderSequenceElements(currentElements);
	}

	void saveSequence() {
		String sequenceName = view.getSequenceName();
		String trimmedName = sequenceName != null ? sequenceName.trim() : "";

		String expression = expressionBuilder.buildExpression(currentElements);

		SequenceDetailService.SaveOutcome outcome = detailService.saveSequence(
				currentPresetId,
				currentPreset,
				trimmedName,
				expression
		);

		if (!outcome.isSuccess()) {
			String message = outcome.getMessage() != null ? outcome.getMessage() : "Failed to save sequence.";
			int messageType = outcome.isValidationFailure()
					? JOptionPane.WARNING_MESSAGE
					: JOptionPane.ERROR_MESSAGE;

			logger.debug("Save attempt failed: {}", message);
			view.showSaveDialog(message, messageType);
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
		}

		loadedSequenceName = trimmedName;
		loadedExpression = expression;
		loadedElements = new ArrayList<>(currentElements);

		view.setSequenceName(trimmedName);
		notifySaveListeners(result);

		String successMessage = outcome.getMessage() != null ? outcome.getMessage() : "Sequence saved successfully.";
		view.showSaveDialog(successMessage, JOptionPane.INFORMATION_MESSAGE);
	}

	void discardChanges() {
		if (currentPreset != null) {
			currentPreset.setName(loadedSequenceName);
			currentPreset.setExpression(loadedExpression);
		}

		currentElements = new ArrayList<>(loadedElements);
		previewElements = new ArrayList<>(loadedElements);
		view.setSequenceName(loadedSequenceName);
		flowView.renderSequenceElements(currentElements);
	}

	void startPaletteDrag(AbilityItem item, JPanel card, Point startPoint) {
		flowView.startPaletteDrag(item, card, startPoint);
	}

	@Override
	public void onDragStart(AbilityItem item, boolean isFromPalette) {
		if (!isFromPalette) {
			previewElements = expressionBuilder.removeAbility(new ArrayList<>(currentElements), item.getKey());
		} else {
			previewElements = new ArrayList<>(currentElements);
		}
	}

	@Override
	public void onDragMove(AbilityItem draggedItem, Point cursorPos, DropPreview preview) {
		currentPreview = preview;
		flowView.clearHighlights();
		isHighlightActive = false;

		Point detailPanelPoint = SwingUtilities.convertPoint(flowView, cursorPos, view.asComponent());
		isDragOutsidePanel = !view.asComponent().contains(detailPanelPoint);

		flowView.setDragOutsidePanel(isDragOutsidePanel);

		if (preview.isValid() && preview.getTargetAbilityIndex() >= 0) {
			flowView.highlightDropZone(preview);
			isHighlightActive = true;
		}
	}

	@Override
	public void onDragEnd(AbilityItem draggedItem, boolean commit) {
		flowView.clearHighlights();

		boolean isFromSequence = !previewElements.equals(currentElements);

		if (commit) {
			if (isHighlightActive && currentPreview != null && currentPreview.isValid()) {
				currentElements = expressionBuilder.insertAbility(
						new ArrayList<>(previewElements),
						draggedItem.getKey(),
						currentPreview.getInsertIndex(),
						currentPreview.getZoneType(),
						currentPreview.getDropSide()
				);
				updateExpression();
			} else {
				currentElements = new ArrayList<>(currentElements);
				previewElements = new ArrayList<>(currentElements);
			}
		} else {
			if (isFromSequence) {
				currentElements = new ArrayList<>(previewElements);
				updateExpression();
			} else {
				previewElements = new ArrayList<>(currentElements);
			}
		}

		isHighlightActive = false;
		isDragOutsidePanel = false;
		currentPreview = null;
		flowView.renderSequenceElements(currentElements);
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
			logger.debug("Updated expression: {}", newExpression);
		}
	}

	interface View {
		void setSequenceName(String name);

		String getSequenceName();

		void showSaveDialog(String message, int messageType);

		JComponent asComponent();
	}
}
