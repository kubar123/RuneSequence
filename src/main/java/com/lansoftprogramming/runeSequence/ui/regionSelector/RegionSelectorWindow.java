package com.lansoftprogramming.runeSequence.ui.regionSelector;

import javax.swing.*;
import javax.swing.border.EmptyBorder;
import java.awt.*;
import java.awt.event.ComponentAdapter;
import java.awt.event.ComponentEvent;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;

public class RegionSelectorWindow extends JDialog {
    private static final int BUTTON_PANEL_HEIGHT = 100;
    private final Rectangle selection;
    private final Rectangle screenBounds;
    private final JButton confirmButton;
    private final RegionOverlay overlay;
    private Point startDrag;
    private boolean selectionMade = false;

    private RegionSelectorWindow(Frame owner) {
        super(owner, "Select Region", true); // Modal dialog
        this.selection = new Rectangle();
        this.confirmButton = new JButton("Confirm Selection");

        setUndecorated(true);
        setBackground(new Color(0, 0, 0, 1)); // Almost fully transparent
        setAlwaysOnTop(true);

        GraphicsEnvironment ge = GraphicsEnvironment.getLocalGraphicsEnvironment();
        this.screenBounds = ge.getMaximumWindowBounds();
        setBounds(screenBounds);

        this.overlay = new RegionOverlay(selection);

        JLayeredPane layeredPane = new JLayeredPane();
        layeredPane.setOpaque(false);
        layeredPane.setLayout(null);
        layeredPane.setPreferredSize(new Dimension(screenBounds.width, screenBounds.height));

        overlay.setBounds(0, 0, screenBounds.width, screenBounds.height);
        layeredPane.add(overlay, JLayeredPane.DEFAULT_LAYER);

        final JPanel buttonPanel = createButtonPanel();
        buttonPanel.setBounds(0, screenBounds.height - BUTTON_PANEL_HEIGHT, screenBounds.width, BUTTON_PANEL_HEIGHT);
        layeredPane.add(buttonPanel, JLayeredPane.PALETTE_LAYER);

        setContentPane(layeredPane);

        layeredPane.addComponentListener(new ComponentAdapter() {
            @Override
            public void componentResized(ComponentEvent e) {
                Dimension size = layeredPane.getSize();
                overlay.setBounds(0, 0, size.width, size.height);
                buttonPanel.setBounds(0, size.height - BUTTON_PANEL_HEIGHT, size.width, BUTTON_PANEL_HEIGHT);
            }
        });

        MouseAdapter dragListener = new MouseAdapter() {
            @Override
            public void mousePressed(MouseEvent e) {
                startDrag = e.getPoint();
                selection.setBounds(startDrag.x, startDrag.y, 0, 0);
                updateConfirmState();
                overlay.repaint();
            }

            @Override
            public void mouseDragged(MouseEvent e) {
                if (startDrag == null) return;
                updateSelection(e.getPoint());
                overlay.repaint();
            }

            @Override
            public void mouseReleased(MouseEvent e) {
                if (startDrag != null) {
                    updateSelection(e.getPoint());
                    startDrag = null;
                    overlay.repaint();
                    updateConfirmState();
                }
            }
        };

        overlay.addMouseListener(dragListener);
        overlay.addMouseMotionListener(dragListener);
    }

    private JPanel createButtonPanel() {
        JPanel buttonPanel = new JPanel();
        buttonPanel.setOpaque(false);
        buttonPanel.setLayout(new BoxLayout(buttonPanel, BoxLayout.X_AXIS));

        Dimension panelSize = new Dimension(screenBounds.width, BUTTON_PANEL_HEIGHT);
        buttonPanel.setPreferredSize(panelSize);
        buttonPanel.setMinimumSize(panelSize);
        buttonPanel.setMaximumSize(panelSize);
        buttonPanel.setBorder(new EmptyBorder(20, 40, 20, 40));

        confirmButton.setEnabled(false);
        confirmButton.setFocusable(false);
        confirmButton.addActionListener(e -> {
            if (!hasValidSelection()) {
                return;
            }
            selectionMade = true;
            dispose();
        });

        JButton cancelButton = new JButton("Cancel");
        cancelButton.setFocusable(false);
        cancelButton.addActionListener(e -> {
            selectionMade = false;
            selection.setBounds(0, 0, 0, 0);
            overlay.repaint();
            dispose();
        });

        buttonPanel.add(Box.createHorizontalGlue());
        buttonPanel.add(cancelButton);
        buttonPanel.add(Box.createRigidArea(new Dimension(15, 0)));
        buttonPanel.add(confirmButton);
        buttonPanel.add(Box.createHorizontalGlue());

        return buttonPanel;
    }

    private void updateSelection(Point currentPoint) {
        if (startDrag == null || currentPoint == null) {
            return;
        }

        int x = Math.min(startDrag.x, currentPoint.x);
        int y = Math.min(startDrag.y, currentPoint.y);
        int w = Math.abs(currentPoint.x - startDrag.x);
        int h = Math.abs(currentPoint.y - startDrag.y);
        selection.setBounds(x, y, w, h);
    }

    private boolean hasValidSelection() {
        return selection.width > 0 && selection.height > 0;
    }

    private void updateConfirmState() {
        confirmButton.setEnabled(hasValidSelection());
    }

    public static RegionSelectorWindow selectRegion() {
        // Create a dummy frame to own the dialog
        final Frame dummyFrame = new JFrame();
        dummyFrame.setUndecorated(true);
        dummyFrame.setOpacity(0.0f);
        dummyFrame.setLocationRelativeTo(null);
        dummyFrame.setVisible(true);


        RegionSelectorWindow dialog = new RegionSelectorWindow(dummyFrame);
        dialog.setVisible(true); // This will block until the dialog is disposed

        // Cleanup the dummy frame
        dummyFrame.dispose();

        if (dialog.isSelectionMade()) {
            return dialog;
        }
        return null; // Return null if no selection was made
    }

    public Rectangle getSelectedRegion() {
        return selectionMade ? new Rectangle(selection) : null;
    }

    public Rectangle getScreenBounds() {
        return new Rectangle(screenBounds);
    }

    public boolean isSelectionMade() {
        return selectionMade;
    }
}
