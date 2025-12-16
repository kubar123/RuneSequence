package com.lansoftprogramming.runeSequence.ui.presetManager.drag.handler;

import com.lansoftprogramming.runeSequence.ui.presetManager.drag.DropZoneIndicators;
import com.lansoftprogramming.runeSequence.ui.presetManager.drag.logic.DropPreviewEngine;
import com.lansoftprogramming.runeSequence.ui.presetManager.drag.model.DragPreviewModel;
import com.lansoftprogramming.runeSequence.ui.presetManager.drag.model.DropPreview;
import com.lansoftprogramming.runeSequence.ui.presetManager.drag.model.DropSide;
import com.lansoftprogramming.runeSequence.ui.presetManager.drag.model.DropZoneType;
import com.lansoftprogramming.runeSequence.ui.presetManager.model.SequenceElement;
import com.lansoftprogramming.runeSequence.ui.shared.model.AbilityItem;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.swing.*;
import javax.swing.Timer;
import java.awt.*;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.util.*;
import java.util.List;

/**
 * Orchestrates drag lifecycle: mouse wiring, preview ticking, overlay/indicator coordination.
 */
public class AbilityDragController {

	private static final Logger logger = LoggerFactory.getLogger(AbilityDragController.class);

	private final Timer previewTimer;
	private boolean dragPreviewDirty = false;
	private Point pendingFlowPoint = null;
	private DragPreviewModel lastPreviewModel;
	private DropPreviewEngine.GeometryCache geometryCache;

	private final JPanel flowPanel;
	private final DragCallback callback;

	private final DragOverlay overlay;
	private final DropPreviewEngine previewEngine;
	private final DropIndicatorController indicatorController;

	private DragState currentDrag;
	private boolean releasePhaseLogging = false;

	public interface DragCallback {
		void onDragStart(AbilityItem item, boolean isFromPalette, int abilityIndex);

		void onDragMove(AbilityItem draggedItem, DragPreviewModel previewModel);

		void onDragEnd(AbilityItem draggedItem, boolean commit);

		List<SequenceElement> getCurrentElements();

		Component[] getAllCards();
	}

	public AbilityDragController(JPanel flowPanel, DragCallback callback) {
		this.flowPanel = flowPanel;
		this.callback = callback;
		this.overlay = new DragOverlay();
		this.previewEngine = new DropPreviewEngine();
		this.indicatorController = new DropIndicatorController(new DropZoneIndicators());
		this.previewTimer = new Timer(16, e -> runPreviewTick());
		this.previewTimer.setRepeats(true);
		this.previewTimer.setCoalesce(true);
	}

	public void setDragOutsidePanel(boolean isOutside) {
		overlay.setDragOutsidePanel(isOutside);
	}

	/**
	 * Creates a `MouseAdapter` for draggable cards.
	 * Initiates, tracks, and finalizes drag operations, distinguishing palette cards from sequence elements.
	 */
	public MouseAdapter createCardDragListener(AbilityItem item, JPanel card, boolean isFromPalette) {
		return new MouseAdapter() {
			@Override
			public void mousePressed(MouseEvent e) {
				if (currentDrag != null) {
					if (e.getButton() != currentDrag.getStartButton()) {
						cancelActiveDrag(true);
					}
					return;
				}

				if (!SwingUtilities.isLeftMouseButton(e) && e.getButton() != MouseEvent.NOBUTTON) {
					return;
				}

				int index = -1;
				int elementIndex = -1;
				Component[] cardsSnapshot = callback.getAllCards();
				if (logger.isInfoEnabled()) {
					StringBuilder sb = new StringBuilder("Current cards: ");
					for (int i = 0; i < cardsSnapshot.length; i++) {
						Component c = cardsSnapshot[i];
						Integer elemIdx = extractElementIndex(c);
						String key = extractAbilityKey(c);
						if (i > 0) sb.append(" | ");
						sb.append(i)
								.append("->")
								.append(key != null ? key : "null")
								.append("(elemIdx=")
								.append(elemIdx)
								.append(")");
					}
					logger.info(sb.toString());
				}
				if (!isFromPalette) {
					for (int i = 0; i < cardsSnapshot.length; i++) {
						if (cardsSnapshot[i] == card) {
							index = i;
							break;
						}
					}
					Object raw = card.getClientProperty("elementIndex");
					if (raw instanceof Integer) {
						elementIndex = (Integer) raw;
					}
				}
				logger.info("Start drag detected card index={}, elementIndex={}, abilityKey={}",
						index, elementIndex, item.getKey());
				startDrag(item, card, isFromPalette, index, e.getPoint(), e.getButton());
			}

			@Override
			public void mouseDragged(MouseEvent e) {
				if (currentDrag != null) {
					handleDrag(e);
				}
			}

			@Override
			public void mouseReleased(MouseEvent e) {
				if (currentDrag != null) {
					handleRelease(e);
				}
			}
		};
	}

	private void startDrag(AbilityItem item,
	                       JPanel card,
	                       boolean isFromPalette,
	                       int originalIndex,
	                       Point startPoint,
	                       int startButton) {
		currentDrag = new DragState(item, card, isFromPalette, originalIndex, callback.getCurrentElements(), startButton);

		overlay.startFloating(card, startPoint);
		indicatorController.setGlassPane(overlay.getGlassPane());

		callback.onDragStart(item, isFromPalette, originalIndex);

		refreshGeometryCache();
		lastPreviewModel = null;
		pendingFlowPoint = SwingUtilities.convertPoint(card, startPoint, flowPanel);
		dragPreviewDirty = true;
		if (!previewTimer.isRunning()) {
			previewTimer.start();
		}
		runPreviewTick();
	}

	private void handleDrag(MouseEvent e) {
		Point glassPanePoint = SwingUtilities.convertPoint((Component) e.getSource(), e.getPoint(), overlay.getGlassPane());
		overlay.updateFloatingPosition(glassPanePoint);

		pendingFlowPoint = SwingUtilities.convertPoint((Component) e.getSource(), e.getPoint(), flowPanel);
		releasePhaseLogging = false;
		dragPreviewDirty = true;
	}

	private void runPreviewTick() {
		if (currentDrag == null) {
			previewTimer.stop();
			return;
		}
		if (pendingFlowPoint == null) {
			return;
		}
		if (!dragPreviewDirty && lastPreviewModel != null) {
			return;
		}

		DragPreviewModel previewModel = previewEngine.calculatePreview(
				pendingFlowPoint,
				geometryCache,
				callback.getCurrentElements(),
				currentDrag != null ? currentDrag.getOriginalElements() : null,
				releasePhaseLogging
		);
		dragPreviewDirty = false;
		dispatchPreviewUpdate(previewModel);
	}

	private void dispatchPreviewUpdate(DragPreviewModel previewModel) {
		if (Objects.equals(previewModel, lastPreviewModel)) {
			return;
		}
		lastPreviewModel = previewModel;

		indicatorController.updateIndicators(
				previewModel,
				overlay.isDragOutsidePanel(),
				callback.getAllCards(),
				currentDrag,
				callback::getCurrentElements,
				this::extractElementIndex,
				previewEngine::resolveGroupZoneAt,
				previewEngine::symbolForZone
		);

		if (previewModel != null) {
			callback.onDragMove(currentDrag.getDraggedItem(), previewModel);
		}
	}

	private void handleRelease(MouseEvent e) {
		if (currentDrag != null) {
			int startButton = currentDrag.getStartButton();
			if (e.getButton() == MouseEvent.BUTTON3 || e.getButton() == MouseEvent.BUTTON2) {
				cancelActiveDrag(true);
				return;
			}
			if (startButton != MouseEvent.NOBUTTON && e.getButton() != startButton) {
				cancelActiveDrag(true);
				return;
			}
		}

		Point flowPanelPoint = SwingUtilities.convertPoint((Component) e.getSource(), e.getPoint(), flowPanel);

		pendingFlowPoint = flowPanelPoint;

		releasePhaseLogging = true;
		DragPreviewModel previewModel = previewEngine.calculatePreview(
				flowPanelPoint,
				geometryCache,
				callback.getCurrentElements(),
				currentDrag != null ? currentDrag.getOriginalElements() : null,
				releasePhaseLogging
		);
		releasePhaseLogging = false;
		dispatchPreviewUpdate(previewModel);

		boolean insidePanel = flowPanel.contains(flowPanelPoint);
		DropPreview preview = previewModel != null ? previewModel.getDropPreview() : new DropPreview(0, DropZoneType.NONE, -1, DropSide.LEFT);
		boolean commit = preview.isValid() && insidePanel;

		logger.info(
				"Drop: item={}, fromPalette={}, commit={}, insertIndex={}, zone={}, targetVisualIdx={}, side={}, insidePanel={}",
				String.valueOf(currentDrag.getDraggedItem()),
				currentDrag.isFromPalette(),
				commit,
				preview.getInsertIndex(),
				preview.getZoneType(),
				preview.getTargetAbilityIndex(),
				preview.getDropSide(),
				insidePanel
		);

		cleanup();
		callback.onDragEnd(currentDrag.getDraggedItem(), commit);
		currentDrag = null;
	}

	private Integer extractElementIndex(Component component) {
		if (component instanceof JComponent) {
			Object prop = ((JComponent) component).getClientProperty("elementIndex");
			if (prop instanceof Integer) {
				return (Integer) prop;
			}
		}
		return null;
	}

	private String extractAbilityKey(Component component) {
		if (component instanceof JComponent) {
			Object prop = ((JComponent) component).getClientProperty("abilityKey");
			if (prop instanceof String) {
				return (String) prop;
			}
		}
		return null;
	}

	private DropZoneType extractGroupZoneFromCard(Component card) {
		if (!(card instanceof JComponent)) {
			return null;
		}
		Object prop = ((JComponent) card).getClientProperty("zoneType");
		if (prop instanceof DropZoneType zoneType) {
			return zoneType;
		}
		return null;
	}

	private void cancelActiveDrag(boolean notifyCallback) {
		if (currentDrag == null) {
			return;
		}

		AbilityItem draggedItem = currentDrag.getDraggedItem();
		cleanup();

		if (notifyCallback) {
			callback.onDragEnd(draggedItem, false);
		}

		currentDrag = null;
	}

	private void cleanup() {
		indicatorController.cleanup();
		overlay.cleanup();

		previewTimer.stop();
		dragPreviewDirty = false;
		pendingFlowPoint = null;
		lastPreviewModel = null;
		geometryCache = null;
		releasePhaseLogging = false;
	}

	private void refreshGeometryCache() {
		Component[] allCards = callback.getAllCards();
		List<SequenceElement> elements = callback.getCurrentElements();
		if (elements == null) {
			elements = List.of();
		}

		Map<String, Integer> abilityOccurrences = new HashMap<>();
		List<DropPreviewEngine.CardSnapshot> snapshots = new ArrayList<>();
		int lastVisualIndex = -1;

		for (int idx = 0; idx < allCards.length; idx++) {
			Component c = allCards[idx];

			if (currentDrag != null && c == currentDrag.getDraggedCard()) {
				continue;
			}

			String abilityKey = extractAbilityKey(c);
			if (abilityKey == null) {
				continue;
			}
			int occurrence = abilityOccurrences.getOrDefault(abilityKey, 0);
			abilityOccurrences.put(abilityKey, occurrence + 1);

			int mappedIndex = previewEngine.mapAbilityToPreviewIndex(elements, abilityKey, occurrence);
			if (mappedIndex < 0) {
				continue;
			}

			Rectangle boundsInFlow = SwingUtilities.convertRectangle(c.getParent(), c.getBounds(), flowPanel);
			DropZoneType groupZone = extractGroupZoneFromCard(c);
			snapshots.add(new DropPreviewEngine.CardSnapshot(boundsInFlow, mappedIndex, idx, groupZone, abilityKey));
			lastVisualIndex = idx;
		}

		geometryCache = new DropPreviewEngine.GeometryCache(snapshots, lastVisualIndex);
	}
}
