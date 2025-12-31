package com.lansoftprogramming.runeSequence.ui.presetManager.detail;

import com.lansoftprogramming.runeSequence.ui.presetManager.drag.handler.AbilityDragController;
import com.lansoftprogramming.runeSequence.ui.presetManager.drag.model.DragPreviewModel;
import com.lansoftprogramming.runeSequence.ui.presetManager.drag.model.DropPreview;
import com.lansoftprogramming.runeSequence.ui.presetManager.drag.model.DropZoneType;
import com.lansoftprogramming.runeSequence.ui.presetManager.model.SequenceElement;
import com.lansoftprogramming.runeSequence.ui.presetManager.model.SequenceGrouping;
import com.lansoftprogramming.runeSequence.ui.shared.component.HoverGlowContainerPanel;
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
import java.util.Set;
import java.util.function.IntConsumer;

import static com.lansoftprogramming.runeSequence.ui.presetManager.model.SequenceElementNavigation.nextNonTooltipIndex;

class AbilityFlowView extends HoverGlowContainerPanel implements Scrollable {
	private final SequenceDetailService detailService;
	private AbilityDragController dragController;
	private AbilityCardFactory cardFactory;
	private TooltipEditHandler tooltipEditHandler;
	private IntConsumer abilityPropertiesHandler;
	private IntConsumer specModifierHandler;
	private final Color defaultBackground;
	private final Border defaultBorder;
	private final JPanel emptyDropIndicator;
	private Component[] cachedAbilityCards;
	private DragPreviewModel activePreview;
	private List<JComponent> highlightedCards;
	private List<SequenceElement> lastRenderedElements;
	private boolean sequenceWideModified;
	private Set<String> modifiedAbilityKeys;

	AbilityFlowView(SequenceDetailService detailService) {
		super(new WrapLayout(FlowLayout.LEFT, 10, 10), component -> component instanceof JComponent jc && "abilityCard".equals(jc.getName()));
		this.detailService = detailService;
		this.defaultBackground = getBackground();
		this.defaultBorder = getBorder();
		this.emptyDropIndicator = createEmptyDropIndicator();
		this.highlightedCards = List.of();
		this.lastRenderedElements = List.of();
		this.sequenceWideModified = false;
		this.modifiedAbilityKeys = Set.of();
	}

	@Override
	protected void paintComponent(Graphics g) {
		super.paintComponent(g);

		if (!shouldShowEmptyHint()) {
			return;
		}

		Graphics2D g2 = (Graphics2D) g.create();
		try {
			g2.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
			g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
			paintEmptyHint(g2);
		} finally {
			g2.dispose();
		}
	}

	@Override
	public Dimension getPreferredScrollableViewportSize() {
		return getPreferredSize();
	}

	@Override
	public int getScrollableUnitIncrement(Rectangle visibleRect, int orientation, int direction) {
		return 5;
	}

	@Override
	public int getScrollableBlockIncrement(Rectangle visibleRect, int orientation, int direction) {
		if (visibleRect == null) {
			return 80;
		}
		return Math.max(80, visibleRect.height - 20);
	}

	@Override
	public boolean getScrollableTracksViewportWidth() {
		return true;
	}

	@Override
	public boolean getScrollableTracksViewportHeight() {
		Container parent = getParent();
		if (!(parent instanceof JViewport viewport)) {
			return false;
		}
		return getPreferredSize().height <= viewport.getHeight();
	}

	void attachDragController(AbilityDragController.DragCallback callback) {
		this.dragController = new AbilityDragController(this, callback);
		this.cardFactory = new AbilityCardFactory(dragController);
	}

	void setModificationIndicators(boolean sequenceWideModified, Set<String> modifiedAbilityKeys) {
		this.sequenceWideModified = sequenceWideModified;
		this.modifiedAbilityKeys = modifiedAbilityKeys != null ? modifiedAbilityKeys : Set.of();
	}

	void setTooltipEditHandler(TooltipEditHandler tooltipEditHandler) {
		this.tooltipEditHandler = tooltipEditHandler;
	}

	void setAbilityPropertiesHandler(IntConsumer handler) {
		this.abilityPropertiesHandler = handler;
	}

	void setSpecModifierHandler(IntConsumer handler) {
		this.specModifierHandler = handler;
	}

	void renderSequenceElements(List<SequenceElement> elements) {
		hideEmptyDropIndicator();
		removeAll();
		activePreview = null;
		highlightedCards = List.of();
		cachedAbilityCards = null;
		lastRenderedElements = elements != null ? new ArrayList<>(elements) : List.of();
		elements = elements != null ? elements : List.of();

		int index = 0;
		while (index < elements.size()) {
			SequenceElement element = elements.get(index);

			if (element.isAbility()) {
				int nextStructuralIndex = nextNonTooltipIndex(elements, index + 1);
				if (nextStructuralIndex != -1) {
					SequenceElement next = elements.get(nextStructuralIndex);
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
		if (targetIndex >= 0 && targetIndex < cards.length && cards[targetIndex] instanceof JPanel) {
			Color highlightColor = getHighlightColor(preview.getZoneType());
			highlightedCards = collectPreviewHighlightCards(cards, preview);
			for (JComponent card : highlightedCards) {
				card.setBackground(highlightColor);
				card.setBorder(BorderFactory.createLineBorder(highlightColor.darker(), 2));
				repaint(card.getBounds());
			}
			return !highlightedCards.isEmpty();
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
		repaint();
	}

	private void clearPreviewHighlight(Component[] cards, DragPreviewModel previewModel) {
		if (previewModel == null || previewModel.getDropPreview() == null || highlightedCards == null || highlightedCards.isEmpty()) {
			return;
		}
		for (JComponent previousCard : highlightedCards) {
			previousCard.setBackground(UiColorPalette.UI_CARD_BACKGROUND);
			previousCard.setBorder(UiColorPalette.CARD_BORDER);
			repaint(previousCard.getBounds());
		}
		highlightedCards = List.of();
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

		int currentIndex = startIndex;
		while (currentIndex < elements.size()) {
			SequenceElement element = elements.get(currentIndex);

			if (element.isTooltip()) {
				addTooltipCard(element, currentIndex, groupPanel);
				currentIndex++;
				continue;
			}

			if (element.isAbility()) {
				addAbilityCard(element, currentIndex, zoneForGroupType(groupType), groupPanel);
				currentIndex++;
				continue;
			}

			if (element.getType() == groupType) {
				groupPanel.add(cardFactory.createSeparatorLabel(element));
				currentIndex++;
				continue;
			}

			break;
		}

		add(groupPanel);
		return currentIndex - 1;
	}

	private void addStandaloneAbility(SequenceElement element, int elementIndex) {
		addAbilityCard(element, elementIndex, null, this);
	}

	private void addAbilityCard(SequenceElement element, int elementIndex, DropZoneType zoneType, Container parent) {
		String abilityKey = element.getResolvedAbilityKey();
		AbilityItem item = detailService.createAbilityItem(abilityKey);
		if (item != null) {
			List<ImageIcon> overlays = element.hasAbilityModifiers()
					? element.getAbilityModifiers().stream()
					.filter(key -> key != null && !key.isBlank())
					.map(detailService::loadIcon)
					.toList()
					: List.of();
			JPanel card = cardFactory.createAbilityCard(item, isElementModified(element), overlays);
			card.putClientProperty("elementIndex", elementIndex);
			card.putClientProperty("zoneType", zoneType);
			attachAbilityPopup(card);
			parent.add(card);
		}
	}

	private void addTooltipCard(SequenceElement element, int elementIndex) {
		addTooltipCard(element, elementIndex, this);
	}

	private void addTooltipCard(SequenceElement element, int elementIndex, Container parent) {
		JPanel card = cardFactory.createTooltipCard(element.getValue());
		card.putClientProperty("elementIndex", elementIndex);
		card.putClientProperty("zoneType", null);
		attachTooltipPopup(card);
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
			parent.add(card);
		}

	private List<JComponent> collectPreviewHighlightCards(Component[] cards, DropPreview preview) {
		if (cards == null || cards.length == 0 || preview == null) {
			return List.of();
		}
		int targetIndex = preview.getTargetAbilityIndex();
		if (targetIndex < 0 || targetIndex >= cards.length) {
			return List.of();
		}

		Integer targetElementIndex = extractElementIndex(cards[targetIndex]);
		if (targetElementIndex == null) {
			return List.of();
		}

		int highlightStart = targetElementIndex;
		int highlightEnd = targetElementIndex;

		if (preview.getZoneType() == DropZoneType.AND || preview.getZoneType() == DropZoneType.OR) {
			SequenceElement.Type separator = preview.getZoneType() == DropZoneType.AND
					? SequenceElement.Type.PLUS
					: SequenceElement.Type.SLASH;
			if (lastRenderedElements != null
					&& targetElementIndex >= 0
					&& targetElementIndex < lastRenderedElements.size()
					&& lastRenderedElements.get(targetElementIndex).isAbility()) {
				SequenceGrouping.AbilityRange abilityRange = SequenceGrouping.computeGroupAbilityRange(lastRenderedElements, targetElementIndex, separator);
				if (abilityRange != null && abilityRange.isValid()) {
					highlightStart = expandLeftTooltips(lastRenderedElements, abilityRange.start());
					highlightEnd = expandRightTooltips(lastRenderedElements, abilityRange.end());
				}
			}
		} else {
			highlightStart = expandLeftTooltips(lastRenderedElements, targetElementIndex);
			highlightEnd = expandRightTooltips(lastRenderedElements, targetElementIndex);
		}

		List<JComponent> out = new ArrayList<>();
		for (Component component : cards) {
			Integer idx = extractElementIndex(component);
			if (idx == null) {
				continue;
			}
			if (idx >= highlightStart && idx <= highlightEnd && component instanceof JComponent jc) {
				out.add(jc);
			}
		}
		return out;
	}

	private Integer extractElementIndex(Component component) {
		if (!(component instanceof JComponent jc)) {
			return null;
		}
		Object prop = jc.getClientProperty("elementIndex");
		if (prop instanceof Integer idx) {
			return idx;
		}
		return null;
	}

	private int expandLeftTooltips(List<SequenceElement> elements, int index) {
		if (elements == null || index <= 0) {
			return Math.max(0, index);
		}
		int cursor = Math.min(index, elements.size() - 1);
		while (cursor - 1 >= 0 && elements.get(cursor - 1).isTooltip()) {
			cursor--;
		}
		return cursor;
	}

	private int expandRightTooltips(List<SequenceElement> elements, int index) {
		if (elements == null || elements.isEmpty()) {
			return index;
		}
		int cursor = Math.max(0, Math.min(index, elements.size() - 1));
		while (cursor + 1 < elements.size() && elements.get(cursor + 1).isTooltip()) {
			cursor++;
		}
		return cursor;
	}

	private void attachTooltipPopup(JPanel card) {
		if (card == null) {
			return;
		}
		card.addMouseListener(new MouseAdapter() {
			private void maybeShowPopup(MouseEvent e) {
				if (tooltipEditHandler == null || !e.isPopupTrigger()) {
					return;
				}
				Object rawIndex = card.getClientProperty("elementIndex");
				if (rawIndex instanceof Integer idx) {
					JPopupMenu menu = new JPopupMenu();
					JMenuItem properties = new JMenuItem("Properties\u2026");
					properties.addActionListener(action -> tooltipEditHandler.editTooltipAt(idx));
					menu.add(properties);
					menu.show(e.getComponent(), e.getX(), e.getY());
				}
			}

			@Override
			public void mousePressed(MouseEvent e) {
				maybeShowPopup(e);
			}

			@Override
			public void mouseReleased(MouseEvent e) {
				maybeShowPopup(e);
			}
		});
	}

	private void attachAbilityPopup(JPanel card) {
		if (card == null) {
			return;
		}
		card.addMouseListener(new MouseAdapter() {
			private void maybeShowPopup(MouseEvent e) {
				if (abilityPropertiesHandler == null || !e.isPopupTrigger()) {
					return;
				}
				Object rawIndex = card.getClientProperty("elementIndex");
				if (rawIndex instanceof Integer idx) {
					JPopupMenu menu = new JPopupMenu();
					JMenuItem properties = new JMenuItem("Properties\u2026");
					properties.addActionListener(action -> abilityPropertiesHandler.accept(idx));
					menu.add(properties);

					SequenceElement element = idx >= 0 && idx < lastRenderedElements.size()
							? lastRenderedElements.get(idx)
							: null;
					String key = element != null ? element.getResolvedAbilityKey() : null;
					if (specModifierHandler != null && ("spec".equals(key) || "eofspec".equals(key))) {
						menu.addSeparator();
						JMenuItem special = new JMenuItem("Special Attack\u2026");
						special.addActionListener(action -> specModifierHandler.accept(idx));
						menu.add(special);
					}
					menu.show(e.getComponent(), e.getX(), e.getY());
				}
			}

			@Override
			public void mousePressed(MouseEvent e) {
				maybeShowPopup(e);
			}

			@Override
			public void mouseReleased(MouseEvent e) {
				maybeShowPopup(e);
			}
		});
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

	private boolean shouldShowEmptyHint() {
		if (getAbilityCardArray().length != 0) {
			return false;
		}
		if (emptyDropIndicator.isVisible()) {
			return false;
		}
		return activePreview == null || activePreview.getDropPreview() == null || !activePreview.isValid();
	}

	private void paintEmptyHint(Graphics2D g2) {
		String[] lines = {
				"Drag an ability from the palette above",
				"and drop it here to build a sequence."
		};

		Font baseFont = getFont();
		if (baseFont == null) {
			baseFont = UiColorPalette.sans(12);
		}

		Font hintFont = baseFont.deriveFont(Font.ITALIC, Math.max(12f, baseFont.getSize2D()));
		g2.setFont(hintFont);
		g2.setColor(UiColorPalette.TEXT_MUTED);

		FontMetrics fm = g2.getFontMetrics();
		int lineHeight = fm.getHeight();
		int totalHeight = lineHeight * lines.length;

		int w = getWidth();
		int h = getHeight();
		if (w <= 0 || h <= 0) {
			return;
		}

		int startY = (h - totalHeight) / 2 + fm.getAscent();
		for (int i = 0; i < lines.length; i++) {
			String line = lines[i];
			int textWidth = fm.stringWidth(line);
			int x = Math.max(10, (w - textWidth) / 2);
			int y = startY + (i * lineHeight);
			g2.drawString(line, x, y);
		}
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

	private boolean isElementModified(SequenceElement element) {
		if (element == null || !element.isAbility()) {
			return false;
		}
		if (sequenceWideModified) {
			return true;
		}
		if (element.hasOverrides()) {
			return true;
		}
		String abilityKey = element.getResolvedAbilityKey();
		return abilityKey != null && modifiedAbilityKeys != null && modifiedAbilityKeys.contains(abilityKey);
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
