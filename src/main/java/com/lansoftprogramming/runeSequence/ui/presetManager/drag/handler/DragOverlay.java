package com.lansoftprogramming.runeSequence.ui.presetManager.drag.handler;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.swing.*;
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
	private JComponent glassPane;

	public DragOverlay() {
		// Preload the scaled trash icon so the first drag-out won't stall.
		ensureTrashIconLoaded();
	}

	public void startFloating(JPanel originalCard, Point startPoint) {
		floatingIconLabel = null;
		originalAbilityIcon = null;
		floatingCard = createFloatingCard(originalCard);

		JRootPane rootPane = SwingUtilities.getRootPane(originalCard);
		if (rootPane != null) {
			glassPane = (JComponent) rootPane.getGlassPane();
			glassPane.setLayout(null); // Absolute positioning
			glassPane.add(floatingCard);
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
	}

	public void setDragOutsidePanel(boolean isOutside) {
		this.dragOutsidePanel = isOutside;
		updateFloatingCardAppearance();
	}

	public boolean isDragOutsidePanel() {
		return dragOutsidePanel;
	}

	public JComponent getGlassPane() {
		return glassPane;
	}

	public void cleanup() {
		if (floatingCard != null && glassPane != null) {
			glassPane.remove(floatingCard);
			glassPane.setVisible(false);
			floatingCard = null;
			glassPane = null;
		}
		floatingIconLabel = null;
		originalAbilityIcon = null;
		dragOutsidePanel = false;
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
