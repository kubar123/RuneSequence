package com.lansoftprogramming.runeSequence.ui.presetManager.detail;

import com.lansoftprogramming.runeSequence.infrastructure.config.RotationConfig;
import com.lansoftprogramming.runeSequence.ui.notification.NotificationService;
import com.lansoftprogramming.runeSequence.ui.presetManager.drag.handler.AbilityDragController;
import com.lansoftprogramming.runeSequence.ui.presetManager.drag.model.DragPreviewModel;
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
	private final NotificationService notifications;

	private List<SequenceElement> currentElements;
	private List<SequenceElement> previewElements;
	private final List<SequenceDetailPanel.SaveListener> saveListeners;
	private RotationConfig.PresetData currentPreset;
	private String currentPresetId;
	private String loadedSequenceName;
	private String loadedExpression;
	private List<SequenceElement> loadedElements;
	private List<SequenceElement> originalElementsBeforeDrag;
	private DragPreviewModel currentPreview;
	private boolean isHighlightActive;
	private boolean isDragOutsidePanel;

	SequenceDetailPresenter(SequenceDetailService detailService,
	                        AbilityFlowView flowView,
	                        View view,
	                        NotificationService notifications) {
		this.detailService = detailService;
		this.flowView = flowView;
		this.view = view;
		this.notifications = notifications;
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

	boolean hasUnsavedChanges() {
		String currentName = view.getSequenceName();
		String trimmedCurrentName = currentName != null ? currentName.trim() : "";
		String trimmedLoadedName = loadedSequenceName != null ? loadedSequenceName.trim() : "";

		if (!trimmedCurrentName.equals(trimmedLoadedName)) {
			return true;
		}

		String currentExpression = expressionBuilder.buildExpression(currentElements);
		String loadedExpr = loadedExpression != null ? loadedExpression : "";
		return !currentExpression.equals(loadedExpr);
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
		}

		loadedSequenceName = trimmedName;
		loadedExpression = expression;
		loadedElements = new ArrayList<>(currentElements);

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
	public void onDragStart(AbilityItem item, boolean isFromPalette, int abilityIndex) {
		logger.info("Drag start: key={}, abilityIndex={}", item.getKey(), abilityIndex);
		flowView.resetPreview();
		flowView.setDragOutsidePanel(false);
		isHighlightActive = false;
		isDragOutsidePanel = false;
		// Keep a pristine copy so cancellation can restore the view state.
		originalElementsBeforeDrag = new ArrayList<>(currentElements);
		if (!isFromPalette) {
			int removalIndex = resolveElementIndexForDrag(item, abilityIndex);
			logger.info("Drag start resolved removalIndex={} for key={} (abilityIndex={})",
					removalIndex, item.getKey(), abilityIndex);
			logAbilityOrder("Before removal", currentElements);
			if (removalIndex >= 0) {
				List<SequenceElement> working = new ArrayList<>(currentElements);
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
				} else if (dropPreview != null) {
					currentElements = expressionBuilder.insertAbility(
							new ArrayList<>(previewElements),
							draggedItem.getKey(),
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
		currentPreview = null;
		originalElementsBeforeDrag = null;
		flowView.setDragOutsidePanel(false);
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
					if (!element.getValue().equals(item.getKey())) {
						logger.warn("Ability index points to mismatched key: expected={}, found={}, abilityIndex={}",
								item.getKey(), element.getValue(), abilityIndex);
					}
					return i;
				}
				abilityCounter++;
			}
		}

		for (int i = 0; i < currentElements.size(); i++) {
			SequenceElement element = currentElements.get(i);
			if (element.isAbility() && element.getValue().equals(item.getKey())) {
				return i;
			}
		}

		return -1;
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
			sb.append(counter).append("->").append(element.getValue()).append("(elemIdx=").append(i).append(")");
			counter++;
		}
		logger.info(sb.toString());
	}

	interface View {
		void setSequenceName(String name);

		String getSequenceName();

		JComponent asComponent();
	}
}
