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
        DropPreview preview = calculateDropPreview(flowPanelPoint);

        // Commit only on a valid drop inside the panel.
        boolean commit = preview.isValid() && flowPanel.contains(flowPanelPoint);

        cleanup();
        callback.onDragEnd(currentDrag.getDraggedItem(), commit);

        currentDrag = null;
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

        logger.debug("=== calculateDropPreview ===");
        logger.debug("dragPoint: {}", dragPoint);
        logger.debug("allCards.length: {}", allCards.length);
        logger.debug("elements.size: {}", elements.size());

        if (allCards.length == 0) {
            return new DropPreview(0, DropZoneType.NEXT, -1, DropSide.RIGHT);
        }

        // Build list of potential drop targets with their original array indices.
        List<Component> visualCards = new ArrayList<>();
        List<Integer> elementIndices = new ArrayList<>();
        List<Integer> originalIndices = new ArrayList<>();

        for (int idx = 0; idx < allCards.length; idx++) {
            Component c = allCards[idx];

            if (currentDrag != null && c == currentDrag.getDraggedCard()) {
                logger.debug("Skipping dragged card at index {}", idx);
                continue;
            }

            // Client property provides stable index during drag previews.
            Integer elementIdx = null;
            if (c instanceof JComponent) {
                Object prop = ((JComponent) c).getClientProperty("elementIndex");
                if (prop instanceof Integer) {
                    elementIdx = (Integer) prop;
                }
            }

            if (elementIdx == null || elementIdx < 0) {
                logger.debug("Skipping card at index {} - invalid elementIdx: {}", idx, elementIdx);
                continue;
            }

            Rectangle bounds = c.getBounds();
            logger.debug("Card {} - originalIdx: {}, elementIdx: {}, bounds: {}",
                c.getName(), idx, elementIdx, bounds);

            visualCards.add(c);
            elementIndices.add(elementIdx);
            originalIndices.add(idx);
        }

        logger.debug("Built {} valid targets", visualCards.size());

        if (visualCards.isEmpty()) {
            return new DropPreview(elements.size(), DropZoneType.NEXT, -1, DropSide.RIGHT);
        }

        // Find nearest card to determine drop target.
        Component nearestCard = null;
        int nearestListIdx = -1;
        double minDistance = Double.MAX_VALUE;

        for (int i = 0; i < visualCards.size(); i++) {
            Component c = visualCards.get(i);

            // Convert card bounds to flow panel coordinate space
            Rectangle boundsInFlowPanel = SwingUtilities.convertRectangle(
                c.getParent(),
                c.getBounds(),
                flowPanel
            );

            Point center = new Point(
                boundsInFlowPanel.x + boundsInFlowPanel.width / 2,
                boundsInFlowPanel.y + boundsInFlowPanel.height / 2
            );
            double dist = dragPoint.distance(center);

            logger.debug("Card {} - center (in flow panel): {}, distance: {}", i, center, dist);

            if (dist < minDistance) {
                minDistance = dist;
                nearestCard = c;
                nearestListIdx = i;
            }
        }

        logger.debug("Nearest card: listIdx={}, distance={}", nearestListIdx, minDistance);

        // No card is close; drop at the end.
        if (nearestCard == null || minDistance > MAX_DROP_DISTANCE) {
            int lastOriginalIdx = originalIndices.get(originalIndices.size() - 1);
            logger.debug("No nearby card - dropping at end. targetVisualIndex={}", lastOriginalIdx);
            return new DropPreview(elements.size(), DropZoneType.NEXT, lastOriginalIdx, DropSide.RIGHT);
        }

        // Convert bounds to flow panel coordinates for accurate zone detection
        Rectangle nearestBoundsInFlowPanel = SwingUtilities.convertRectangle(
            nearestCard.getParent(),
            nearestCard.getBounds(),
            flowPanel
        );

        // Determine drop side based on HORIZONTAL position
        int cardCenterX = nearestBoundsInFlowPanel.x + nearestBoundsInFlowPanel.width / 2;
        DropSide dropSide = dragPoint.x < cardCenterX ? DropSide.LEFT : DropSide.RIGHT;

        logger.debug("Drop side calculation: dragX={}, cardCenterX={}, dropSide={}",
            dragPoint.x, cardCenterX, dropSide);

        // Determine drop zone type based on VERTICAL position
        int topLimit = nearestBoundsInFlowPanel.y + ZONE_HEIGHT;
        int bottomLimit = nearestBoundsInFlowPanel.y + nearestBoundsInFlowPanel.height - ZONE_HEIGHT;
        int targetElementIndex = elementIndices.get(nearestListIdx);
        int targetVisualIndex = originalIndices.get(nearestListIdx);

        logger.debug("Nearest card details:");
        logger.debug("  targetElementIndex: {}", targetElementIndex);
        logger.debug("  targetVisualIndex: {}", targetVisualIndex);
        logger.debug("  bounds (in flow panel): {}", nearestBoundsInFlowPanel);
        logger.debug("  topLimit: {}, bottomLimit: {}", topLimit, bottomLimit);
        logger.debug("  dragPoint.y: {}", dragPoint.y);

        // Check if target is in an existing group
        List<SequenceElement> elementsToCheck = currentDrag != null
            ? currentDrag.getOriginalElements()
            : elements;
        SequenceElement.Type existingGroupType = detectGroupType(elementsToCheck, targetElementIndex);
        logger.debug("  existingGroupType: {}", existingGroupType);

        // Find the current position of the target ability in the current elements list
        String targetAbilityKey = null;
        if (targetElementIndex < elementsToCheck.size()) {
            SequenceElement elem = elementsToCheck.get(targetElementIndex);
            if (elem.isAbility()) {
                targetAbilityKey = elem.getValue();
            }
        }

        logger.debug("  targetAbilityKey from original: {}", targetAbilityKey);

        // Find this ability in the CURRENT elements list
        int currentTargetIndex = -1;
        if (targetAbilityKey != null) {
            for (int i = 0; i < elements.size(); i++) {
                if (elements.get(i).isAbility() && elements.get(i).getValue().equals(targetAbilityKey)) {
                    currentTargetIndex = i;
                    break;
                }
            }
        }

        // Fallback if we couldn't find it
        if (currentTargetIndex == -1) {
            currentTargetIndex = Math.min(targetElementIndex, elements.size() - 1);
        }

        logger.debug("  currentTargetIndex in current elements: {}", currentTargetIndex);

        // Calculate group boundaries in CURRENT elements
        GroupBoundaries groupBounds = analyzeGroupBoundaries(elements, currentTargetIndex, existingGroupType);

        logger.debug("  Group boundaries: start={}, end={}", groupBounds.start, groupBounds.end);

        // Determine zone type based on vertical position
        DropZoneType zoneType;

        if (dragPoint.y < topLimit) {
            // Top zone - AND or group extraction
            zoneType = determineTopZoneType(existingGroupType, currentTargetIndex, groupBounds);
        } else if (dragPoint.y > bottomLimit) {
            // Bottom zone - OR or group extraction
            zoneType = determineBottomZoneType(existingGroupType, currentTargetIndex, groupBounds);
        } else {
            // Middle zone - group continuation or NEXT
            zoneType = determineMiddleZoneType(existingGroupType);
        }

        logger.debug("  Determined zoneType: {}", zoneType);

        // Calculate insertion index based on drop side and zone type
        int insertIndex = calculateInsertionIndex(
            elements,
            currentTargetIndex,
            dropSide,
            zoneType,
            groupBounds
        );

        logger.debug("  Final: insertIndex={}, zoneType={}, dropSide={}, visualIdx={}",
            insertIndex, zoneType, dropSide, targetVisualIndex);

        return new DropPreview(insertIndex, zoneType, targetVisualIndex, dropSide);
    }

    /**
     * Analyzes group boundaries for a given ability.
     */
    private GroupBoundaries analyzeGroupBoundaries(List<SequenceElement> elements,
                                                   int targetIndex,
                                                   SequenceElement.Type groupType) {
        if (groupType == null || targetIndex < 0 || targetIndex >= elements.size()) {
            return new GroupBoundaries(-1, -1);
        }

        int start = targetIndex;
        int end = targetIndex;

        // Scan backwards for group start
        for (int i = targetIndex - 1; i >= 0; i--) {
            SequenceElement elem = elements.get(i);
            if (elem.getType() == groupType) {
                i--;
                if (i >= 0 && elements.get(i).isAbility()) {
                    start = i;
                }
            } else if (elem.isAbility()) {
                break;
            } else {
                break;
            }
        }

        // Scan forwards for group end
        for (int i = targetIndex + 1; i < elements.size(); i++) {
            SequenceElement elem = elements.get(i);
            if (elem.getType() == groupType) {
                i++;
                if (i < elements.size() && elements.get(i).isAbility()) {
                    end = i;
                }
            } else if (elem.isAbility()) {
                break;
            } else {
                break;
            }
        }

        return new GroupBoundaries(start, end);
    }

    /**
     * Determines zone type for top drop area.
     */
    private DropZoneType determineTopZoneType(SequenceElement.Type existingGroupType,
                                              int currentTargetIndex,
                                              GroupBoundaries groupBounds) {
        if (existingGroupType != null && groupBounds.isValid()) {
            boolean isGroupStart = (currentTargetIndex == groupBounds.start);
            if (isGroupStart) {
                return DropZoneType.NEXT; // Extract from group
            }
            return existingGroupType == SequenceElement.Type.PLUS ? DropZoneType.AND : DropZoneType.OR;
        }
        return DropZoneType.AND; // Create new AND group
    }

    /**
     * Determines zone type for bottom drop area.
     */
    private DropZoneType determineBottomZoneType(SequenceElement.Type existingGroupType,
                                                 int currentTargetIndex,
                                                 GroupBoundaries groupBounds) {
        if (existingGroupType != null && groupBounds.isValid()) {
            boolean isGroupEnd = (currentTargetIndex == groupBounds.end);
            if (isGroupEnd) {
                return DropZoneType.NEXT; // Extract from group
            }
            return existingGroupType == SequenceElement.Type.PLUS ? DropZoneType.AND : DropZoneType.OR;
        }
        return DropZoneType.OR; // Create new OR group
    }

    /**
     * Determines zone type for middle drop area.
     */
    private DropZoneType determineMiddleZoneType(SequenceElement.Type existingGroupType) {
        if (existingGroupType != null) {
            return existingGroupType == SequenceElement.Type.PLUS ? DropZoneType.AND : DropZoneType.OR;
        }
        return DropZoneType.NEXT;
    }

    /**
     * Calculates the exact insertion index based on drop side, zone type, and group context.
     */
    private int calculateInsertionIndex(List<SequenceElement> elements,
                                        int currentTargetIndex,
                                        DropSide dropSide,
                                        DropZoneType zoneType,
                                        GroupBoundaries groupBounds) {
        if (zoneType == DropZoneType.NEXT) {
            // For NEXT, handle group boundaries specially
            if (groupBounds.isValid()) {
                if (currentTargetIndex == groupBounds.start && dropSide == DropSide.LEFT) {
                    return currentTargetIndex; // Before group start
                } else if (currentTargetIndex == groupBounds.end && dropSide == DropSide.RIGHT) {
                    return currentTargetIndex + 1; // After group end
                }
            }
            // Standard NEXT insertion
            return dropSide == DropSide.LEFT ? currentTargetIndex : currentTargetIndex + 1;
        }

        // For AND/OR, always insert on the drop side
        return dropSide == DropSide.LEFT ? currentTargetIndex : currentTargetIndex + 1;
    }

    /**
     * Helper class to hold group boundary information.
     */
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

    /**
     * Detects if an ability at the given element index is part of a group.
     * Returns the group's separator type (PLUS or SLASH), or null if standalone.
     */
    private SequenceElement.Type detectGroupType(List<SequenceElement> elements, int abilityIndex) {
        // Bounds check - during drag preview, indices may be out of range
        if (abilityIndex < 0 || abilityIndex >= elements.size()) {
            return null;
        }

        // Check separator immediately after
        if (abilityIndex + 1 < elements.size()) {
            SequenceElement next = elements.get(abilityIndex + 1);
            if (next.isPlus()) {
                return SequenceElement.Type.PLUS;
            } else if (next.isSlash()) {
                return SequenceElement.Type.SLASH;
            }
        }

        // Check separator immediately before
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

    /**
     * Creates a visual copy of the original card for the `floatingCard`.
     * This maintains appearance without moving the original component.
     */
    private JPanel createFloatingCard(JPanel original) {
        JPanel floating = new JPanel();
        floating.setLayout(new BoxLayout(floating, BoxLayout.Y_AXIS));
        floating.setPreferredSize(original.getPreferredSize());
        floating.setSize(original.getSize());
        floating.setBorder(original.getBorder());
        floating.setBackground(original.getBackground());
        floating.setOpaque(true);

        // Create a lightweight visual replica, not a deep clone.
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

    /**
     * Removes the `floatingCard` from the glass pane.
     * Restores the UI to its pre-drag state.
     */
    private void cleanup() {
        indicators.cleanup();
        isDragOutsidePanel = false;

        if (floatingCard != null && glassPane != null) {
            glassPane.remove(floatingCard);
            glassPane.setVisible(false);
            floatingCard = null;
            glassPane = null;
        }
    }

    /**
 * Updates drop zone indicators based on current preview.
 * Shows insertion line based on drop side for visual consistency.
 */
    private void updateIndicators(DropPreview preview) {
        if (!preview.isValid() || preview.getTargetAbilityIndex() < 0) {
            indicators.hideIndicators();
            return;
        }

        Component[] allCards = callback.getAllCards();
        int targetVisualIndex = preview.getTargetAbilityIndex();

        if (targetVisualIndex < 0 || targetVisualIndex >= allCards.length) {
            indicators.hideIndicators();
            return;
        }

        Component targetCard = allCards[targetVisualIndex];

        // Determine which cards to show insertion line between based on drop SIDE
        Component leftCard = null;
        Component rightCard = null;

        if (preview.getDropSide() == DropSide.LEFT) {
            // Dropping on left side - insert before target card
            rightCard = targetCard;
            if (targetVisualIndex > 0) {
                leftCard = allCards[targetVisualIndex - 1];
            }
        } else {
            // Dropping on right side - insert after target card
            leftCard = targetCard;
            if (targetVisualIndex + 1 < allCards.length) {
                rightCard = allCards[targetVisualIndex + 1];
            }
        }

        indicators.showInsertionLineBetweenCards(leftCard, rightCard, preview.getZoneType());
    }
}