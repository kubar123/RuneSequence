package com.lansoftprogramming.runeSequence.ui.presetManager.detail;

import com.lansoftprogramming.runeSequence.ui.presetManager.drag.handler.AbilityDragController;
import com.lansoftprogramming.runeSequence.ui.presetManager.drag.model.DropPreview;
import com.lansoftprogramming.runeSequence.ui.presetManager.drag.model.DropZoneType;
import com.lansoftprogramming.runeSequence.ui.presetManager.model.SequenceElement;
import com.lansoftprogramming.runeSequence.ui.shared.component.WrapLayout;
import com.lansoftprogramming.runeSequence.ui.shared.model.AbilityItem;

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
	private final Color defaultBackground;
	private final Border defaultBorder;
	private final JPanel emptyDropIndicator;

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

	void renderSequenceElements(List<SequenceElement> elements) {
		hideEmptyDropIndicator();
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
		setBackground(defaultBackground);
		setBorder(defaultBorder);
		hideEmptyDropIndicator();

		for (Component card : getAbilityCardArray()) {
			if (card instanceof JPanel) {
				card.setBackground(Color.WHITE);
				((JPanel) card).setBorder(BorderFactory.createLineBorder(Color.LIGHT_GRAY));
			}
		}
		repaint();
	}

	boolean highlightDropZone(DropPreview preview) {
		if (preview == null || preview.getZoneType() == null) {
			return false;
		}

		Component[] cards = getAbilityCardArray();
		int targetIndex = preview.getTargetAbilityIndex();

		if (cards.length == 0) {
			highlightEmptyPanel(preview.getZoneType());
			return true;
		}

		hideEmptyDropIndicator();

		if (targetIndex >= 0 && targetIndex < cards.length && cards[targetIndex] instanceof JPanel targetCard) {
			Color highlightColor = getHighlightColor(preview.getZoneType());
			targetCard.setBackground(highlightColor);
			targetCard.setBorder(BorderFactory.createLineBorder(highlightColor.darker(), 2));
			repaint();
			return true;
		}

		return false;
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

	private void highlightEmptyPanel(DropZoneType zoneType) {
		Color highlightColor = getHighlightColor(zoneType);
		showEmptyDropIndicator(highlightColor);
		repaint();
	}

	private Color getHighlightColor(DropZoneType zoneType) {
		if (zoneType == null) {
			return Color.WHITE;
		}

		return switch (zoneType) {
			case AND -> new Color(170, 255, 171);
			case OR -> new Color(158, 99, 220);
			case NEXT -> new Color(250, 117, 159);
			default -> Color.WHITE;
		};
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
		if (emptyDropIndicator.getParent() != this) {
			add(emptyDropIndicator, 0);
		}
		emptyDropIndicator.setVisible(true);
		revalidate();
		repaint();
	}

	private void hideEmptyDropIndicator() {
		if (emptyDropIndicator.getParent() == this) {
			remove(emptyDropIndicator);
			revalidate();
		}
		emptyDropIndicator.setVisible(false);
		repaint();
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
}
