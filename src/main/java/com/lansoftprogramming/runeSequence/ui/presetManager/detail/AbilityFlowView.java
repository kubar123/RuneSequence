package com.lansoftprogramming.runeSequence.ui.presetManager.detail;

import com.lansoftprogramming.runeSequence.ui.presetManager.drag.handler.AbilityDragController;
import com.lansoftprogramming.runeSequence.ui.presetManager.drag.model.DropPreview;
import com.lansoftprogramming.runeSequence.ui.presetManager.model.SequenceElement;
import com.lansoftprogramming.runeSequence.ui.shared.component.WrapLayout;
import com.lansoftprogramming.runeSequence.ui.shared.model.AbilityItem;

import javax.swing.*;
import java.awt.*;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.util.ArrayList;
import java.util.List;

class AbilityFlowView extends JPanel {
	private final SequenceDetailService detailService;
	private AbilityDragController dragController;
	private AbilityCardFactory cardFactory;

	AbilityFlowView(SequenceDetailService detailService) {
		super(new WrapLayout(FlowLayout.LEFT, 10, 10));
		this.detailService = detailService;
	}

	void attachDragController(AbilityDragController.DragCallback callback) {
		this.dragController = new AbilityDragController(this, callback);
		this.cardFactory = new AbilityCardFactory(dragController);
	}

	void renderSequenceElements(List<SequenceElement> elements) {
		removeAll();

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
			}

			index++;
		}

		revalidate();
		repaint();
	}

	void clearHighlights() {
		for (Component card : getAbilityCardArray()) {
			if (card instanceof JPanel) {
				card.setBackground(Color.WHITE);
				((JPanel) card).setBorder(BorderFactory.createLineBorder(Color.LIGHT_GRAY));
			}
		}
		repaint();
	}

	void highlightDropZone(DropPreview preview) {
		Component[] cards = getAbilityCardArray();
		int targetIndex = preview.getTargetAbilityIndex();

		if (targetIndex >= 0 && targetIndex < cards.length && cards[targetIndex] instanceof JPanel) {
			JPanel targetCard = (JPanel) cards[targetIndex];

			Color highlightColor;
			switch (preview.getZoneType()) {
				case AND:
					highlightColor = new Color(170, 255, 171);
					break;
				case OR:
					highlightColor = new Color(158, 99, 220);
					break;
				case NEXT:
					highlightColor = new Color(250, 117, 159);
					break;
				default:
					highlightColor = Color.WHITE;
			}

			targetCard.setBackground(highlightColor);
			targetCard.setBorder(BorderFactory.createLineBorder(highlightColor.darker(), 2));
			repaint();
		}
	}

	Component[] getAbilityCardArray() {
		List<Component> cards = new ArrayList<>();
		for (Component component : getComponents()) {
			collectAbilityCardsRecursive(component, cards);
		}
		return cards.toArray(new Component[0]);
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

	private int renderAbilityGroup(List<SequenceElement> elements, int startIndex, SequenceElement.Type groupType) {
		Color groupColor = groupType == SequenceElement.Type.PLUS
				? new Color(220, 255, 223)
				: new Color(196, 163, 231);

		AbilityGroupPanel groupPanel = new AbilityGroupPanel(groupColor);

		SequenceElement firstElement = elements.get(startIndex);
		AbilityItem firstItem = detailService.createAbilityItem(firstElement.getValue());
		if (firstItem != null) {
			JPanel card = cardFactory.createAbilityCard(firstItem);
			card.putClientProperty("elementIndex", startIndex);
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
			add(card);
		}
	}

	private void collectAbilityCardsRecursive(Component component, List<Component> out) {
		if (component == null) {
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
}
