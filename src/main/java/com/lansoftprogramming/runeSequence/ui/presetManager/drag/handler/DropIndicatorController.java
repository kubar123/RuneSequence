package com.lansoftprogramming.runeSequence.ui.presetManager.drag.handler;

import com.lansoftprogramming.runeSequence.ui.presetManager.drag.DropZoneIndicators;
import com.lansoftprogramming.runeSequence.ui.presetManager.drag.model.DragPreviewModel;
import com.lansoftprogramming.runeSequence.ui.presetManager.drag.model.DropPreview;
import com.lansoftprogramming.runeSequence.ui.presetManager.drag.model.DropSide;
import com.lansoftprogramming.runeSequence.ui.presetManager.drag.model.DropZoneType;
import com.lansoftprogramming.runeSequence.ui.presetManager.model.SequenceElement;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.swing.*;
import java.awt.*;
import java.util.List;
import java.util.Objects;
import java.util.function.BiFunction;
import java.util.function.Function;
import java.util.function.Supplier;

/**
 * Controls drop zone indicators and deduplicates indicator-related logging.
 */
public class DropIndicatorController {

	private static final Logger logger = LoggerFactory.getLogger(DropIndicatorController.class);

	private final DropZoneIndicators indicators;

	private Integer lastIndicatorVisualIdx = null;
	private DropSide lastIndicatorDropSide = null;
	private DropZoneType lastIndicatorZoneType = null;
	private boolean hasLoggedIndicatorShown = false;

	public DropIndicatorController(DropZoneIndicators indicators) {
		this.indicators = indicators;
	}

	public void setGlassPane(JComponent glassPane) {
		indicators.setGlassPane(glassPane);
	}

	public void updateIndicators(DragPreviewModel previewModel,
	                             boolean isDragOutsidePanel,
	                             Component[] allCards,
	                             DragState currentDrag,
	                             Supplier<List<SequenceElement>> currentElementsSupplier,
	                             Function<Component, Integer> elementIndexExtractor,
	                             BiFunction<List<SequenceElement>, Integer, DropZoneType> groupZoneResolver,
	                             Function<DropZoneType, String> symbolResolver) {
		if (isDragOutsidePanel || previewModel == null || !previewModel.isValid()) {
			indicators.hideIndicators();
			resetIndicatorState();
			return;
		}

		if (allCards == null) {
			allCards = new Component[0];
		}

		DropPreview preview = previewModel.getDropPreview();

		if (allCards.length == 0) {
			indicators.hideIndicators();

			if (!hasLoggedIndicatorShown || lastIndicatorVisualIdx == null || lastIndicatorVisualIdx != -1
					|| lastIndicatorZoneType != preview.getZoneType()) {
				logger.info("Highlighting empty panel, zone={}", preview.getZoneType());
				hasLoggedIndicatorShown = true;
			}

			lastIndicatorVisualIdx = -1;
			lastIndicatorDropSide = preview.getDropSide();
			lastIndicatorZoneType = preview.getZoneType();
			return;
		}

		int targetVisualIndex = preview.getTargetAbilityIndex();

		if (targetVisualIndex < 0 || targetVisualIndex >= allCards.length) {
			indicators.hideIndicators();
			resetIndicatorState();
			return;
		}

		Component targetCard = allCards[targetVisualIndex];

		Component leftCard = null;
		Component rightCard = null;

		List<SequenceElement> elementSnapshot = currentElementsSupplier.get();

		int leftBoundaryVisual = targetVisualIndex;
		int rightBoundaryVisual = targetVisualIndex;

		Integer targetElementIndex = elementIndexExtractor.apply(targetCard);
		if (targetElementIndex != null && elementSnapshot != null && !elementSnapshot.isEmpty()) {
			leftBoundaryVisual = expandBoundaryLeft(allCards, targetVisualIndex, targetElementIndex, elementSnapshot, elementIndexExtractor);
			rightBoundaryVisual = expandBoundaryRight(allCards, targetVisualIndex, targetElementIndex, elementSnapshot, elementIndexExtractor);
		}

		if (preview.getDropSide() == DropSide.LEFT) {
			rightCard = allCards[Math.max(0, leftBoundaryVisual)];
			if (leftBoundaryVisual > 0) {
				leftCard = allCards[leftBoundaryVisual - 1];
			}
		} else {
			leftCard = allCards[Math.min(allCards.length - 1, rightBoundaryVisual)];
			if (rightBoundaryVisual + 1 < allCards.length) {
				rightCard = allCards[rightBoundaryVisual + 1];
			}
		}

		indicators.showInsertionLineBetweenCards(leftCard, rightCard, preview.getZoneType(), preview.getDropSide());

		if (!hasLoggedIndicatorShown) {
			logger.info("Indicator shown at visualIdx={}, side={}, zone={}",
					targetVisualIndex, preview.getDropSide(), preview.getZoneType());
			hasLoggedIndicatorShown = true;
			lastIndicatorVisualIdx = targetVisualIndex;
			lastIndicatorDropSide = preview.getDropSide();
			lastIndicatorZoneType = preview.getZoneType();
		} else if (!targetVisualIndexEquals(targetVisualIndex)
				|| lastIndicatorDropSide != preview.getDropSide()
				|| lastIndicatorZoneType != preview.getZoneType()) {
			logger.info("Indicator moved to visualIdx={}, side={}, zone={}",
					targetVisualIndex, preview.getDropSide(), preview.getZoneType());
			lastIndicatorVisualIdx = targetVisualIndex;
			lastIndicatorDropSide = preview.getDropSide();
			lastIndicatorZoneType = preview.getZoneType();
		}

		if (preview.getZoneType() == DropZoneType.NEXT) {
			return;
		}

		DropZoneType indicatorZone = extractGroupZoneFromCard(targetCard);
		if (indicatorZone == DropZoneType.NEXT) {
			indicatorZone = null;
		}

		if (indicatorZone == null) {
			Integer elementIdx = elementIndexExtractor.apply(targetCard);
			if (elementIdx == null) {
				return;
			}

			indicatorZone = groupZoneResolver.apply(elementSnapshot, elementIdx);
		}

		String groupSymbol = symbolResolver.apply(indicatorZone);
		String topSymbol = previewModel.isShowTopButton()
				? (groupSymbol != null ? groupSymbol : "+")
				: null;
		String bottomSymbol = previewModel.isShowBottomButton()
				? (groupSymbol != null ? groupSymbol : "/")
				: null;

		indicators.showIndicators(targetCard, topSymbol, bottomSymbol);
	}

	private int expandBoundaryRight(Component[] cards,
	                                int startVisualIndex,
	                                int startElementIndex,
	                                List<SequenceElement> elements,
	                                Function<Component, Integer> elementIndexExtractor) {
		int visual = startVisualIndex;
		int elementIndex = startElementIndex;
		while (visual + 1 < cards.length) {
			Integer nextElementIndex = elementIndexExtractor.apply(cards[visual + 1]);
			if (nextElementIndex == null || nextElementIndex != elementIndex + 1) {
				break;
			}
			if (nextElementIndex < 0 || nextElementIndex >= elements.size() || !elements.get(nextElementIndex).isTooltip()) {
				break;
			}
			visual++;
			elementIndex = nextElementIndex;
		}
		return visual;
	}

	private int expandBoundaryLeft(Component[] cards,
	                               int startVisualIndex,
	                               int startElementIndex,
	                               List<SequenceElement> elements,
	                               Function<Component, Integer> elementIndexExtractor) {
		int visual = startVisualIndex;
		int elementIndex = startElementIndex;
		while (visual - 1 >= 0) {
			Integer prevElementIndex = elementIndexExtractor.apply(cards[visual - 1]);
			if (prevElementIndex == null || prevElementIndex != elementIndex - 1) {
				break;
			}
			if (prevElementIndex < 0 || prevElementIndex >= elements.size() || !elements.get(prevElementIndex).isTooltip()) {
				break;
			}
			visual--;
			elementIndex = prevElementIndex;
		}
		return visual;
	}

	public void cleanup() {
		resetIndicatorState();
		indicators.cleanup();
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

	private boolean targetVisualIndexEquals(Integer idx) {
		return lastIndicatorVisualIdx != null && Objects.equals(lastIndicatorVisualIdx, idx);
	}

	private void resetIndicatorState() {
		lastIndicatorVisualIdx = null;
		lastIndicatorDropSide = null;
		lastIndicatorZoneType = null;
		hasLoggedIndicatorShown = false;
	}
}
