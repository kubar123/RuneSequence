package com.lansoftprogramming.runeSequence.ui.presetManager.drag.handler;

import com.lansoftprogramming.runeSequence.ui.presetManager.drag.DropZoneIndicators;
import com.lansoftprogramming.runeSequence.ui.presetManager.drag.model.DropPreview;
import com.lansoftprogramming.runeSequence.ui.presetManager.drag.model.DropSide;
import com.lansoftprogramming.runeSequence.ui.presetManager.drag.model.DropZoneType;
import com.lansoftprogramming.runeSequence.ui.presetManager.model.SequenceElement;
import com.lansoftprogramming.runeSequence.ui.shared.model.AbilityItem;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.swing.*;
import java.awt.*;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.net.URL;
import java.util.ArrayList;
import java.util.List;

/**
 * Manages card-style drag-and-drop for abilities.
 * It handles visual dragging on the glass pane and real-time drop zone previews.
 */
public class AbilityDragController {

    private static final Logger logger = LoggerFactory.getLogger(AbilityDragController.class);
    private static final int ZONE_HEIGHT = 20;
    private static final int MAX_DROP_DISTANCE = 100;

    private boolean isDragOutsidePanel = false;
    private JLabel trashIconLabel;
    private ImageIcon trashIcon;

    private final JPanel flowPanel;
    private final DragCallback callback;

    private DragState currentDrag;
    private JPanel floatingCard;
    private JComponent glassPane;
    private DropZoneIndicators indicators;

    // Logging state to avoid spam
    private boolean releasePhaseLogging = false;
    private Integer lastIndicatorVisualIdx = null;
    private DropSide lastIndicatorDropSide = null;
    private DropZoneType lastIndicatorZoneType = null;
    private boolean hasLoggedIndicatorShown = false;

    public interface DragCallback {
        void onDragStart(AbilityItem item, boolean isFromPalette);
        void onDragMove(AbilityItem draggedItem, Point cursorPos, DropPreview preview);
        void onDragEnd(AbilityItem draggedItem, boolean commit);
        List<SequenceElement> getCurrentElements();
        Component[] getAllCards();
    }

    public AbilityDragController(JPanel flowPanel, DragCallback callback) {
        this.flowPanel = flowPanel;
        this.callback = callback;
        this.indicators = new DropZoneIndicators();
    }

    public void setDragOutsidePanel(boolean isOutside) {
        this.isDragOutsidePanel = isOutside;
        updateFloatingCardAppearance();
    }

    private void updateFloatingCardAppearance() {
        if (floatingCard == null) return;

        if (isDragOutsidePanel) {
            // Load trash icon if not already loaded
            if (trashIcon == null) {
                try {
                    URL trashUrl = getClass().getResource("/ui/trash-512.png");
                    if (trashUrl != null) {
                        ImageIcon fullSizeTrash = new ImageIcon(trashUrl);
                        // Scale to 50x50 to match ability icon size
                        Image scaledImage = fullSizeTrash.getImage().getScaledInstance(50, 50, Image.SCALE_SMOOTH);
                        trashIcon = new ImageIcon(scaledImage);
                    }
                } catch (Exception e) {
                    logger.warn("Failed to load trash icon", e);
                }
            }

            // Find the icon label (first label with an icon)
            for (Component comp : floatingCard.getComponents()) {
                if (comp instanceof JLabel) {
                    JLabel label = (JLabel) comp;
                    // Check if this label has an icon (the ability icon, not the text label)
                    if (label.getIcon() != null && label.getClientProperty("originalIcon") == null) {
                        // Save original icon only if not already saved
                        label.putClientProperty("originalIcon", label.getIcon());

                        // Replace with trash icon
                        if (trashIcon != null) {
                            label.setIcon(trashIcon);
                        }
                        break; // Only process the icon label
                    }
                }
            }
        } else {
            // Restore original appearance
            for (Component comp : floatingCard.getComponents()) {
                if (comp instanceof JLabel) {
                    JLabel label = (JLabel) comp;
                    ImageIcon originalIcon = (ImageIcon) label.getClientProperty("originalIcon");
                    if (originalIcon != null) {
                        label.setIcon(originalIcon);
                        label.putClientProperty("originalIcon", null);
                        break; // Only restore the icon label
                    }
                }
            }
        }

        floatingCard.revalidate();
        floatingCard.repaint();
    }


    /**
     * Creates a `MouseAdapter` for draggable cards.
     * Initiates, tracks, and finalizes drag operations, distinguishing palette cards from sequence elements.
     */
    public MouseAdapter createCardDragListener(AbilityItem item, JPanel card, boolean isFromPalette) {
        return new MouseAdapter() {
            @Override
            public void mousePressed(MouseEvent e) {
                int index = -1;
                // re-ordering existing sequence cards.
                if (!isFromPalette) {
                    Component[] cards = callback.getAllCards();
                    for (int i = 0; i < cards.length; i++) {
                        if (cards[i] == card) {
                            index = i;
                            break;
                        }
                    }
                }
                startDrag(item, card, isFromPalette, index, e.getPoint());
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

    /**
     * Initiates a drag by creating a `floatingCard`
     * on the glass pane, allowing it to follow the cursor without affecting layout.
     */
    private void startDrag(AbilityItem item, JPanel card, boolean isFromPalette, int originalIndex, Point startPoint) {
        currentDrag = new DragState(item, card, isFromPalette, originalIndex, callback.getCurrentElements());

        floatingCard = createFloatingCard(card);

        JRootPane rootPane = SwingUtilities.getRootPane(card);
        if (rootPane != null) {
            // Float card above all other UI.
            glassPane = (JComponent) rootPane.getGlassPane();
            glassPane.setLayout(null); // Absolute positioning
            glassPane.add(floatingCard);
            glassPane.setVisible(true);

            // Setup indicators on glass pane
            indicators.setGlassPane(glassPane);

            // Convert to glass pane's coordinate system.
            Point glassPanePoint = SwingUtilities.convertPoint(card, startPoint, glassPane);
            floatingCard.setLocation(
                glassPanePoint.x - card.getWidth() / 2,
                glassPanePoint.y - card.getHeight() / 2
            );
        }

        callback.onDragStart(item, isFromPalette);
    }

    /**
     * Updates `floatingCard` position and calculates a `DropPreview`.
     * Notifies `callback` for real-time layout updates.
     */
    private void handleDrag(MouseEvent e) {
        if (floatingCard == null || glassPane == null) return;

        Point glassPanePoint = SwingUtilities.convertPoint((Component) e.getSource(), e.getPoint(), glassPane);
        floatingCard.setLocation(
            glassPanePoint.x - floatingCard.getWidth() / 2,
            glassPanePoint.y - floatingCard.getHeight() / 2
        );

        // Convert to flow panel's coordinates for drop logic.
        Point flowPanelPoint = SwingUtilities.convertPoint((Component) e.getSource(), e.getPoint(), flowPanel);

        // Silence preview logs during move
        releasePhaseLogging = false;
        DropPreview preview = calculateDropPreview(flowPanelPoint);

        // Update indicators based on preview
        updateIndicators(preview);

        callback.onDragMove(currentDrag.getDraggedItem(), flowPanelPoint, preview);

        // Refresh layout
        flowPanel.revalidate();
        flowPanel.repaint();
    }

    /**
     * Finalizes the drag. Determines if the drop is valid based on `DropPreview`
     * and `flowPanel` bounds.
     * Cleans up the `floatingCard` and notifies `callback` to commit or cancel.
     */
    private void handleRelease(MouseEvent e) {
        Point flowPanelPoint = SwingUtilities.convertPoint((Component) e.getSource(), e.getPoint(), flowPanel);

        // Enable condensed logs for the final calculation only
        releasePhaseLogging = true;
        DropPreview preview = calculateDropPreview(flowPanelPoint);
        releasePhaseLogging = false;

        boolean insidePanel = flowPanel.contains(flowPanelPoint);
        boolean commit = preview.isValid() && insidePanel;

        // One-line drop summary
        logger.info(
            "Drop: item={}, fromPalette={}, commit={}, insertIndex={}, zone={}, targetVisualIdx={}, side={}, insidePanel={}",
            safeItemLabel(currentDrag.getDraggedItem()),
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

    private String safeItemLabel(AbilityItem item) {
        try {
            String name = String.valueOf(item);
            return name;
        } catch (Exception ex) {
            return "AbilityItem";
        }
    }

    /**
     * Calculates the drop position and type ("AND", "OR", "NEXT") within the sequence.
     * Uses the nearest card and vertical drag point to determine the precise insertion logic.
     *
     * elements list by scanning for the target ability, not using the original elementIndex.
     */
    private DropPreview calculateDropPreview(Point dragPoint) {
        Component[] allCards = callback.getAllCards();
        List<SequenceElement> elements = callback.getCurrentElements();

        if (allCards.length == 0) {
            return new DropPreview(0, DropZoneType.NEXT, -1, DropSide.RIGHT);
        }

        // Build list of potential targets
        List<Component> visualCards = new ArrayList<>();
        List<Integer> elementIndices = new ArrayList<>();
        List<Integer> originalIndices = new ArrayList<>();

        for (int idx = 0; idx < allCards.length; idx++) {
            Component c = allCards[idx];

            if (currentDrag != null && c == currentDrag.getDraggedCard()) {
                if (releasePhaseLogging) {
                    logger.info("Skipping dragged card at index {}", idx);
                }
                continue;
            }

            Integer elementIdx = null;
            if (c instanceof JComponent) {
                Object prop = ((JComponent) c).getClientProperty("elementIndex");
                if (prop instanceof Integer) {
                    elementIdx = (Integer) prop;
                }
            }
            if (elementIdx == null || elementIdx < 0) {
                continue;
            }

            visualCards.add(c);
            elementIndices.add(elementIdx);
            originalIndices.add(idx);
        }

		if (releasePhaseLogging) {
			logger.info("Built {} valid targets", visualCards.size());
		}

		if (visualCards.isEmpty()) {
			if (releasePhaseLogging) {
				logger.info("Preview fallback: no candidate cards, append at end (elementCount={})", elements.size());
			}
			return new DropPreview(elements.size(), DropZoneType.NEXT, -1, DropSide.RIGHT);
		}

        // Find nearest card
        Component nearestCard = null;
        int nearestListIdx = -1;
        double minDistance = Double.MAX_VALUE;

        for (int i = 0; i < visualCards.size(); i++) {
            Component c = visualCards.get(i);
            Rectangle boundsInFlowPanel = SwingUtilities.convertRectangle(c.getParent(), c.getBounds(), flowPanel);
            Point center = new Point(
                boundsInFlowPanel.x + boundsInFlowPanel.width / 2,
                boundsInFlowPanel.y + boundsInFlowPanel.height / 2
            );
            double dist = dragPoint.distance(center);

            if (releasePhaseLogging) {
                logger.info("Card {} - center (in flow panel): {}", i, center);
            }

            if (dist < minDistance) {
                minDistance = dist;
                nearestCard = c;
                nearestListIdx = i;
            }
        }

		if (nearestCard == null || minDistance > MAX_DROP_DISTANCE) {
			int lastOriginalIdx = originalIndices.get(originalIndices.size() - 1);
			if (releasePhaseLogging) {
				logger.info("Preview fallback: nearest invalid (minDistance={}, limit={}), using NEXT append, lastVisualIdx={}, elementCount={}",
					minDistance,
					MAX_DROP_DISTANCE,
					lastOriginalIdx,
					elements.size()
				);
			}
			return new DropPreview(elements.size(), DropZoneType.NEXT, lastOriginalIdx, DropSide.RIGHT);
		}

        Rectangle nearestBoundsInFlowPanel = SwingUtilities.convertRectangle(
            nearestCard.getParent(),
            nearestCard.getBounds(),
            flowPanel
        );

        int cardCenterX = nearestBoundsInFlowPanel.x + nearestBoundsInFlowPanel.width / 2;
        DropSide dropSide = dragPoint.x < cardCenterX ? DropSide.LEFT : DropSide.RIGHT;

        int topLimit = nearestBoundsInFlowPanel.y + ZONE_HEIGHT;
        int bottomLimit = nearestBoundsInFlowPanel.y + nearestBoundsInFlowPanel.height - ZONE_HEIGHT;
        int targetElementIndex = elementIndices.get(nearestListIdx);
        int targetVisualIndex = originalIndices.get(nearestListIdx);

        List<SequenceElement> elementsToCheck = currentDrag != null
            ? currentDrag.getOriginalElements()
            : elements;
        SequenceElement.Type existingGroupType = detectGroupType(elementsToCheck, targetElementIndex);

        String targetAbilityKey = null;
        if (targetElementIndex < elementsToCheck.size()) {
            SequenceElement elem = elementsToCheck.get(targetElementIndex);
            if (elem.isAbility()) {
                targetAbilityKey = elem.getValue();
            }
        }

        int currentTargetIndex = -1;
        if (targetAbilityKey != null) {
            for (int i = 0; i < elements.size(); i++) {
                if (elements.get(i).isAbility() && elements.get(i).getValue().equals(targetAbilityKey)) {
                    currentTargetIndex = i;
                    break;
                }
            }
        }
		if (currentTargetIndex == -1) {
			if (releasePhaseLogging) {
				logger.info("Preview fallback: unmatched ability key={}, defaulting to index {} (elementsSize={})",
					targetAbilityKey,
					targetElementIndex,
					elements.size()
				);
			}
			currentTargetIndex = Math.min(targetElementIndex, elements.size() - 1);
		}

        GroupBoundaries groupBounds = analyzeGroupBoundaries(elements, currentTargetIndex, existingGroupType);

        DropZoneType zoneType;
        if (dragPoint.y < topLimit) {
            zoneType = determineTopZoneType(existingGroupType, currentTargetIndex, groupBounds);
        } else if (dragPoint.y > bottomLimit) {
            zoneType = determineBottomZoneType(existingGroupType, currentTargetIndex, groupBounds);
        } else {
            zoneType = determineMiddleZoneType(existingGroupType);
        }

		if (releasePhaseLogging) {
			logger.info("Zone decision: cursorY={}, topLimit={}, bottomLimit={}, zone={}, groupType={}, targetElementIndex={}, targetVisualIndex={}",
				dragPoint.y,
				topLimit,
				bottomLimit,
				zoneType,
				existingGroupType,
				targetElementIndex,
				targetVisualIndex
			);
		}

		int insertIndex = calculateInsertionIndex(
			elements,
			currentTargetIndex,
			dropSide,
			zoneType,
			groupBounds
		);

		if (releasePhaseLogging) {
			logger.info("Insert decision: resolvedIndex={}, currentTargetIndex={}, dropSide={}, zone={}, groupBounds=[{},{}], groupType={}, targetKey={}",
				insertIndex,
				currentTargetIndex,
				dropSide,
				zoneType,
				groupBounds.start,
				groupBounds.end,
				existingGroupType,
				targetAbilityKey
			);
		}

		return new DropPreview(insertIndex, zoneType, targetVisualIndex, dropSide);
	}

    private GroupBoundaries analyzeGroupBoundaries(List<SequenceElement> elements,
                                                   int targetIndex,
                                                   SequenceElement.Type groupType) {
        if (groupType == null || targetIndex < 0 || targetIndex >= elements.size()) {
            return new GroupBoundaries(-1, -1);
        }
        int start = targetIndex;
        int end = targetIndex;
        for (int i = targetIndex - 1; i >= 0; i--) {
            SequenceElement elem = elements.get(i);
            if (elem.getType() == groupType) {
                i--;
                if (i >= 0 && elements.get(i).isAbility()) {
                    start = i;
                }
            } else {
                break;
            }
        }
        for (int i = targetIndex + 1; i < elements.size(); i++) {
            SequenceElement elem = elements.get(i);
            if (elem.getType() == groupType) {
                i++;
                if (i < elements.size() && elements.get(i).isAbility()) {
                    end = i;
                }
            } else {
                break;
            }
        }
        return new GroupBoundaries(start, end);
    }

    private DropZoneType determineTopZoneType(SequenceElement.Type existingGroupType,
                                              int currentTargetIndex,
                                              GroupBoundaries groupBounds) {
        if (existingGroupType != null && groupBounds.isValid()) {
            boolean isGroupStart = (currentTargetIndex == groupBounds.start);
            if (isGroupStart) {
                return DropZoneType.NEXT;
            }
            return existingGroupType == SequenceElement.Type.PLUS ? DropZoneType.AND : DropZoneType.OR;
        }
        return DropZoneType.AND;
    }

    private DropZoneType determineBottomZoneType(SequenceElement.Type existingGroupType,
                                                 int currentTargetIndex,
                                                 GroupBoundaries groupBounds) {
        if (existingGroupType != null && groupBounds.isValid()) {
            boolean isGroupEnd = (currentTargetIndex == groupBounds.end);
            if (isGroupEnd) {
                return DropZoneType.NEXT;
            }
            return existingGroupType == SequenceElement.Type.PLUS ? DropZoneType.AND : DropZoneType.OR;
        }
        return DropZoneType.OR;
    }

    private DropZoneType determineMiddleZoneType(SequenceElement.Type existingGroupType) {
        if (existingGroupType != null) {
            return existingGroupType == SequenceElement.Type.PLUS ? DropZoneType.AND : DropZoneType.OR;
        }
        return DropZoneType.NEXT;
    }

    private int calculateInsertionIndex(List<SequenceElement> elements,
                                        int currentTargetIndex,
                                        DropSide dropSide,
                                        DropZoneType zoneType,
                                        GroupBoundaries groupBounds) {
        if (zoneType == DropZoneType.NEXT) {
            if (groupBounds.isValid()) {
                if (currentTargetIndex == groupBounds.start && dropSide == DropSide.LEFT) {
                    return currentTargetIndex;
                } else if (currentTargetIndex == groupBounds.end && dropSide == DropSide.RIGHT) {
                    return currentTargetIndex + 1;
                }
            }
            return dropSide == DropSide.LEFT ? currentTargetIndex : currentTargetIndex + 1;
        }
        return dropSide == DropSide.LEFT ? currentTargetIndex : currentTargetIndex + 1;
    }

    private static class GroupBoundaries {
        final int start;
        final int end;

        GroupBoundaries(int start, int end) {
            this.start = start;
            this.end = end;
        }

        boolean isValid() {
            return start >= 0 && end >= 0;
        }
    }

    private SequenceElement.Type detectGroupType(List<SequenceElement> elements, int abilityIndex) {
        if (abilityIndex < 0 || abilityIndex >= elements.size()) {
            return null;
        }
        if (abilityIndex + 1 < elements.size()) {
            SequenceElement next = elements.get(abilityIndex + 1);
            if (next.isPlus()) {
                return SequenceElement.Type.PLUS;
            } else if (next.isSlash()) {
                return SequenceElement.Type.SLASH;
            }
        }
        if (abilityIndex - 1 >= 0) {
            SequenceElement prev = elements.get(abilityIndex - 1);
            if (prev.isPlus()) {
                return SequenceElement.Type.PLUS;
            } else if (prev.isSlash()) {
                return SequenceElement.Type.SLASH;
            }
        }
        return null;
    }

    private JPanel createFloatingCard(JPanel original) {
        JPanel floating = new JPanel();
        floating.setLayout(new BoxLayout(floating, BoxLayout.Y_AXIS));
        floating.setPreferredSize(original.getPreferredSize());
        floating.setSize(original.getSize());
        floating.setBorder(original.getBorder());
        floating.setBackground(original.getBackground());
        floating.setOpaque(true);

        for (Component comp : original.getComponents()) {
            if (comp instanceof JLabel) {
                JLabel origLabel = (JLabel) comp;
                JLabel newLabel = new JLabel(origLabel.getText(), origLabel.getIcon(), origLabel.getHorizontalAlignment());
                newLabel.setFont(origLabel.getFont());
                newLabel.setAlignmentX(origLabel.getAlignmentX());
                newLabel.setPreferredSize(comp.getPreferredSize());
                newLabel.setMinimumSize(comp.getMinimumSize());
                newLabel.setMaximumSize(comp.getMaximumSize());
                floating.add(newLabel);
            } else if (comp instanceof Box.Filler) {
                floating.add(Box.createRigidArea(comp.getSize()));
            }
        }
        return floating;
    }

    private void cleanup() {
        indicators.cleanup();
        isDragOutsidePanel = false;

        // reset indicator logging state
        lastIndicatorVisualIdx = null;
        lastIndicatorDropSide = null;
        lastIndicatorZoneType = null;
        hasLoggedIndicatorShown = false;

        if (floatingCard != null && glassPane != null) {
            glassPane.remove(floatingCard);
            glassPane.setVisible(false);
            floatingCard = null;
            glassPane = null;
        }
    }

    /**
     * Updates drop zone indicators based on current preview.
     * Condensed logging: show once, then only on change.
     */
	private void updateIndicators(DropPreview preview) {
		if (!preview.isValid()) {
			indicators.hideIndicators();

			// reset so next valid show will log once
			lastIndicatorVisualIdx = null;
			lastIndicatorDropSide = null;
			lastIndicatorZoneType = null;
			hasLoggedIndicatorShown = false;
			return;
		}

		Component[] allCards = callback.getAllCards();

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
			lastIndicatorVisualIdx = null;
			lastIndicatorDropSide = null;
			lastIndicatorZoneType = null;
			hasLoggedIndicatorShown = false;
			return;
		}

        Component targetCard = allCards[targetVisualIndex];

        // Calculate insertion line position
        Component leftCard = null;
        Component rightCard = null;

        if (preview.getDropSide() == DropSide.LEFT) {
            rightCard = targetCard;
            if (targetVisualIndex > 0) {
                leftCard = allCards[targetVisualIndex - 1];
            }
        } else {
            leftCard = targetCard;
            if (targetVisualIndex + 1 < allCards.length) {
                rightCard = allCards[targetVisualIndex + 1];
            }
        }

        indicators.showInsertionLineBetweenCards(leftCard, rightCard, preview.getZoneType());

        // Logging: first show and changes only
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

        List<SequenceElement> elementsToCheck = currentDrag != null
            ? currentDrag.getOriginalElements()
            : callback.getCurrentElements();

        Integer elementIdx = null;
        if (targetCard instanceof JComponent) {
            Object prop = ((JComponent) targetCard).getClientProperty("elementIndex");
            if (prop instanceof Integer) {
                elementIdx = (Integer) prop;
            }
        }
        if (elementIdx == null) {
            return;
        }

        SequenceElement.Type groupType = detectGroupType(elementsToCheck, elementIdx);

        String topSymbol;
        String bottomSymbol;

        if (groupType != null) {
            String groupSymbol = groupType == SequenceElement.Type.PLUS ? "+" : "/";
            topSymbol = groupSymbol;
            bottomSymbol = groupSymbol;
        } else {
            topSymbol = "+";
            bottomSymbol = "/";
        }

        indicators.showIndicators(targetCard, topSymbol, bottomSymbol);
    }

    private boolean targetVisualIndexEquals(Integer idx) {
        return lastIndicatorVisualIdx != null && lastIndicatorVisualIdx.equals(idx);
    }
}
