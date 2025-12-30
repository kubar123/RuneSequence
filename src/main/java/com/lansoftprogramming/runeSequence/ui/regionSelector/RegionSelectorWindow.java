package com.lansoftprogramming.runeSequence.ui.regionSelector;

import com.lansoftprogramming.runeSequence.ui.theme.*;

import javax.swing.*;
import java.awt.*;
import java.awt.event.*;

public class RegionSelectorWindow extends JDialog {
	private static final int ACTION_PANEL_MARGIN = 12;
    private final Rectangle selection;
    private final Rectangle screenBounds;
    private final JButton confirmButton;
	private final JButton cancelButton;
    private final RegionOverlay overlay;
	private final ThemedPanel actionPanel;
	private final GlowPulseOverlay glowOverlay;
    private Point startDrag;
    private boolean selectionMade = false;

    private RegionSelectorWindow(Frame owner) {
        super(owner, "Select Region", true); // Modal dialog
        this.selection = new Rectangle();
	    this.confirmButton = new JButton("Confirm");
        ThemedButtons.apply(confirmButton, ButtonStyle.DEFAULT);
	    this.cancelButton = new JButton("Cancel");
	    ThemedButtons.apply(cancelButton, ButtonStyle.DEFAULT);

        setUndecorated(true);
        setBackground(UiColorPalette.TRANSLUCENT_WINDOW); // Almost fully transparent
        setAlwaysOnTop(true);

        GraphicsEnvironment ge = GraphicsEnvironment.getLocalGraphicsEnvironment();
        this.screenBounds = ge.getMaximumWindowBounds();
        setBounds(screenBounds);

        this.overlay = new RegionOverlay(selection);
	    this.actionPanel = createActionPanel();
	    this.glowOverlay = new GlowPulseOverlay(() -> actionPanel.getBounds(), () -> actionPanel.isVisible());

        JLayeredPane layeredPane = new JLayeredPane();
        layeredPane.setOpaque(false);
        layeredPane.setLayout(null);
        layeredPane.setPreferredSize(new Dimension(screenBounds.width, screenBounds.height));

        overlay.setBounds(0, 0, screenBounds.width, screenBounds.height);
        layeredPane.add(overlay, JLayeredPane.DEFAULT_LAYER);

	    glowOverlay.setBounds(0, 0, screenBounds.width, screenBounds.height);
	    layeredPane.add(glowOverlay, JLayeredPane.PALETTE_LAYER);

	    actionPanel.setVisible(false);
	    layeredPane.add(actionPanel, JLayeredPane.MODAL_LAYER);

        setContentPane(layeredPane);

        layeredPane.addComponentListener(new ComponentAdapter() {
            @Override
            public void componentResized(ComponentEvent e) {
                Dimension size = layeredPane.getSize();
                overlay.setBounds(0, 0, size.width, size.height);
	            glowOverlay.setBounds(0, 0, size.width, size.height);
	            updateActionPanelBounds();
            }
        });

	    installHotkeys(layeredPane);

        MouseAdapter dragListener = new MouseAdapter() {
            @Override
            public void mousePressed(MouseEvent e) {
                startDrag = e.getPoint();
                selection.setBounds(startDrag.x, startDrag.y, 0, 0);
                updateConfirmState();
	            hideActionPanel();
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
	                showActionPanelIfReady();
                }
            }
        };

        overlay.addMouseListener(dragListener);
        overlay.addMouseMotionListener(dragListener);
    }

	private ThemedPanel createActionPanel() {
		ThemedPanel panel = new ThemedPanel(PanelStyle.DIALOG);
		panel.setLayout(new BoxLayout(panel, BoxLayout.Y_AXIS));

		Dimension buttonSize = new Dimension(220, 60);
		applyActionButtonSizing(confirmButton, buttonSize, true);
		applyActionButtonSizing(cancelButton, buttonSize, false);

        confirmButton.setEnabled(false);
        confirmButton.setFocusable(false);
        confirmButton.addActionListener(e -> {
            if (!hasValidSelection()) {
                return;
            }
            selectionMade = true;
            dispose();
        });

        cancelButton.setFocusable(false);
        cancelButton.addActionListener(e -> {
            selectionMade = false;
            selection.setBounds(0, 0, 0, 0);
            overlay.repaint();
            dispose();
        });

		JPanel row = new JPanel();
		row.setOpaque(false);
		row.setLayout(new BoxLayout(row, BoxLayout.X_AXIS));
		row.add(cancelButton);
		row.add(Box.createRigidArea(new Dimension(16, 0)));
		row.add(confirmButton);

		panel.add(row);
		panel.setSize(panel.getPreferredSize());
		return panel;
	}

	private static void applyActionButtonSizing(JButton button, Dimension size, boolean isPrimary) {
		Font font = button.getFont();
		float baseSize = font != null ? font.getSize2D() : 12f;
		float target = Math.max(16f, baseSize + 4f);
		if (font != null) {
			button.setFont(font.deriveFont(isPrimary ? Font.BOLD : Font.PLAIN, target));
		}
		button.setPreferredSize(size);
		button.setMinimumSize(size);
		button.setMaximumSize(size);
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

	private void hideActionPanel() {
		glowOverlay.stopPulse();
		actionPanel.setVisible(false);
	}

	private void showActionPanelIfReady() {
		if (!hasValidSelection()) {
			hideActionPanel();
			return;
		}
		updateActionPanelBounds();
		actionPanel.setVisible(true);
		glowOverlay.activateWithPulse();
		SwingUtilities.invokeLater(() -> confirmButton.requestFocusInWindow());
	}

	private void updateActionPanelBounds() {
		if (actionPanel == null) {
			return;
		}
		if (!hasValidSelection() || startDrag != null) {
			actionPanel.setVisible(false);
			return;
		}

		Dimension preferred = actionPanel.getPreferredSize();
		int width = preferred.width > 0 ? preferred.width : actionPanel.getWidth();
		int height = preferred.height > 0 ? preferred.height : actionPanel.getHeight();
		if (width <= 0 || height <= 0) {
			actionPanel.setSize(actionPanel.getPreferredSize());
			preferred = actionPanel.getPreferredSize();
			width = preferred.width;
			height = preferred.height;
		}

		int centerX = selection.x + (selection.width / 2);
		int centerY = selection.y + (selection.height / 2);
		int x = centerX - (width / 2);
		int y = centerY - (height / 2);

		int maxX = Math.max(ACTION_PANEL_MARGIN, screenBounds.width - width - ACTION_PANEL_MARGIN);
		int maxY = Math.max(ACTION_PANEL_MARGIN, screenBounds.height - height - ACTION_PANEL_MARGIN);
		x = Math.max(ACTION_PANEL_MARGIN, Math.min(x, maxX));
		y = Math.max(ACTION_PANEL_MARGIN, Math.min(y, maxY));

		actionPanel.setBounds(x, y, width, height);
		actionPanel.revalidate();
		actionPanel.repaint();
	}

	private void installHotkeys(JComponent root) {
		root.getInputMap(JComponent.WHEN_IN_FOCUSED_WINDOW)
				.put(KeyStroke.getKeyStroke(KeyEvent.VK_ESCAPE, 0), "cancel-selection");
		root.getActionMap().put("cancel-selection", new AbstractAction() {
			@Override
			public void actionPerformed(java.awt.event.ActionEvent e) {
				cancelButton.doClick();
			}
		});

		getRootPane().setDefaultButton(confirmButton);
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

	private static final class GlowPulseOverlay extends JComponent {
		private static final int GLOW_PX = 14;
		private static final int FADE_MS = 650;
		private static final int TICK_MS = 25;
		private static final float BASE_ALPHA = 0.28f;

		private final java.util.function.Supplier<Rectangle> targetBoundsSupplier;
		private final java.util.function.BooleanSupplier targetVisibleSupplier;

		private transient Timer timer;
		private transient long pulseStartMs;
		private transient float alpha;

		private GlowPulseOverlay(java.util.function.Supplier<Rectangle> targetBoundsSupplier,
		                         java.util.function.BooleanSupplier targetVisibleSupplier) {
			this.targetBoundsSupplier = targetBoundsSupplier;
			this.targetVisibleSupplier = targetVisibleSupplier;
			setOpaque(false);
			setVisible(false);
		}

		@Override
		public boolean contains(int x, int y) {
			return false;
		}

		void activateWithPulse() {
			pulseStartMs = System.currentTimeMillis();
			alpha = 1f;
			setVisible(true);
			ensureTimer();
			repaint();
		}

		void stopPulse() {
			alpha = 0f;
			if (timer != null) {
				timer.stop();
			}
			setVisible(false);
			repaint();
		}

		private void ensureTimer() {
			if (timer != null && timer.isRunning()) {
				return;
			}
			if (timer == null) {
				timer = new Timer(TICK_MS, e -> onTick());
			}
			timer.start();
		}

		private void onTick() {
			long elapsed = System.currentTimeMillis() - pulseStartMs;
			float t = FADE_MS <= 0 ? 1f : Math.min(1f, elapsed / (float) FADE_MS);
			alpha = BASE_ALPHA + ((1f - BASE_ALPHA) * (1f - t));
			if (alpha <= BASE_ALPHA) {
				alpha = BASE_ALPHA;
				if (timer != null) {
					timer.stop();
				}
			}
			repaint();
		}

		@Override
		protected void paintComponent(Graphics g) {
			if (alpha <= 0f || !targetVisibleSupplier.getAsBoolean()) {
				return;
			}
			Rectangle target = targetBoundsSupplier.get();
			if (target == null || target.width <= 0 || target.height <= 0) {
				return;
			}

			Graphics2D g2 = (Graphics2D) g.create();
			try {
				g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
				g2.setStroke(new BasicStroke(1f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));

				int x = target.x - GLOW_PX;
				int y = target.y - GLOW_PX;
				int w = target.width + (GLOW_PX * 2);
				int h = target.height + (GLOW_PX * 2);
				if (w <= 2 || h <= 2) {
					return;
				}

				int arc = Math.min(18, Math.min(w, h) / 3);
				float baseCornerRadius = arc / 2f;
				Color base = UiColorPalette.DIALOG_TITLE_GOLD;

				for (int i = 0; i < GLOW_PX; i++) {
					float ringT = (GLOW_PX <= 1) ? 1f : (i / (float) (GLOW_PX - 1));
					float ringAlphaStrength = 0.10f + (0.85f * (float) Math.pow(ringT, 1.25f));
					float ringAlpha = alpha * ringAlphaStrength;

					int r = lerp(255, base.getRed(), ringT);
					int gr = lerp(235, base.getGreen(), ringT);
					int b = lerp(140, base.getBlue(), ringT);
					g2.setColor(new Color(r, gr, b, Math.round(255 * clamp01(ringAlpha))));

					int inset = i;
					int rx = x + inset;
					int ry = y + inset;
					int rw = w - 1 - (inset * 2);
					int rh = h - 1 - (inset * 2);
					if (rw <= 0 || rh <= 0) {
						break;
					}

					int arcNow = Math.max(0, Math.round((Math.max(0f, baseCornerRadius - inset)) * 2f));
					if (arcNow > 0) {
						g2.drawRoundRect(rx, ry, rw, rh, arcNow, arcNow);
					} else {
						g2.drawRect(rx, ry, rw, rh);
					}
				}
			} finally {
				g2.dispose();
			}
		}

		private static int lerp(int a, int b, float t) {
			return Math.round(a + (b - a) * t);
		}

		private static float clamp01(float v) {
			return Math.max(0f, Math.min(1f, v));
		}
	}
}