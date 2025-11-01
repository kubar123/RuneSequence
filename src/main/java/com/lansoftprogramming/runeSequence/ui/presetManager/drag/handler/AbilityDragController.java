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
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Manages card-style drag-and-drop for abilities.
 * It handles visual dragging on the glass pane and real-time drop zone previews.
 */
public class AbilityDragController {

    private static final Logger logger = LoggerFactory.getLogger(AbilityDragController.class);
    private static final int ZONE_HEIGHT = 20;
    private static final int MAX_DROP_DISTANCE = 100;
    private static final int GROUP_EDGE_TOLERANCE = 8;

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
        void onDragStart(AbilityItem item, boolean isFromPalette, int abilityIndex);
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
                // re-ordering existing sequence cards.
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

        callback.onDragStart(item, isFromPalette, originalIndex);
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

    /**
     * Calculates the drop position and type ("AND", "OR", "NEXT") within the sequence.
     * Uses the nearest card and vertical drag point to determine the precise insertion logic.
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
        Map<String, Integer> abilityOccurrences = new HashMap<>();

        for (int idx = 0; idx < allCards.length; idx++) {
            Component c = allCards[idx];

            if (currentDrag != null && c == currentDrag.getDraggedCard()) {
                if (releasePhaseLogging) {
                    logger.info("Skipping dragged card at index {}", idx);
                }
                continue;
            }

            Integer elementIdx = extractElementIndex(c);
            String abilityKey = extractAbilityKey(c);
            if (abilityKey == null) {
                continue;
            }
            int occurrence = abilityOccurrences.getOrDefault(abilityKey, 0);
            abilityOccurrences.put(abilityKey, occurrence + 1);

            int mappedIndex = mapAbilityToPreviewIndex(elements, abilityKey, occurrence);
            if (mappedIndex < 0) {
                if (releasePhaseLogging) {
                    logger.info("Skipping card {}: could not map key={} occurrence={} into preview elements", idx, abilityKey, occurrence);
                }
                continue;
            }

            visualCards.add(c);
            elementIndices.add(mappedIndex);
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
        boolean beyondLeftEdge = dragPoint.x < nearestBoundsInFlowPanel.x - GROUP_EDGE_TOLERANCE;
        boolean beyondRightEdge = dragPoint.x > nearestBoundsInFlowPanel.x + nearestBoundsInFlowPanel.width + GROUP_EDGE_TOLERANCE;

        int topLimit = nearestBoundsInFlowPanel.y + ZONE_HEIGHT;
        int bottomLimit = nearestBoundsInFlowPanel.y + nearestBoundsInFlowPanel.height - ZONE_HEIGHT;
        int targetElementIndex = elementIndices.get(nearestListIdx);
        int targetVisualIndex = originalIndices.get(nearestListIdx);

        List<SequenceElement> elementsToCheck = currentDrag != null
            ? currentDrag.getOriginalElements()
            : elements;
        DropZoneType existingGroupZone = extractGroupZoneFromCard(nearestCard);

        String targetAbilityKey = null;
        if (targetElementIndex < elementsToCheck.size()) {
            SequenceElement elem = elementsToCheck.get(targetElementIndex);
            if (elem.isAbility()) {
                targetAbilityKey = elem.getValue();
            }
        }

        int currentTargetIndex = isAbilityAtIndex(elements, targetElementIndex)
            ? targetElementIndex
            : findNearestAbilityIndex(elements, targetElementIndex);
        if (currentTargetIndex == -1 && targetAbilityKey != null) {
            currentTargetIndex = findAbilityIndexByKey(elements, targetAbilityKey, targetElementIndex);
            if (releasePhaseLogging && currentTargetIndex != -1 && currentTargetIndex != targetElementIndex) {
                logger.info("Adjusted target index via key match: preferred={}, resolved={}", targetElementIndex, currentTargetIndex);
            }
        }
        if (currentTargetIndex == -1 && !elements.isEmpty()) {
            currentTargetIndex = Math.max(0, Math.min(targetElementIndex, elements.size() - 1));
            if (releasePhaseLogging) {
                logger.info("Preview fallback: using clamped index {} (original {}, elementsSize={})",
                    currentTargetIndex,
                    targetElementIndex,
                    elements.size()
                );
            }
        }
        if (releasePhaseLogging && currentTargetIndex != -1 && currentTargetIndex != targetElementIndex) {
            logger.info("Aligned target index from {} to {}", targetElementIndex, currentTargetIndex);
        }
        if (currentTargetIndex < 0) {
            currentTargetIndex = 0;
        }

        if (existingGroupZone == null) {
            existingGroupZone = resolveGroupZoneAt(elementsToCheck, currentTargetIndex);
        }
        if (existingGroupZone == null) {
            existingGroupZone = resolveGroupZoneAt(elements, currentTargetIndex);
        }

        GroupBoundaries groupBounds = analyzeGroupBoundaries(elements, currentTargetIndex, existingGroupZone);

        DropZoneType zoneType;
        if (dragPoint.y < topLimit) {
            zoneType = determineTopZoneType(existingGroupZone, currentTargetIndex, groupBounds);
        } else if (dragPoint.y > bottomLimit) {
            zoneType = determineBottomZoneType(existingGroupZone, currentTargetIndex, groupBounds);
        } else {
            zoneType = determineMiddleZoneType(existingGroupZone, currentTargetIndex, groupBounds, dropSide, beyondLeftEdge, beyondRightEdge);
        }

		if (releasePhaseLogging) {
			logger.info("Zone decision: cursorY={}, topLimit={}, bottomLimit={}, zone={}, groupZone={}, targetElementIndex={}, targetVisualIndex={}",
				dragPoint.y,
				topLimit,
				bottomLimit,
				zoneType,
				existingGroupZone,
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
			logger.info("Insert decision: resolvedIndex={}, currentTargetIndex={}, dropSide={}, zone={}, groupBounds=[{},{}], groupZone={}, targetKey={}",
				insertIndex,
				currentTargetIndex,
				dropSide,
				zoneType,
				groupBounds.start,
				groupBounds.end,
				existingGroupZone,
				targetAbilityKey
			);
		}

        return new DropPreview(insertIndex, zoneType, targetVisualIndex, dropSide);
    }

    private GroupBoundaries analyzeGroupBoundaries(List<SequenceElement> elements,
                                                   int targetIndex,
                                                   DropZoneType zoneType) {
        SequenceElement.Type separatorType = separatorForZone(zoneType);
        if (separatorType == null || targetIndex < 0 || targetIndex >= elements.size()) {
            return new GroupBoundaries(-1, -1);
        }
        int start = targetIndex;
        int end = targetIndex;
        for (int i = targetIndex - 1; i >= 0; i--) {
            SequenceElement elem = elements.get(i);
            if (elem.getType() == separatorType) {
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
            if (elem.getType() == separatorType) {
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

    private DropZoneType determineTopZoneType(DropZoneType existingGroupZone,
                                               int currentTargetIndex,
                                               GroupBoundaries groupBounds) {
        if (existingGroupZone != null && groupBounds.isValid()) {
            boolean isGroupStart = (currentTargetIndex == groupBounds.start);
            if (isGroupStart) {
                return DropZoneType.NEXT;
            }
            return existingGroupZone;
        }
        return DropZoneType.AND;
    }

    private DropZoneType determineBottomZoneType(DropZoneType existingGroupZone,
                                                  int currentTargetIndex,
                                                  GroupBoundaries groupBounds) {
        if (existingGroupZone != null && groupBounds.isValid()) {
            boolean isGroupEnd = (currentTargetIndex == groupBounds.end);
            if (isGroupEnd) {
                return DropZoneType.NEXT;
            }
            return existingGroupZone;
        }
        return DropZoneType.OR;
    }

    private DropZoneType determineMiddleZoneType(DropZoneType existingGroupZone,
                                                 int currentTargetIndex,
                                                 GroupBoundaries groupBounds,
                                                 DropSide dropSide,
                                                 boolean beyondLeftEdge,
                                                 boolean beyondRightEdge) {
        if (existingGroupZone != null && groupBounds.isValid()) {
            boolean atGroupStart = currentTargetIndex == groupBounds.start;
            boolean atGroupEnd = currentTargetIndex == groupBounds.end;
            boolean leavingLeft = atGroupStart && dropSide == DropSide.LEFT && beyondLeftEdge;
            boolean leavingRight = atGroupEnd && dropSide == DropSide.RIGHT && beyondRightEdge;
            if (leavingLeft || leavingRight) {
                return DropZoneType.NEXT;
            }
            return existingGroupZone;
        }
        return existingGroupZone != null ? existingGroupZone : DropZoneType.NEXT;
    }

    private int calculateInsertionIndex(List<SequenceElement> elements,
                                        int currentTargetIndex,
                                        DropSide dropSide,
                                        DropZoneType zoneType,
                                        GroupBoundaries groupBounds) {
        if (zoneType == DropZoneType.NEXT && groupBounds.isValid()) {
            if (currentTargetIndex == groupBounds.start && dropSide == DropSide.LEFT)
                return currentTargetIndex;
            if (currentTargetIndex == groupBounds.end && dropSide == DropSide.RIGHT)
                return currentTargetIndex + 1;
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

    // Reads any cached zone hint from the view layer, sparing repeated element scans during drag.
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

    // Infers the active zone by inspecting separators around the ability when the UI provides no hint.
    private DropZoneType resolveGroupZoneAt(List<SequenceElement> elements, int abilityIndex) {
        if (abilityIndex < 0 || abilityIndex >= elements.size()) {
            return null;
        }
        if (abilityIndex + 1 < elements.size()) {
            DropZoneType zone = zoneForSeparator(elements.get(abilityIndex + 1).getType());
            if (zone != null) {
                return zone;
            }
        }
        if (abilityIndex - 1 >= 0) {
            DropZoneType zone = zoneForSeparator(elements.get(abilityIndex - 1).getType());
            if (zone != null) {
                return zone;
            }
        }
        return null;
    }

    // Converts a zone intent into the separator token the expression builder expects.
    private SequenceElement.Type separatorForZone(DropZoneType zoneType) {
        if (zoneType == null) {
            return null;
        }
        return switch (zoneType) {
            case AND -> SequenceElement.Type.PLUS;
            case OR -> SequenceElement.Type.SLASH;
            default -> null;
        };
    }

    // Mirror mapping for quickly shaping a separator back into a zone decision.
    private DropZoneType zoneForSeparator(SequenceElement.Type type) {
        if (type == null) {
            return null;
        }
        return switch (type) {
            case PLUS -> DropZoneType.AND;
            case SLASH -> DropZoneType.OR;
            default -> null;
        };
    }

    // Picks the glyph shown to the user when highlighting a group’s zone.
    private String symbolForZone(DropZoneType zoneType) {
        if (zoneType == null) {
            return null;
        }
        return switch (zoneType) {
            case AND -> "+";
            case OR -> "/";
            default -> null;
        };
    }

    // Resolves duplicate ability keys by favouring the occurrence closest to the visual index we targeted.
    private int findAbilityIndexByKey(List<SequenceElement> elements, String abilityKey, int preferredIndex) {
        if (abilityKey == null || elements.isEmpty()) {
            return -1;
        }
        int bestIndex = -1;
        int bestDistance = Integer.MAX_VALUE;
        for (int i = 0; i < elements.size(); i++) {
            SequenceElement element = elements.get(i);
            if (!element.isAbility() || !abilityKey.equals(element.getValue())) {
                continue;
            }
            int distance = preferredIndex >= 0 ? Math.abs(i - preferredIndex) : 0;
            if (distance < bestDistance) {
                bestDistance = distance;
                bestIndex = i;
                if (distance == 0) {
                    break;
                }
            }
        }
        return bestIndex;
    }

    private int mapAbilityToPreviewIndex(List<SequenceElement> elements, String abilityKey, int occurrence) {
        if (abilityKey == null || occurrence < 0) {
            return -1;
        }
        int matchCount = 0;
        for (int i = 0; i < elements.size(); i++) {
            SequenceElement element = elements.get(i);
            if (!element.isAbility()) {
                continue;
            }
            if (abilityKey.equals(element.getValue())) {
                if (matchCount == occurrence) {
                    return i;
                }
                matchCount++;
            }
        }
        return -1;
    }

    private boolean isAbilityAtIndex(List<SequenceElement> elements, int index) {
        return index >= 0 && index < elements.size() && elements.get(index).isAbility();
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
			resetIndicatorState();
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
			resetIndicatorState();
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

        DropZoneType indicatorZone = extractGroupZoneFromCard(targetCard);
        if (indicatorZone == DropZoneType.NEXT) {
            indicatorZone = null;
        }

        if (indicatorZone == null) {
            List<SequenceElement> elementsToCheck = currentDrag != null
                ? currentDrag.getOriginalElements()
                : callback.getCurrentElements();

            Integer elementIdx = extractElementIndex(targetCard);
            if (elementIdx == null) {
                return;
            }

            indicatorZone = resolveGroupZoneAt(elementsToCheck, elementIdx);
        }

        String groupSymbol = symbolForZone(indicatorZone);
        String topSymbol = groupSymbol != null ? groupSymbol : "+";
        String bottomSymbol = groupSymbol != null ? groupSymbol : "/";

        indicators.showIndicators(targetCard, topSymbol, bottomSymbol);
    }

    // Ensures subsequent logic anchors on an ability node even when the hint lands on a separator.
    private int findNearestAbilityIndex(List<SequenceElement> elements, int hintIndex) {
        if (elements.isEmpty()) {
            return -1;
        }
        int clampedIndex = Math.max(0, Math.min(hintIndex, elements.size() - 1));
        if (elements.get(clampedIndex).isAbility()) {
            return clampedIndex;
        }
        int left = clampedIndex - 1;
        int right = clampedIndex + 1;
        while (left >= 0 || right < elements.size()) {
            if (left >= 0 && elements.get(left).isAbility()) {
                return left;
            }
            if (right < elements.size() && elements.get(right).isAbility()) {
                return right;
            }
            left--;
            right++;
        }
        return -1;
    }

	private boolean targetVisualIndexEquals(Integer idx) {
		return lastIndicatorVisualIdx != null && lastIndicatorVisualIdx.equals(idx);
	}

	private void resetIndicatorState() {
		lastIndicatorVisualIdx = null;
		lastIndicatorDropSide = null;
		lastIndicatorZoneType = null;
		hasLoggedIndicatorShown = false;
	}
}