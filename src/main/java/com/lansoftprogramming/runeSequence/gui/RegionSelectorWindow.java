package com.lansoftprogramming.runeSequence.gui;

import javax.swing.*;
import java.awt.*;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;

public class RegionSelectorWindow extends JWindow {
	private final Rectangle selection = new Rectangle();
	private Point startDrag;
	private Rectangle screenBounds;

	public RegionSelectorWindow() {
		setAlwaysOnTop(true);
		setBackground(new Color(0, 0, 0, 50)); // semi-transparent background

		// full screen across all monitors
		GraphicsEnvironment ge = GraphicsEnvironment.getLocalGraphicsEnvironment();
		screenBounds = ge.getMaximumWindowBounds();
		setBounds(screenBounds);

		RegionOverlay overlay = new RegionOverlay(selection);
		setContentPane(overlay);

		MouseAdapter dragListener = new MouseAdapter() {
			@Override
			public void mousePressed(MouseEvent e) {
				startDrag = e.getPoint();
				selection.setBounds(startDrag.x, startDrag.y, 0, 0);
				overlay.repaint();
			}

			@Override
			public void mouseDragged(MouseEvent e) {
				int x = Math.min(startDrag.x, e.getX());
				int y = Math.min(startDrag.y, e.getY());
				int w = Math.abs(e.getX() - startDrag.x);
				int h = Math.abs(e.getY() - startDrag.y);
				selection.setBounds(x, y, w, h);
				overlay.repaint();
			}

			@Override
			public void mouseReleased(MouseEvent e) {
				dispose();
				System.out.println("Selected region: " + selection);
			}
		};

		overlay.addMouseListener(dragListener);
		overlay.addMouseMotionListener(dragListener);
	}

	public Rectangle getSelectedRegion() {
		return new Rectangle(selection);
	}

	public Rectangle getScreenBounds() {
		return new Rectangle(screenBounds);
	}

	public static void launch() {
		SwingUtilities.invokeLater(() -> {
			RegionSelectorWindow win = new RegionSelectorWindow();
			win.setVisible(true);
		});
	}
}