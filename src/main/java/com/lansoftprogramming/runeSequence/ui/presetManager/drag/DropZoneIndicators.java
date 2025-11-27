package com.lansoftprogramming.runeSequence.ui.presetManager.drag;

import com.lansoftprogramming.runeSequence.ui.presetManager.drag.model.DropSide;
import com.lansoftprogramming.runeSequence.ui.presetManager.drag.model.DropZoneType;
import com.lansoftprogramming.runeSequence.ui.theme.UiColorPalette;

import javax.swing.*;
import java.awt.*;

/**
 * Visual indicators showing where an ability can be dropped (+ for AND, / for OR).
 * Uses absolute positioning on a glass pane layer.
 * Includes a vertical insertion line for precise drop point feedback.
 */
public class DropZoneIndicators {

    private static final int ABILITY_CARD_HEIGHT = 68;

    private final JLabel topIndicator;
    private final JLabel bottomIndicator;
    private final JPanel insertionLine;
    private JComponent glassPane;

    public DropZoneIndicators() {
        topIndicator = createIndicator("+");
        bottomIndicator = createIndicator("/");
        insertionLine = createInsertionLine();

        topIndicator.setVisible(false);
        bottomIndicator.setVisible(false);
        insertionLine.setVisible(false);
    }

    private JLabel createIndicator(String text) {
        JLabel label = new JLabel(text);
        label.setFont(UiColorPalette.SYMBOL_LARGE);
        label.setForeground(UiColorPalette.TEXT_INVERSE);
        label.setBackground(UiColorPalette.DROP_ZONE_LABEL_BACKGROUND);
        label.setOpaque(true);
        label.setBorder(BorderFactory.createCompoundBorder(
            UiColorPalette.lineBorder(UiColorPalette.DROP_ZONE_LABEL_BORDER, 1),
            BorderFactory.createEmptyBorder(3, 8, 3, 8)
        ));
        label.setHorizontalAlignment(SwingConstants.CENTER);

        return label;
    }

    private JPanel createInsertionLine() {
        JPanel line = new JPanel();
        line.setOpaque(true);
        line.setBackground(UiColorPalette.INSERTION_LINE);
        return line;
    }

    /**
     * Initialize with glass pane for absolute positioning.
     */
    public void setGlassPane(JComponent glassPane) {
        // Remove from old glass pane if exists
        if (this.glassPane != null) {
            this.glassPane.remove(topIndicator);
            this.glassPane.remove(bottomIndicator);
            this.glassPane.remove(insertionLine);
        }

        this.glassPane = glassPane;

        if (glassPane != null) {
            glassPane.add(topIndicator);
            glassPane.add(bottomIndicator);
            glassPane.add(insertionLine);

            // Size the indicators
            topIndicator.setSize(topIndicator.getPreferredSize());
            bottomIndicator.setSize(bottomIndicator.getPreferredSize());
        }
    }

    /**
     * Sets the visual style of an indicator based on its symbol.
     * @param indicator The JLabel to style.
     * @param symbol The symbol ("+" or "/") displayed.
     */
    private void setIndicatorStyle(JLabel indicator, String symbol) {
        DropZoneType zoneType = mapSymbolToZoneType(symbol);
        Color backgroundColor;
        Color foregroundColor;
        Color borderColor;

        if (zoneType != null) {
            backgroundColor = UiColorPalette.getDropZoneOverlayColor(zoneType);
            foregroundColor = UiColorPalette.BASE_BLACK; // Black text for better contrast on light colors
            borderColor = backgroundColor.darker();
        } else {
            backgroundColor = UiColorPalette.DROP_ZONE_LABEL_BACKGROUND;
            foregroundColor = UiColorPalette.TEXT_INVERSE;
            borderColor = UiColorPalette.DROP_ZONE_LABEL_BORDER;
        }

        indicator.setForeground(foregroundColor);
        indicator.setBackground(backgroundColor);
        indicator.setBorder(BorderFactory.createCompoundBorder(
            UiColorPalette.lineBorder(borderColor, 1),
            BorderFactory.createEmptyBorder(3, 8, 3, 8)
        ));
    }

    /**
     * Gets the color for a zone type.
     */
    private Color getZoneColor(DropZoneType zoneType) {
        return UiColorPalette.getDropZoneOverlayColor(zoneType);
    }

    private DropZoneType mapSymbolToZoneType(String symbol) {
        if ("+".equals(symbol)) {
            return DropZoneType.AND;
        }
        if ("/".equals(symbol)) {
            return DropZoneType.OR;
        }
        return null;
    }

    /**
     * Shows indicators above and below the target card.
     * A null symbol will hide the corresponding indicator.
     * @param targetCard The card to show indicators for (in flow panel coordinates)
     * @param topSymbol Symbol to show at top (+ or /), or null to hide.
     * @param bottomSymbol Symbol to show at bottom (+ or /), or null to hide.
     */
    public void showIndicators(Component targetCard, String topSymbol, String bottomSymbol) {
        if (glassPane == null || targetCard == null) {
            hideIndicators();
            return;
        }

        // Convert card bounds to glass pane coordinates
        Rectangle cardBounds = SwingUtilities.convertRectangle(
            targetCard.getParent(),
            targetCard.getBounds(),
            glassPane
        );

        // Show/hide top indicator
        if (topSymbol != null) {
            topIndicator.setText(topSymbol);
            setIndicatorStyle(topIndicator, topSymbol);
            topIndicator.setSize(topIndicator.getPreferredSize());
            int topX = cardBounds.x + (cardBounds.width - topIndicator.getWidth()) / 2;
            int topY = cardBounds.y - topIndicator.getHeight() - 3;
            topIndicator.setLocation(topX, topY);
            topIndicator.setVisible(true);
        } else {
            topIndicator.setVisible(false);
        }

        // Show/hide bottom indicator
        if (bottomSymbol != null) {
            bottomIndicator.setText(bottomSymbol);
            setIndicatorStyle(bottomIndicator, bottomSymbol);
            bottomIndicator.setSize(bottomIndicator.getPreferredSize());
            int bottomX = cardBounds.x + (cardBounds.width - bottomIndicator.getWidth()) / 2;
            int bottomY = cardBounds.y + cardBounds.height + 3;
            bottomIndicator.setLocation(bottomX, bottomY);
            bottomIndicator.setVisible(true);
        } else {
            bottomIndicator.setVisible(false);
        }

        // Hide insertion line when using symbol indicators
    //insertionLine.setVisible(false);

        glassPane.repaint();
    }

    /**
     * Shows an insertion line at the specified position with the given zone type color.
     * @param insertionX X coordinate for the line (in glass pane coordinates)
     * @param insertionY Y coordinate for the line (in glass pane coordinates)
     * @param lineHeight Height of the insertion line
     * @param zoneType The zone type (determines color)
     */
    public void showInsertionLine(int insertionX, int insertionY, int lineHeight, DropZoneType zoneType) {
        if (glassPane == null) {
            return;
        }

        Color lineColor = getZoneColor(zoneType);
        insertionLine.setBackground(lineColor);

        // Line dimensions: 4px wide, with rounded appearance
        int lineWidth = 4;
        insertionLine.setBounds(insertionX - lineWidth / 2, insertionY, lineWidth, lineHeight);
        insertionLine.setVisible(true);

        // Hide symbol indicators when showing insertion line
        topIndicator.setVisible(false);
        bottomIndicator.setVisible(false);

        glassPane.repaint();
    }

    /**
     * Shows insertion line between two cards or at boundaries.
     * @param leftCard Left boundary card (can be null for start position)
     * @param rightCard Right boundary card (can be null for end position)
     * @param zoneType Zone type for color coding
     */
    public void showInsertionLineBetweenCards(Component leftCard, Component rightCard, DropZoneType zoneType, DropSide dropSide) {
        if (glassPane == null) {
            hideIndicators();
            return;
        }

        Rectangle leftBounds = null;
        Rectangle rightBounds = null;

        if (leftCard != null) {
            leftBounds = SwingUtilities.convertRectangle(
                leftCard.getParent(),
                leftCard.getBounds(),
                glassPane
            );
        }

        if (rightCard != null) {
            rightBounds = SwingUtilities.convertRectangle(
                rightCard.getParent(),
                rightCard.getBounds(),
                glassPane
            );
        }

        int insertionX;
        int insertionY;
        int lineHeight;

        boolean rowsDiffer = leftBounds != null && rightBounds != null && leftBounds.y != rightBounds.y;

        if (rowsDiffer && dropSide != null) {
            // When wrap layout puts the boundary cards on different rows, anchor to the row that matches the drop side.
            Rectangle anchorBounds = dropSide == DropSide.LEFT ? rightBounds : leftBounds;
            if (anchorBounds != null) {
                insertionX = dropSide == DropSide.LEFT
                    ? anchorBounds.x - 5
                    : anchorBounds.x + anchorBounds.width + 5;
                insertionY = anchorBounds.y;
                lineHeight = anchorBounds.height;
                showInsertionLine(insertionX, insertionY, lineHeight, zoneType);
                return;
            }
        }

        if (leftBounds != null && rightBounds != null) {
            // Between two cards
            insertionX = (leftBounds.x + leftBounds.width + rightBounds.x) / 2;
            insertionY = Math.min(leftBounds.y, rightBounds.y);
            lineHeight = Math.max(leftBounds.height, rightBounds.height);
        } else if (leftBounds != null) {
            // After last card
            insertionX = leftBounds.x + leftBounds.width + 5;
            insertionY = leftBounds.y;
            lineHeight = leftBounds.height;
        } else if (rightBounds != null) {
            // Before first card
            insertionX = rightBounds.x - 5;
            insertionY = rightBounds.y;
            lineHeight = rightBounds.height;
        } else {
            // No cards (shouldn't happen)
            hideIndicators();
            return;
        }

        showInsertionLine(insertionX, insertionY, lineHeight, zoneType);
    }

    /**
     * Shows an insertion line centered in an empty panel to indicate a valid drop target.
     * @param targetPanel The panel representing the drop surface
     * @param zoneType Zone type for color coding
     */
    public void showInsertionLineForEmptyPanel(JComponent targetPanel, DropZoneType zoneType) {
        if (glassPane == null || targetPanel == null) {
            hideIndicators();
            return;
        }

        Container parent = targetPanel.getParent();
        if (parent == null) {
            hideIndicators();
            return;
        }

        Rectangle panelBounds = SwingUtilities.convertRectangle(
            parent,
            targetPanel.getBounds(),
            glassPane
        );

        int width = panelBounds.width;
        int height = panelBounds.height;

        if (width <= 0 || height <= 0) {
            Dimension preferred = targetPanel.getPreferredSize();
            width = Math.max(width, preferred.width);
            height = Math.max(height, preferred.height);
        }

        if (width <= 0 || height <= 0) {
            hideIndicators();
            return;
        }

        int insertionX = panelBounds.x + width / 2;
        int lineHeight = ABILITY_CARD_HEIGHT;
        int insertionY = panelBounds.y + Math.max((height - lineHeight) / 2, 0);

        showInsertionLine(insertionX, insertionY, lineHeight, zoneType);
    }

    /**
     * Hides all indicators (symbols and insertion line).
     */
    public void hideIndicators() {
        topIndicator.setVisible(false);
        bottomIndicator.setVisible(false);
        insertionLine.setVisible(false);
        if (glassPane != null) {
            glassPane.repaint();
        }
    }

    /**
     * Removes indicators from glass pane.
     */
    public void cleanup() {
        if (glassPane != null) {
            glassPane.remove(topIndicator);
            glassPane.remove(bottomIndicator);
            glassPane.remove(insertionLine);
            glassPane = null;
        }
    }
}
