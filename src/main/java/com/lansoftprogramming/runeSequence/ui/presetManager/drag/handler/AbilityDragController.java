package com.lansoftprogramming.runeSequence.ui.presetManager.drag.handler;

import com.lansoftprogramming.runeSequence.ui.presetManager.drag.model.DropPreview;
import com.lansoftprogramming.runeSequence.ui.presetManager.drag.model.DropZoneType;
import com.lansoftprogramming.runeSequence.ui.presetManager.model.SequenceElement;
import com.lansoftprogramming.runeSequence.ui.shared.model.AbilityItem;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.swing.*;
import java.awt.*;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.util.ArrayList;
import java.util.List;

/**
 * Manages solitaire-style drag-and-drop for ability cards.
 * It handles visual dragging on the glass pane and real-time drop zone previews.
 */
public class AbilityDragController {

    private static final Logger logger = LoggerFactory.getLogger(AbilityDragController.class);
    private static final int ZONE_HEIGHT = 20;
    private static final int MAX_DROP_DISTANCE = 100;

    private final JPanel flowPanel;
    private final DragCallback callback;

    private DragState currentDrag;
    private JPanel floatingCard;
    private JComponent glassPane;

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
     */
    private DropPreview calculateDropPreview(Point dragPoint) {
        Component[] allCards = callback.getAllCards();
        List<SequenceElement> elements = callback.getCurrentElements();

        if (allCards.length == 0) {
            return new DropPreview(0, DropZoneType.NEXT, -1);
        }

        // Build list of potential drop targets.
        List<Component> visualCards = new ArrayList<>();
        List<Integer> elementIndices = new ArrayList<>();

        for (Component c : allCards) {
            if (currentDrag != null && c == currentDrag.getDraggedCard()) {
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

            if (elementIdx == null || elementIdx < 0 || elementIdx >= elements.size()) {
                continue;
            }

            visualCards.add(c);
            elementIndices.add(elementIdx);
        }

        if (visualCards.isEmpty()) {
            return new DropPreview(elements.size(), DropZoneType.NEXT, -1);
        }

        // Find nearest card to determine drop target.
        Component nearestCard = null;
        int nearestVisualIdx = -1;
        double minDistance = Double.MAX_VALUE;

        for (int i = 0; i < visualCards.size(); i++) {
            Component c = visualCards.get(i);
            Rectangle bounds = c.getBounds();
            Point center = new Point(bounds.x + bounds.width / 2, bounds.y + bounds.height / 2);
            double dist = dragPoint.distance(center);

            if (dist < minDistance) {
                minDistance = dist;
                nearestCard = c;
                nearestVisualIdx = i;
            }
        }

        // No card is close; drop at the end.
        if (nearestCard == null || minDistance > MAX_DROP_DISTANCE) {
            return new DropPreview(elements.size(), DropZoneType.NEXT, visualCards.size() - 1);
        }

        // Drop type (AND/OR/NEXT) depends on vertical position.
        Rectangle nearestBounds = nearestCard.getBounds();
        int topLimit = nearestBounds.y + ZONE_HEIGHT;
        int bottomLimit = nearestBounds.y + nearestBounds.height - ZONE_HEIGHT;
        int targetElementIndex = elementIndices.get(nearestVisualIdx);

        if (dragPoint.y < topLimit) {
            // Drop above the card -> "AND" group.
            return new DropPreview(targetElementIndex, DropZoneType.AND, nearestVisualIdx);
        } else if (dragPoint.y > bottomLimit) {
            // Drop below the card -> "OR" group.
            return new DropPreview(targetElementIndex, DropZoneType.OR, nearestVisualIdx);
        } else {
            // Drop in the middle -> insert as "NEXT" element.
            return new DropPreview(targetElementIndex + 1, DropZoneType.NEXT, nearestVisualIdx);
        }
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
        if (floatingCard != null && glassPane != null) {
            glassPane.remove(floatingCard);
            // Hide glass pane to stop intercepting mouse events.
            glassPane.setVisible(false);
            floatingCard = null;
            glassPane = null;
        }
    }
}