package com.lansoftprogramming.runeSequence.ui.presetManager.palette;

import com.lansoftprogramming.runeSequence.ui.theme.BackgroundFillPainter;
import com.lansoftprogramming.runeSequence.ui.theme.PanelStyle;
import com.lansoftprogramming.runeSequence.ui.theme.Theme;
import com.lansoftprogramming.runeSequence.ui.theme.ThemeManager;

import javax.swing.*;
import java.awt.*;
import java.awt.image.BufferedImage;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/**
 * Custom theme-driven tab bar used to display ability categories without relying on {@link javax.swing.JTabbedPane}.
 * Uses 9-slice rendering based on the active {@link Theme} and switches content via {@link CardLayout}.
 */
public final class TabBar extends JPanel {
	private final TabStripPanel tabsStrip;
	private final TabsStripLayout tabsLayout;
	private final TabContentPanel cards;
	private final CardLayout cardLayout;
	private final List<TabComponent> tabs;
	private int selectedIndex = -1;
	private boolean paintContentBackground = true;
	private transient java.beans.PropertyChangeListener themeListener;

	public TabBar() {
		super(new BorderLayout());
		setOpaque(false);
		setBorder(BorderFactory.createEmptyBorder());

		tabsStrip = new TabStripPanel();
		tabsStrip.setOpaque(false);
		tabsStrip.setBorder(BorderFactory.createEmptyBorder());
		tabsLayout = new TabsStripLayout();
		tabsStrip.setLayout(tabsLayout);

		cardLayout = new CardLayout();
		cards = new TabContentPanel(cardLayout);

		tabs = new ArrayList<>();

		add(tabsStrip, BorderLayout.NORTH);
		add(cards, BorderLayout.CENTER);
	}

	@Override
	public void addNotify() {
		super.addNotify();
		installThemeListener();
		refreshThemeMetrics();
	}

	@Override
	public void removeNotify() {
		uninstallThemeListener();
		super.removeNotify();
	}

	public void addTab(String title, JComponent content) {
		Objects.requireNonNull(title, "title");
		Objects.requireNonNull(content, "content");

		int index = tabs.size();
		String cardName = "tab-" + index;

		TabComponent tab = new TabComponent(this, title, index);
		tabs.add(tab);
		tabsStrip.add(tab);
		cards.add(content, cardName);

		if (selectedIndex < 0) {
			setSelectedIndex(0);
		}

		revalidate();
		repaint();
	}

	public int getTabCount() {
		return tabs.size();
	}

	public int getSelectedIndex() {
		return selectedIndex;
	}

	public void setSelectedIndex(int index) {
		if (index < 0 || index >= tabs.size()) {
			return;
		}
		if (selectedIndex == index) {
			return;
		}

		int previous = selectedIndex;
		selectedIndex = index;

		if (previous >= 0 && previous < tabs.size()) {
			tabs.get(previous).setSelected(false);
		}
		TabComponent selected = tabs.get(index);
		selected.setSelected(true);
		tabsStrip.setSelectedComponent(selected);

		cardLayout.show(cards, "tab-" + index);

		repaint();
	}

	public void setPaintContentBackground(boolean paintContentBackground) {
		if (this.paintContentBackground == paintContentBackground) {
			return;
		}
		this.paintContentBackground = paintContentBackground;
		cards.refreshThemeBackground();
	}

	public void setTitleAt(int index, String title) {
		if (index < 0 || index >= tabs.size()) {
			return;
		}
		tabs.get(index).setTitle(title);
	}

	public void setEnabledAt(int index, boolean enabled) {
		if (index < 0 || index >= tabs.size()) {
			return;
		}
		TabComponent tab = tabs.get(index);
		tab.setEnabled(enabled);
		if (!enabled && selectedIndex == index) {
			// Maintain a valid selection if the active tab becomes disabled.
			for (int i = 0; i < tabs.size(); i++) {
				if (tabs.get(i).isEnabled()) {
					setSelectedIndex(i);
					break;
				}
			}
		}
	}

	private void refreshThemeMetrics() {
		int overlapPx = 0;
		try {
			Theme theme = ThemeManager.getTheme();
			if (theme != null) {
				overlapPx = theme.getTabInterTabOverlapPx();
			}
		} catch (RuntimeException ignored) {
			overlapPx = 0;
		}
		tabsLayout.setOverlapPx(overlapPx);
		cards.refreshThemeBackground();
		for (TabComponent tab : tabs) {
			tab.refreshThemeMetrics();
		}
		revalidate();
		repaint();
	}

	private void installThemeListener() {
		if (themeListener != null) {
			return;
		}
		themeListener = evt -> refreshThemeMetrics();
		ThemeManager.addThemeChangeListener(themeListener);
	}

	private void uninstallThemeListener() {
		if (themeListener == null) {
			return;
		}
		ThemeManager.removeThemeChangeListener(themeListener);
		themeListener = null;
	}

	private final class TabContentPanel extends JPanel {
		private Theme lastTheme;
		private BufferedImage backgroundImage;

		private TabContentPanel(LayoutManager layout) {
			super(layout);
			setOpaque(false);
			setBorder(BorderFactory.createEmptyBorder());
		}

		private void refreshThemeBackground() {
			if (!TabBar.this.paintContentBackground) {
				lastTheme = null;
				backgroundImage = null;
				repaint();
				return;
			}
			Theme theme = ThemeManager.getTheme();
			if (lastTheme == theme && backgroundImage != null) {
				return;
			}
			lastTheme = theme;
			backgroundImage = loadBackgroundImage(theme);
			repaint();
		}

		private BufferedImage loadBackgroundImage(Theme theme) {
			if (theme == null) {
				return null;
			}

			try {
				BufferedImage loaded = theme.getTabContentBackgroundImage();
				if (loaded != null) {
					return loaded;
				}
			} catch (RuntimeException ignored) {
				// Fall back to the theme default.
			}

			try {
				return theme.getPanelBackgroundImage(PanelStyle.DETAIL);
			} catch (RuntimeException ignored) {
				return null;
			}
		}

		@Override
		protected void paintComponent(Graphics graphics) {
			super.paintComponent(graphics);
			if (!TabBar.this.paintContentBackground) {
				return;
			}
			if (backgroundImage == null) {
				return;
			}

			int width = getWidth();
			int height = getHeight();
			if (width <= 0 || height <= 0) {
				return;
			}

			Graphics2D g2 = graphics instanceof Graphics2D ? (Graphics2D) graphics.create() : null;
			if (g2 == null) {
				return;
			}

			try {
				g2.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BILINEAR);
				Insets insets = getInsets();
				int innerX = insets.left;
				int innerY = insets.top;
				int innerWidth = width - insets.left - insets.right;
				int innerHeight = height - insets.top - insets.bottom;
				Rectangle innerRect = new Rectangle(innerX, innerY, innerWidth, innerHeight);
				BackgroundFillPainter.paintTopLeftCropScale(g2, backgroundImage, innerRect);
			} finally {
				g2.dispose();
			}
		}
	}

	/**
	 * Lays out tabs with no visible gaps by slightly overlapping adjacent tabs.
	 * This avoids seams caused by transparent pixels at the edges of the tab artwork.
	 */
	private static final class TabStripPanel extends JPanel {
		private Component selected;

		void setSelectedComponent(Component selected) {
			this.selected = selected;
			repaint();
		}

		@Override
		protected void paintChildren(Graphics g) {
			synchronized (getTreeLock()) {
				for (Component child : getComponents()) {
					if (child == selected) {
						continue;
					}
					paintChild(g, child);
				}
				if (selected != null) {
					paintChild(g, selected);
				}
			}
		}

		@Override
		public boolean isOptimizedDrawingEnabled() {
			return false;
		}

		private void paintChild(Graphics g, Component child) {
			if (child == null || !child.isVisible()) {
				return;
			}
			Graphics childGraphics = g.create(child.getX(), child.getY(), child.getWidth(), child.getHeight());
			try {
				child.paint(childGraphics);
			} finally {
				childGraphics.dispose();
			}
		}
	}

	private static final class TabsStripLayout implements LayoutManager {
		private int overlapPx = 0;

		void setOverlapPx(int overlapPx) {
			this.overlapPx = Math.max(0, overlapPx);
		}

		@Override
		public void addLayoutComponent(String name, Component comp) {
		}

		@Override
		public void removeLayoutComponent(Component comp) {
		}

		@Override
		public Dimension preferredLayoutSize(Container parent) {
			synchronized (parent.getTreeLock()) {
				int count = parent.getComponentCount();
				int width = 0;
				int height = 0;
				int visibleCount = 0;
				for (int i = 0; i < count; i++) {
					Component c = parent.getComponent(i);
					if (!c.isVisible()) {
						continue;
					}
					Dimension d = c.getPreferredSize();
					width += d.width;
					height = Math.max(height, d.height);
					visibleCount++;
				}
				if (visibleCount > 1) {
					width -= overlapPx * (visibleCount - 1);
				}
				Insets insets = parent.getInsets();
				return new Dimension(width + insets.left + insets.right, height + insets.top + insets.bottom);
			}
		}

		@Override
		public Dimension minimumLayoutSize(Container parent) {
			return preferredLayoutSize(parent);
		}

		@Override
		public void layoutContainer(Container parent) {
			synchronized (parent.getTreeLock()) {
				Insets insets = parent.getInsets();
				int x = insets.left;
				int y = insets.top;
				int availableHeight = Math.max(0, parent.getHeight() - insets.top - insets.bottom);

				int count = parent.getComponentCount();
				for (int i = 0; i < count; i++) {
					Component c = parent.getComponent(i);
					if (!c.isVisible()) {
						continue;
					}
					Dimension d = c.getPreferredSize();
					int w = Math.max(0, d.width);
					int h = Math.min(availableHeight, Math.max(0, d.height));
					c.setBounds(x, y, w, h);
					x += w;

					// Overlap with the next visible tab (if any) to eliminate seams.
					boolean hasNextVisible = false;
					for (int j = i + 1; j < count; j++) {
						if (parent.getComponent(j).isVisible()) {
							hasNextVisible = true;
							break;
						}
					}
					if (hasNextVisible) {
						x -= overlapPx;
					}
				}
			}
		}
	}
}
