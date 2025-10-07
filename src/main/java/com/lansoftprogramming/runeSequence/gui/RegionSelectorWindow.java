package com.lansoftprogramming.runeSequence.gui;

import javax.swing.*;
import java.awt.*;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.util.concurrent.atomic.AtomicReference;

public class RegionSelectorWindow extends JDialog {
    private final Rectangle selection;
    private final Rectangle screenBounds;
    private Point startDrag;
    private boolean selectionMade = false;

    private RegionSelectorWindow(Frame owner) {
        super(owner, "Select Region", true); // Modal dialog
        this.selection = new Rectangle();

        setUndecorated(true);
        setBackground(new Color(0, 0, 0, 1)); // Almost fully transparent
        setAlwaysOnTop(true);

        GraphicsEnvironment ge = GraphicsEnvironment.getLocalGraphicsEnvironment();
        this.screenBounds = ge.getMaximumWindowBounds();
        setBounds(screenBounds);

        RegionOverlay overlay = new RegionOverlay(selection);
        setContentPane(overlay);

        MouseAdapter dragListener = new MouseAdapter() {
            @Override
            public void mousePressed(MouseEvent e) {
                startDrag = e.getPoint();
                selection.setBounds(startDrag.x, startDrag.y, 0, 0);
            }

            @Override
            public void mouseDragged(MouseEvent e) {
                if (startDrag == null) return;
                int x = Math.min(startDrag.x, e.getX());
                int y = Math.min(startDrag.y, e.getY());
                int w = Math.abs(e.getX() - startDrag.x);
                int h = Math.abs(e.getY() - startDrag.y);
                selection.setBounds(x, y, w, h);
                overlay.repaint();
            }

            @Override
            public void mouseReleased(MouseEvent e) {
                if (startDrag != null) {
                    selectionMade = true;
                    dispose();
                }
            }
        };

        overlay.addMouseListener(dragListener);
        overlay.addMouseMotionListener(dragListener);
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