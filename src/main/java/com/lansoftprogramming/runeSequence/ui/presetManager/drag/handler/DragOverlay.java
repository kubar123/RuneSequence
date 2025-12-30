package com.lansoftprogramming.runeSequence.ui.presetManager.drag.handler;

import com.lansoftprogramming.runeSequence.ui.presetManager.drag.model.DropZoneType;
import com.lansoftprogramming.runeSequence.ui.theme.UiColorPalette;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.swing.*;
import javax.swing.border.Border;
import java.awt.*;
import java.net.URL;

/**
 * Manages the floating drag card on the glass pane and trash icon swapping
 * when the cursor leaves the drop surface.
 */
public class DragOverlay {

	private static final Logger logger = LoggerFactory.getLogger(DragOverlay.class);

	private boolean dragOutsidePanel = false;
	private ImageIcon trashIcon;
	private JLabel floatingIconLabel;
	private Icon originalAbilityIcon;
	private JPanel floatingCard;
	private JLabel floatingActionLabel;
	private DropZoneType floatingActionZone;
	private boolean floatingActionAddsToGroup;
	private JComponent glassPane;

	public DragOverlay() {
		// Preload the scaled trash icon so the first drag-out won't stall.
		ensureTrashIconLoaded();
	}

	public void startFloating(JPanel originalCard, Point startPoint) {
		floatingIconLabel = null;
		originalAbilityIcon = null;
		floatingCard = createFloatingCard(originalCard);
		floatingCard.setName("floatingDragCard");
		floatingActionLabel = createFloatingActionLabel();
		floatingActionLabel.setName("floatingDragActionLabel");
		floatingActionZone = null;
		floatingActionAddsToGroup = false;

		JRootPane rootPane = SwingUtilities.getRootPane(originalCard);
		if (rootPane != null) {
			glassPane = (JComponent) rootPane.getGlassPane();
			glassPane.setLayout(null); // Absolute positioning
			glassPane.add(floatingCard);
			glassPane.add(floatingActionLabel);
			// Keep the floating card behind any overlays that may also live on the glass pane.
			glassPane.setComponentZOrder(floatingCard, glassPane.getComponentCount() - 1);
			ensureFloatingActionLabelAboveCard();
			glassPane.setVisible(true);

			Point glassPanePoint = SwingUtilities.convertPoint(originalCard, startPoint, glassPane);
			updateFloatingPosition(glassPanePoint);
		}
	}

	public void updateFloatingPosition(Point glassPanePoint) {
		if (floatingCard == null || glassPane == null || glassPanePoint == null) {
			return;
		}
		floatingCard.setLocation(
				glassPanePoint.x - floatingCard.getWidth() / 2,
				glassPanePoint.y - floatingCard.getHeight() / 2
		);
		updateFloatingActionLabelPosition();
	}

	public void setDragOutsidePanel(boolean isOutside) {
		this.dragOutsidePanel = isOutside;
		updateFloatingCardAppearance();
		if (isOutside) {
			hideFloatingActionLabel();
		}
	}

	public boolean isDragOutsidePanel() {
		return dragOutsidePanel;
	}

	public JComponent getGlassPane() {
		return glassPane;
	}

	public void setFloatingAction(DropZoneType zoneType, boolean addsToGroup) {
		if (floatingActionLabel == null) {
			return;
		}
		if (zoneType == null || zoneType == DropZoneType.NONE || dragOutsidePanel) {
			hideFloatingActionLabel();
			floatingActionZone = null;
			floatingActionAddsToGroup = false;
			return;
		}

		if (zoneType == floatingActionZone && addsToGroup == floatingActionAddsToGroup && floatingActionLabel.isVisible()) {
			return;
		}

		floatingActionZone = zoneType;
		floatingActionAddsToGroup = addsToGroup;
		floatingActionLabel.setText(resolveFloatingActionText(zoneType, addsToGroup));
		applyFloatingActionStyle(zoneType);
		floatingActionLabel.setSize(floatingActionLabel.getPreferredSize());
		floatingActionLabel.setVisible(true);
		ensureFloatingActionLabelAboveCard();
		updateFloatingActionLabelPosition();
	}

	public void cleanup() {
		if (floatingCard != null && glassPane != null) {
			glassPane.remove(floatingCard);
			if (floatingActionLabel != null) {
				glassPane.remove(floatingActionLabel);
			}
			glassPane.setVisible(false);
			floatingCard = null;
			glassPane = null;
		}
		floatingIconLabel = null;
		originalAbilityIcon = null;
		dragOutsidePanel = false;
		floatingActionLabel = null;
		floatingActionZone = null;
		floatingActionAddsToGroup = false;
	}

	private void updateFloatingCardAppearance() {
		if (floatingCard == null) return;

		cacheFloatingIcon();

		if (floatingIconLabel != null) {
			if (dragOutsidePanel) {
				ensureTrashIconLoaded();
				if (trashIcon != null) {
					floatingIconLabel.setIcon(trashIcon);
				}
			} else {
				floatingIconLabel.setIcon(originalAbilityIcon);
			}
		}

		floatingCard.revalidate();
		floatingCard.repaint();
	}

	private void cacheFloatingIcon() {
		if (floatingIconLabel != null || floatingCard == null) {
			return;
		}
		for (Component comp : floatingCard.getComponents()) {
			if (comp instanceof JLabel label && label.getIcon() != null) {
				floatingIconLabel = label;
				if (originalAbilityIcon == null) {
					originalAbilityIcon = label.getIcon();
				}
				break;
			}
		}
	}

	private void ensureTrashIconLoaded() {
		if (trashIcon != null) {
			return;
		}

		try {
			URL trashUrl = getClass().getResource("/ui/trash-512.png");
			if (trashUrl != null) {
				ImageIcon fullSizeTrash = new ImageIcon(trashUrl);
				Image scaledImage = fullSizeTrash.getImage().getScaledInstance(50, 50, Image.SCALE_SMOOTH);
				trashIcon = new ImageIcon(scaledImage);
			}
		} catch (Exception e) {
			logger.warn("Failed to load trash icon", e);
		}
	}

	private JLabel createFloatingActionLabel() {
		JLabel label = new JLabel("", SwingConstants.CENTER);
		label.setFont(UiColorPalette.boldSans(12));
		label.setOpaque(true);
		label.setVisible(false);
		label.setBorder(createFloatingActionBorder(UiColorPalette.DROP_ZONE_LABEL_BORDER));
		label.setBackground(UiColorPalette.DROP_ZONE_LABEL_BACKGROUND);
		label.setForeground(UiColorPalette.TEXT_INVERSE);
		return label;
	}

	private void applyFloatingActionStyle(DropZoneType zoneType) {
		Color background = UiColorPalette.getDropZoneOverlayColor(zoneType);
		Color border = background != null ? background.darker() : UiColorPalette.DROP_ZONE_LABEL_BORDER;
		Color foreground = UiColorPalette.computeReadableForeground(background);

		floatingActionLabel.setBackground(background);
		floatingActionLabel.setForeground(foreground);
		floatingActionLabel.setBorder(createFloatingActionBorder(border));
	}

	private static Border createFloatingActionBorder(Color borderColor) {
		return BorderFactory.createCompoundBorder(
				UiColorPalette.lineBorder(borderColor, 1),
				BorderFactory.createEmptyBorder(3, 8, 3, 8)
		);
	}

	private static String resolveFloatingActionText(DropZoneType zoneType, boolean addsToGroup) {
		if (zoneType == null) {
			return "";
		}
		return switch (zoneType) {
			case AND -> addsToGroup ? "Add to AND group" : "Create AND group";
			case OR -> addsToGroup ? "Add to OR group" : "Create OR group";
			case NEXT -> "Insert next";
			case NONE -> "";
		};
	}

	private void hideFloatingActionLabel() {
		if (floatingActionLabel == null) {
			return;
		}
		floatingActionLabel.setVisible(false);
	}

	private void ensureFloatingActionLabelAboveCard() {
		if (glassPane == null || floatingActionLabel == null || floatingCard == null) {
			return;
		}

		// Keep the label above the floating card, but below other overlays (drop indicators).
		int labelZOrder = Math.max(0, glassPane.getComponentZOrder(floatingCard) - 1);
		glassPane.setComponentZOrder(floatingActionLabel, labelZOrder);
	}

	private void updateFloatingActionLabelPosition() {
		if (glassPane == null || floatingCard == null || floatingActionLabel == null || !floatingActionLabel.isVisible()) {
			return;
		}

		int gap = 6;

		int x = floatingCard.getX() + (floatingCard.getWidth() - floatingActionLabel.getWidth()) / 2;
		int y = floatingCard.getY() - floatingActionLabel.getHeight() - gap;

		int maxX = Math.max(0, glassPane.getWidth() - floatingActionLabel.getWidth());
		int maxY = Math.max(0, glassPane.getHeight() - floatingActionLabel.getHeight());

		x = Math.min(Math.max(0, x), maxX);
		y = Math.min(Math.max(0, y), maxY);

		floatingActionLabel.setLocation(x, y);
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
				if (floatingIconLabel == null && origLabel.getIcon() != null) {
					floatingIconLabel = newLabel;
					originalAbilityIcon = newLabel.getIcon();
				}
				floating.add(newLabel);
			} else if (comp instanceof Box.Filler) {
				floating.add(Box.createRigidArea(comp.getSize()));
			}
		}
		return floating;
	}
}
