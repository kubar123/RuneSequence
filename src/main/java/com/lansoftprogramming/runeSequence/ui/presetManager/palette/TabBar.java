package com.lansoftprogramming.runeSequence.ui.presetManager.palette;

import com.lansoftprogramming.runeSequence.ui.theme.*;

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
	private JComponent trailingComponent;
	private int selectedIndex = -1;
	private boolean paintContentBackground = true;
	private int tabsContentOverlapPx = 1;
	private int tabsTrailingGapPx = 10;
	private transient java.beans.PropertyChangeListener themeListener;

	public TabBar() {
		super();
		setOpaque(false);
		setBorder(BorderFactory.createEmptyBorder());
		setLayout(new TabBarLayout());

		cardLayout = new CardLayout();
		cards = new TabContentPanel(cardLayout);

		tabsStrip = new TabStripPanel();
		tabsStrip.setOpaque(false);
		tabsStrip.setBorder(BorderFactory.createEmptyBorder());
		tabsLayout = new TabsStripLayout();
		tabsStrip.setLayout(tabsLayout);

		tabs = new ArrayList<>();

		add(cards);
		add(tabsStrip);
		// Ensure tabs are painted on top when bounds overlap.
		setComponentZOrder(tabsStrip, 0);
		setComponentZOrder(cards, 1);
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

	@Override
	public boolean isOptimizedDrawingEnabled() {
		return false;
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

	public void setTrailingComponent(JComponent component) {
		if (trailingComponent == component) {
			return;
		}
		if (trailingComponent != null) {
			remove(trailingComponent);
		}
		trailingComponent = component;
		if (trailingComponent != null) {
			add(trailingComponent);
			// Ensure trailing component stays visible if bounds overlap.
			setComponentZOrder(trailingComponent, 0);
			setComponentZOrder(tabsStrip, 1);
			setComponentZOrder(cards, 2);
		} else {
			setComponentZOrder(tabsStrip, 0);
			setComponentZOrder(cards, 1);
		}
		revalidate();
		repaint();
	}

	public void setTabsTrailingGapPx(int gapPx) {
		int clamped = Math.max(0, gapPx);
		if (tabsTrailingGapPx == clamped) {
			return;
		}
		tabsTrailingGapPx = clamped;
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
		cards.setPaintThemedBackground(paintContentBackground);
	}

	public void setTabsContentOverlapPx(int overlapPx) {
		int clamped = Math.max(0, overlapPx);
		if (tabsContentOverlapPx == clamped) {
			return;
		}
		tabsContentOverlapPx = clamped;
		revalidate();
		repaint();
	}

	@Override
	protected void paintChildren(Graphics g) {
		super.paintChildren(g);
		Graphics2D g2 = g instanceof Graphics2D ? (Graphics2D) g.create() : null;
		if (g2 == null) {
			return;
		}
		try {
			// Ensure the marker draws on top of all children (including tab text) without inheriting a child clip.
			g2.setClip(0, 0, getWidth(), getHeight());
			paintOpenedMarker(g2);
		} finally {
			g2.dispose();
		}
	}

	private void paintOpenedMarker(Graphics2D g) {
		if (g == null) {
			return;
		}
		if (selectedIndex < 0 || selectedIndex >= tabs.size()) {
			return;
		}
		TabComponent selected = tabs.get(selectedIndex);
		if (selected == null || !selected.isVisible()) {
			return;
		}

		Theme theme = ThemeManager.getTheme();
		if (theme == null) {
			return;
		}

		BufferedImage marker;
		int anchorFromBottomPx;
		try {
			marker = theme.getTabOpenedMarkerImage();
			anchorFromBottomPx = theme.getTabOpenedMarkerAnchorFromBottomPx();
		} catch (RuntimeException ignored) {
			return;
		}
		if (marker == null) {
			return;
		}

		int markerWidth = marker.getWidth();
		int markerHeight = marker.getHeight();
		if (markerWidth <= 0 || markerHeight <= 0) {
			return;
		}

		Point tabLocation = SwingUtilities.convertPoint(tabsStrip, selected.getX(), selected.getY(), this);
		int tabX = tabLocation.x;
		int tabY = tabLocation.y;
		int tabWidth = selected.getWidth();
		int tabHeight = selected.getHeight();
		if (tabWidth <= 0 || tabHeight <= 0) {
			return;
		}

		int markerX = tabX + (tabWidth - markerWidth) / 2;
		int anchor = Math.max(0, Math.min(markerHeight, anchorFromBottomPx));
		int markerY = tabY + (tabHeight - 1) - (markerHeight - anchor);

		g.drawImage(marker, markerX, markerY, null);
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
		cards.setPaintThemedBackground(paintContentBackground);
		cards.revalidate();
		cards.repaint();
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
		private boolean paintThemedBackground;

		private TabContentPanel(LayoutManager layout) {
			super(layout);
			setOpaque(false);
			setPaintThemedBackground(true);
		}

		private void setPaintThemedBackground(boolean paintThemedBackground) {
			if (this.paintThemedBackground == paintThemedBackground) {
				return;
			}
			this.paintThemedBackground = paintThemedBackground;
			setBorder(paintThemedBackground
					? new ThemedPanelBorder(PanelStyle.TAB_CONTENT)
					: BorderFactory.createEmptyBorder());
			revalidate();
			repaint();
		}

		@Override
		protected void paintComponent(Graphics graphics) {
			super.paintComponent(graphics);
			if (!paintThemedBackground) {
				return;
			}

			int width = getWidth();
			int height = getHeight();
			if (width <= 0 || height <= 0) {
				return;
			}

			Theme theme = ThemeManager.getTheme();
			if (theme == null) {
				return;
			}

			BufferedImage backgroundImage;
			try {
				backgroundImage = theme.getPanelBackgroundImage(PanelStyle.TAB_CONTENT);
			} catch (RuntimeException ignored) {
				return;
			}
			if (backgroundImage == null) {
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
	private final class TabBarLayout implements LayoutManager {
		@Override
		public void addLayoutComponent(String name, Component comp) {
		}

		@Override
		public void removeLayoutComponent(Component comp) {
		}

		@Override
		public Dimension preferredLayoutSize(Container parent) {
			synchronized (parent.getTreeLock()) {
				Insets insets = parent.getInsets();
				Dimension tabsPref = tabsStrip.getPreferredSize();
				Dimension trailingPref = trailingComponent != null ? trailingComponent.getPreferredSize() : new Dimension(0, 0);
				Dimension cardsPref = cards.getPreferredSize();
				int overlap = Math.min(tabsContentOverlapPx, tabsPref.height);
				int trailingWidth = trailingPref.width > 0 ? trailingPref.width + tabsTrailingGapPx : 0;
				int width = Math.max(tabsPref.width + trailingWidth, cardsPref.width) + insets.left + insets.right;
				int height = tabsPref.height + cardsPref.height - overlap + insets.top + insets.bottom;
				return new Dimension(Math.max(0, width), Math.max(0, height));
			}
		}

		@Override
		public Dimension minimumLayoutSize(Container parent) {
			synchronized (parent.getTreeLock()) {
				Insets insets = parent.getInsets();
				Dimension tabsMin = tabsStrip.getMinimumSize();
				Dimension trailingMin = trailingComponent != null ? trailingComponent.getMinimumSize() : new Dimension(0, 0);
				Dimension cardsMin = cards.getMinimumSize();
				int overlap = Math.min(tabsContentOverlapPx, tabsMin.height);
				// Allow horizontal shrinking; content can clip or reflow.
				int width = insets.left + insets.right;
				int height = Math.max(tabsMin.height, trailingMin.height) + cardsMin.height - overlap + insets.top + insets.bottom;
				return new Dimension(Math.max(0, width), Math.max(0, height));
			}
		}

		@Override
		public void layoutContainer(Container parent) {
			synchronized (parent.getTreeLock()) {
				Insets insets = parent.getInsets();
				int width = Math.max(0, parent.getWidth() - insets.left - insets.right);
				int height = Math.max(0, parent.getHeight() - insets.top - insets.bottom);

				Dimension tabsPref = tabsStrip.getPreferredSize();
				Dimension trailingPref = trailingComponent != null ? trailingComponent.getPreferredSize() : new Dimension(0, 0);
				int tabsHeight = Math.min(height, Math.max(0, tabsPref.height));
				int overlap = Math.min(tabsContentOverlapPx, tabsHeight);

				int trailingWidth = trailingComponent != null ? Math.max(0, trailingPref.width) : 0;
				int gap = trailingComponent != null && trailingWidth > 0 ? tabsTrailingGapPx : 0;
				int tabsWidth = Math.max(0, width - trailingWidth - gap);

				tabsStrip.setBounds(insets.left, insets.top, tabsWidth, tabsHeight);

				if (trailingComponent != null && trailingWidth > 0) {
					int trailingX = insets.left + width - trailingWidth;
					int trailingHeight = Math.min(tabsHeight, Math.max(0, trailingPref.height));
					int trailingY = insets.top + Math.max(0, (tabsHeight - trailingHeight) / 2);
					trailingComponent.setBounds(trailingX, trailingY, trailingWidth, trailingHeight);
				}

				int cardsY = insets.top + tabsHeight - overlap;
				int cardsHeight = Math.max(0, insets.top + height - cardsY);
				cards.setBounds(insets.left, cardsY, width, cardsHeight);
			}
		}
	}

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
			synchronized (parent.getTreeLock()) {
				int count = parent.getComponentCount();
				int height = 0;
				for (int i = 0; i < count; i++) {
					Component c = parent.getComponent(i);
					if (!c.isVisible()) {
						continue;
					}
					Dimension d = c.getMinimumSize();
					height = Math.max(height, d.height);
				}
				Insets insets = parent.getInsets();
				// Allow horizontal shrinking (tabs can clip); preserve height.
				return new Dimension(insets.left + insets.right, height + insets.top + insets.bottom);
			}
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
