package com.lansoftprogramming.runeSequence.ui.presetManager.detail;

import com.lansoftprogramming.runeSequence.ui.presetManager.drag.handler.AbilityDragController;
import com.lansoftprogramming.runeSequence.ui.presetManager.drag.model.DragPreviewModel;
import com.lansoftprogramming.runeSequence.ui.presetManager.drag.model.DropPreview;
import com.lansoftprogramming.runeSequence.ui.presetManager.drag.model.DropZoneType;
import com.lansoftprogramming.runeSequence.ui.presetManager.model.SequenceElement;
import com.lansoftprogramming.runeSequence.ui.shared.component.WrapLayout;
import com.lansoftprogramming.runeSequence.ui.shared.model.AbilityItem;
import com.lansoftprogramming.runeSequence.ui.theme.UiColorPalette;

import javax.swing.*;
import javax.swing.border.Border;
import java.awt.*;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.util.ArrayList;
import java.util.List;

class AbilityFlowView extends JPanel {
	private final SequenceDetailService detailService;
	private AbilityDragController dragController;
	private AbilityCardFactory cardFactory;
	private TooltipEditHandler tooltipEditHandler;
	private final Color defaultBackground;
	private final Border defaultBorder;
	private final JPanel emptyDropIndicator;
	private Component[] cachedAbilityCards;
	private DragPreviewModel activePreview;

	AbilityFlowView(SequenceDetailService detailService) {
		super(new WrapLayout(FlowLayout.LEFT, 10, 10));
		this.detailService = detailService;
		this.defaultBackground = getBackground();
		this.defaultBorder = getBorder();
		this.emptyDropIndicator = createEmptyDropIndicator();
	}

	void attachDragController(AbilityDragController.DragCallback callback) {
		this.dragController = new AbilityDragController(this, callback);
		this.cardFactory = new AbilityCardFactory(dragController);
	}

	void setTooltipEditHandler(TooltipEditHandler tooltipEditHandler) {
		this.tooltipEditHandler = tooltipEditHandler;
	}

	void renderSequenceElements(List<SequenceElement> elements) {
		hideEmptyDropIndicator();
		removeAll();
		activePreview = null;
		cachedAbilityCards = null;

		int index = 0;
		while (index < elements.size()) {
			SequenceElement element = elements.get(index);

			if (element.isAbility()) {
				if (index + 1 < elements.size()) {
					SequenceElement next = elements.get(index + 1);
					if (next.isPlus() || next.isSlash()) {
						index = renderAbilityGroup(elements, index, next.getType());
						index++;
						continue;
					}
				}
				addStandaloneAbility(element, index);
			} else if (element.isSeparator()) {
				add(cardFactory.createSeparatorLabel(element));
			} else if (element.isTooltip()) {
				addTooltipCard(element, index);
			}

			index++;
		}

		revalidate();
		repaint();
		cachedAbilityCards = getAbilityCardArray();
	}

	void clearHighlights() {
		resetPreview();
	}

	boolean applyPreviewModel(DragPreviewModel previewModel) {
		Component[] cards = getAbilityCardArray();
		clearPreviewHighlight(cards, activePreview);
		activePreview = previewModel;

		if (previewModel == null || previewModel.getDropPreview() == null || !previewModel.isValid()) {
			hideEmptyDropIndicator();
			return false;
		}

		DropPreview preview = previewModel.getDropPreview();

		if (cards.length == 0) {
			highlightEmptyPanel(preview.getZoneType());
			return true;
		}

		hideEmptyDropIndicator();

		int targetIndex = preview.getTargetAbilityIndex();
		if (targetIndex >= 0 && targetIndex < cards.length && cards[targetIndex] instanceof JPanel targetCard) {
			Color highlightColor = getHighlightColor(preview.getZoneType());
			targetCard.setBackground(highlightColor);
			targetCard.setBorder(BorderFactory.createLineBorder(highlightColor.darker(), 2));
			repaint(targetCard.getBounds());
			return true;
		}

		return false;
	}

	void resetPreview() {
		Component[] cards = getAbilityCardArray();
		clearPreviewHighlight(cards, activePreview);
		activePreview = null;
		setBackground(defaultBackground);
		setBorder(defaultBorder);
		hideEmptyDropIndicator();
	}

	private void clearPreviewHighlight(Component[] cards, DragPreviewModel previewModel) {
		if (previewModel == null || previewModel.getDropPreview() == null) {
			return;
		}
		int previousIndex = previewModel.getDropPreview().getTargetAbilityIndex();
		if (previousIndex >= 0 && previousIndex < cards.length && cards[previousIndex] instanceof JPanel previousCard) {
			previousCard.setBackground(UiColorPalette.UI_CARD_BACKGROUND);
			previousCard.setBorder(UiColorPalette.CARD_BORDER);
			repaint(previousCard.getBounds());
		}
	}

	Component[] getAbilityCardArray() {
		if (cachedAbilityCards != null) {
			return cachedAbilityCards;
		}
		List<Component> cards = new ArrayList<>();
		for (Component component : getComponents()) {
			collectAbilityCardsRecursive(component, cards);
		}
		cachedAbilityCards = cards.toArray(new Component[0]);
		return cachedAbilityCards;
	}

	void setDragOutsidePanel(boolean outsidePanel) {
		if (dragController != null) {
			dragController.setDragOutsidePanel(outsidePanel);
		}
	}

	void startPaletteDrag(AbilityItem item, JPanel card, Point startPoint) {
		if (dragController == null) {
			return;
		}

		MouseAdapter dragListener = dragController.createCardDragListener(item, card, true);

		MouseEvent syntheticEvent = new MouseEvent(
				card,
				MouseEvent.MOUSE_PRESSED,
				System.currentTimeMillis(),
				0,
				startPoint.x,
				startPoint.y,
				1,
				false
		);
		dragListener.mousePressed(syntheticEvent);

		card.addMouseMotionListener(dragListener);
		card.addMouseListener(new MouseAdapter() {
			@Override
			public void mouseReleased(MouseEvent e) {
				dragListener.mouseReleased(e);
				card.removeMouseMotionListener(dragListener);
				card.removeMouseListener(this);
			}
		});
	}

	MouseAdapter createPaletteDragListener(AbilityItem item, JPanel card) {
		if (dragController == null) {
			return null;
		}
		return dragController.createCardDragListener(item, card, true);
	}

	private int renderAbilityGroup(List<SequenceElement> elements, int startIndex, SequenceElement.Type groupType) {
		Color groupColor = groupType == SequenceElement.Type.PLUS
				? UiColorPalette.ABILITY_GROUP_AND_BACKGROUND
				: UiColorPalette.ABILITY_GROUP_OR_BACKGROUND;

		AbilityGroupPanel groupPanel = new AbilityGroupPanel(groupColor);

		SequenceElement firstElement = elements.get(startIndex);
		AbilityItem firstItem = detailService.createAbilityItem(firstElement.getValue());
		if (firstItem != null) {
			JPanel card = cardFactory.createAbilityCard(firstItem);
			card.putClientProperty("elementIndex", startIndex);
			card.putClientProperty("zoneType", zoneForGroupType(groupType));
			groupPanel.add(card);
		}

		int currentIndex = startIndex + 1;
		while (currentIndex < elements.size()) {
			SequenceElement separator = elements.get(currentIndex);

			if (separator.getType() != groupType) {
				break;
			}

			groupPanel.add(cardFactory.createSeparatorLabel(separator));

			if (currentIndex + 1 < elements.size() && elements.get(currentIndex + 1).isAbility()) {
				int abilityElementIndex = currentIndex + 1;
				SequenceElement abilityElement = elements.get(abilityElementIndex);
				AbilityItem abilityItem = detailService.createAbilityItem(abilityElement.getValue());
				if (abilityItem != null) {
					JPanel abilityCard = cardFactory.createAbilityCard(abilityItem);
					abilityCard.putClientProperty("elementIndex", abilityElementIndex);
					abilityCard.putClientProperty("zoneType", zoneForGroupType(groupType));
					groupPanel.add(abilityCard);
				}
				currentIndex += 2;
			} else {
				break;
			}
		}

		add(groupPanel);
		return currentIndex - 1;
	}

	private void addStandaloneAbility(SequenceElement element, int elementIndex) {
		AbilityItem item = detailService.createAbilityItem(element.getValue());
		if (item != null) {
			JPanel card = cardFactory.createAbilityCard(item);
			card.putClientProperty("elementIndex", elementIndex);
			card.putClientProperty("zoneType", null);
			add(card);
		}
	}

	private void addTooltipCard(SequenceElement element, int elementIndex) {
		JPanel card = cardFactory.createTooltipCard(element.getValue());
		card.putClientProperty("elementIndex", elementIndex);
		card.putClientProperty("zoneType", null);
		if (tooltipEditHandler != null) {
			card.addMouseListener(new MouseAdapter() {
				@Override
				public void mouseClicked(MouseEvent e) {
					if (e.getClickCount() == 2 && SwingUtilities.isLeftMouseButton(e)) {
						Object rawIndex = card.getClientProperty("elementIndex");
						if (rawIndex instanceof Integer idx) {
							tooltipEditHandler.editTooltipAt(idx);
						}
					}
				}
			});
		}
		add(card);
	}

	private void highlightEmptyPanel(DropZoneType zoneType) {
		Color highlightColor = getHighlightColor(zoneType);
		showEmptyDropIndicator(highlightColor);
		repaint();
	}

	private Color getHighlightColor(DropZoneType zoneType) {
		return UiColorPalette.getDropZoneHighlightColor(zoneType);
	}

	private JPanel createEmptyDropIndicator() {
		JPanel indicator = new JPanel();
		indicator.setName("emptyDropIndicator");
		indicator.setOpaque(true);
		indicator.setPreferredSize(new Dimension(50, 68));
		indicator.setMinimumSize(new Dimension(50, 68));
		indicator.setMaximumSize(new Dimension(50, 68));
		indicator.setVisible(false);
		return indicator;
	}

	private void showEmptyDropIndicator(Color highlightColor) {
		emptyDropIndicator.setBackground(highlightColor);
		emptyDropIndicator.setBorder(BorderFactory.createLineBorder(highlightColor.darker(), 2));
		boolean added = false;
		if (emptyDropIndicator.getParent() != this) {
			add(emptyDropIndicator, 0);
			added = true;
		}
		boolean visibilityChanged = !emptyDropIndicator.isVisible();
		emptyDropIndicator.setVisible(true);
		if (added || visibilityChanged) {
			revalidate();
		}
		repaint(emptyDropIndicator.getBounds());
	}

	private void hideEmptyDropIndicator() {
		boolean removed = false;
		if (emptyDropIndicator.getParent() == this) {
			remove(emptyDropIndicator);
			removed = true;
		}
		boolean visibilityChanged = emptyDropIndicator.isVisible();
		emptyDropIndicator.setVisible(false);
		if (removed || visibilityChanged) {
			revalidate();
		}
		repaint(emptyDropIndicator.getBounds());
	}

	private void collectAbilityCardsRecursive(Component component, List<Component> out) {
		if (component == null) {
			return;
		}
		if (component == emptyDropIndicator) {
			return;
		}
		if (component instanceof JPanel && "abilityCard".equals(component.getName())) {
			out.add(component);
			return;
		}
		if (component instanceof Container) {
			for (Component child : ((Container) component).getComponents()) {
				collectAbilityCardsRecursive(child, out);
			}
		}
	}

	// Bridges domain separator type to drag/drop zone semantics so UI and controller stay aligned.
	private DropZoneType zoneForGroupType(SequenceElement.Type groupType) {
		if (groupType == SequenceElement.Type.PLUS) {
			return DropZoneType.AND;
		}
		if (groupType == SequenceElement.Type.SLASH) {
			return DropZoneType.OR;
		}
		return null;
	}
}
