package com.lansoftprogramming.runeSequence.ui.presetManager.palette;

import com.lansoftprogramming.runeSequence.infrastructure.config.AbilityCategoryConfig;
import com.lansoftprogramming.runeSequence.infrastructure.config.AbilityConfig;
import com.lansoftprogramming.runeSequence.ui.presetManager.detail.SequenceDetailPanel;
import com.lansoftprogramming.runeSequence.ui.shared.component.HoverGlowContainerPanel;
import com.lansoftprogramming.runeSequence.ui.shared.component.WrapLayout;
import com.lansoftprogramming.runeSequence.ui.shared.model.AbilityItem;
import com.lansoftprogramming.runeSequence.ui.shared.service.AbilityIconLoader;
import com.lansoftprogramming.runeSequence.ui.taskbar.MenuAction;
import com.lansoftprogramming.runeSequence.ui.theme.UiColorPalette;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.swing.*;
import javax.swing.border.Border;
import javax.swing.border.EmptyBorder;
import javax.swing.event.DocumentEvent;
import javax.swing.event.DocumentListener;
import javax.swing.text.JTextComponent;
import java.awt.*;
import java.awt.event.AWTEventListener;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.awt.event.MouseMotionAdapter;
import java.util.*;
import java.util.List;
import java.util.stream.Collectors;

/**
 * Panel component that displays the ability palette with categorized tabs.
 * Includes fuzzy search with real-time filtering and visual feedback.
 */
public class AbilityPalettePanel extends JPanel {
	private static final String ICON_COGWHEEL_DARK = "/ui/dark/PresetManagerWindow.cogWheel.png";

	private static final Logger logger = LoggerFactory.getLogger(AbilityPalettePanel.class);
	private static final float DIM_OPACITY = 0.3f;
	private static final Color DIMMED_CARD_BACKGROUND = UiColorPalette.UI_CARD_BACKGROUND.darker().darker();

	private final AbilityConfig abilityConfig;
	private final AbilityCategoryConfig categoryConfig;
	private final AbilityIconLoader iconLoader;
	private final FuzzySearchService searchService;

	private JTextField searchField;
	private JButton clearSearchButton;
	private JTabbedPane categoryTabs;
	private JPanel searchInputPanel;
	private SequenceDetailPanel detailPanel;
	private JButton settingsButton;
	private JButton selectRegionButton;
	private MenuAction settingsAction;
	private MenuAction regionSelectorAction;
	private transient AWTEventListener textCursorListener;
	private transient Boolean lastCursorOverText;
	private transient long lastCursorLogAtMs;
	private transient Window cursorWindow;

	// Cache of category panels and their ability cards
	private final Map<String, CategoryPanel> categoryPanels;
	private final Map<String, List<AbilityItem>> categoryAbilities;

	public AbilityPalettePanel(AbilityConfig abilityConfig,
	                           AbilityCategoryConfig categoryConfig,
	                           AbilityIconLoader iconLoader) {
		this.abilityConfig = Objects.requireNonNull(abilityConfig, "Ability config cannot be null");
		this.categoryConfig = Objects.requireNonNull(categoryConfig, "Category config cannot be null");
		this.iconLoader = Objects.requireNonNull(iconLoader, "Icon loader cannot be null");
		this.searchService = new FuzzySearchService();
		this.categoryPanels = new LinkedHashMap<>();
		this.categoryAbilities = new LinkedHashMap<>();

		initializeComponents();
		layoutComponents();
		loadCategories();
		attachSearchListener();
	}

	public void setDetailPanel(SequenceDetailPanel detailPanel) {
		this.detailPanel = detailPanel;
	}

	public void setMainAppActions(MenuAction settingsAction, MenuAction regionSelectorAction) {
		this.settingsAction = settingsAction;
		this.regionSelectorAction = regionSelectorAction;

		if (settingsButton != null) {
			settingsButton.setEnabled(settingsAction != null);
		}
		if (selectRegionButton != null) {
			selectRegionButton.setEnabled(regionSelectorAction != null);
		}
	}

	private void initializeComponents() {
		setLayout(new BorderLayout());
		setBorder(new EmptyBorder(10, 10, 10, 10));

		searchField = new JTextField();
		installTextCursor(searchField);
		searchField.setToolTipText("Search abilities (fuzzy matching supported)...");

		clearSearchButton = new JButton("\u00D7");
		clearSearchButton.setToolTipText("Clear search");
		clearSearchButton.setFocusable(false);
		clearSearchButton.setBorderPainted(false);
		clearSearchButton.setContentAreaFilled(false);
		clearSearchButton.setOpaque(false);
		clearSearchButton.setFont(UiColorPalette.boldSans(16));
		clearSearchButton.setMargin(new Insets(0, 10, 0, 10));
		clearSearchButton.setForeground(UiColorPalette.UI_TEXT_COLOR);
		int searchFieldHeight = searchField.getPreferredSize().height;
		clearSearchButton.setPreferredSize(new Dimension(34, searchFieldHeight));
		clearSearchButton.setMinimumSize(new Dimension(34, searchFieldHeight));
		clearSearchButton.setVisible(false);
		clearSearchButton.addActionListener(e -> {
			searchField.setText("");
			searchField.requestFocusInWindow();
		});

		categoryTabs = new JTabbedPane();

		ImageIcon settingsIcon = loadScaledIconOrNull(ICON_COGWHEEL_DARK, 16, 16);
		settingsButton = createMainAppButton(settingsIcon, "Settings", "Open main app settings", () -> {
			if (settingsAction != null) {
				settingsAction.execute();
			}
		});
		selectRegionButton = createMainAppButton("Region", "Select capture region", () -> {
			if (regionSelectorAction != null) {
				regionSelectorAction.execute();
			}
		});

		settingsButton.setEnabled(false);
		selectRegionButton.setEnabled(false);
	}

	private void layoutComponents() {
		JPanel searchPanel = new JPanel(new BorderLayout(8, 0));
		searchPanel.add(new JLabel("Search:"), BorderLayout.WEST);
		searchInputPanel = new JPanel(new BorderLayout(0, 0));
		installTextCursor(searchInputPanel);
		Border searchFieldBorder = searchField.getBorder();
		if (searchFieldBorder != null) {
			searchInputPanel.setBorder(searchFieldBorder);
			searchField.setBorder(BorderFactory.createEmptyBorder(0, 4, 0, 0));
		}
		searchInputPanel.setBackground(searchField.getBackground());
		searchInputPanel.add(searchField, BorderLayout.CENTER);
		searchInputPanel.add(clearSearchButton, BorderLayout.EAST);
		searchPanel.add(searchInputPanel, BorderLayout.CENTER);
		searchPanel.add(createMainAppSettingsPanel(), BorderLayout.EAST);
		searchPanel.setBorder(new EmptyBorder(0, 0, 10, 0));

		add(searchPanel, BorderLayout.NORTH);
		add(categoryTabs, BorderLayout.CENTER);
	}

	@Override
	public void addNotify() {
		super.addNotify();
		// Ensure look-and-feel/UI updates don't override the text cursor.
		installTextCursor(searchField);
		installTextCursor(searchInputPanel);
		installTextCursorResolver();
	}

	@Override
	public void removeNotify() {
		uninstallTextCursorResolver();
		super.removeNotify();
	}

	private static void installTextCursor(JComponent component) {
		if (component == null) {
			return;
		}
		Cursor textCursor = Cursor.getPredefinedCursor(Cursor.TEXT_CURSOR);
		component.setCursor(textCursor);
		component.addMouseListener(new MouseAdapter() {
			@Override
			public void mouseEntered(MouseEvent e) {
				component.setCursor(textCursor);
			}
		});
		component.addMouseMotionListener(new MouseMotionAdapter() {
			@Override
			public void mouseMoved(MouseEvent e) {
				component.setCursor(textCursor);
			}
		});
	}

	private void installTextCursorResolver() {
		if (textCursorListener != null) {
			return;
		}
		Cursor textCursor = Cursor.getPredefinedCursor(Cursor.TEXT_CURSOR);
		Cursor defaultCursor = Cursor.getDefaultCursor();
		cursorWindow = SwingUtilities.getWindowAncestor(this);

		textCursorListener = event -> {
			if (!(event instanceof MouseEvent mouseEvent)) {
				return;
			}
			int id = mouseEvent.getID();
			if (id != MouseEvent.MOUSE_MOVED && id != MouseEvent.MOUSE_DRAGGED && id != MouseEvent.MOUSE_EXITED) {
				return;
			}
			Object src = mouseEvent.getSource();
			if (!(src instanceof Component sourceComponent)) {
				return;
			}
			if (!SwingUtilities.isDescendingFrom(sourceComponent, this)) {
				return;
			}

			Point panelPoint = SwingUtilities.convertPoint(sourceComponent, mouseEvent.getPoint(), this);
			Component deepest = SwingUtilities.getDeepestComponentAt(this, panelPoint.x, panelPoint.y);
			boolean overText = deepest instanceof JTextComponent;
			// Set the cursor at the Window level; this wins even if another container overlays the field.
			Window owner = cursorWindow != null ? cursorWindow : SwingUtilities.getWindowAncestor(this);
			if (owner != null) {
				owner.setCursor(overText ? textCursor : defaultCursor);
			} else {
				setCursor(overText ? textCursor : defaultCursor);
			}

			// Debug logging: emit on state changes (or at most ~2x/sec while inside the panel).
			long now = System.currentTimeMillis();
			boolean shouldLog = lastCursorOverText == null
					|| lastCursorOverText != overText
					|| (now - lastCursorLogAtMs) > 500;
			if (shouldLog && logger.isDebugEnabled()) {
				lastCursorOverText = overText;
				lastCursorLogAtMs = now;
				String srcName = sourceComponent.getName();
				String deepestName = deepest != null ? deepest.getName() : null;
				String deepestClass = deepest != null ? deepest.getClass().getName() : "null";
				Cursor deepestCursor = deepest != null ? deepest.getCursor() : null;
				boolean deepestEnabled = deepest == null || deepest.isEnabled();
				boolean deepestEditable = !(deepest instanceof JTextComponent tc) || tc.isEditable();
				Cursor ownerCursor = owner != null ? owner.getCursor() : null;
				logger.debug(
						"Cursor debug: overText={}, eventId={}, src={}, srcName={}, panelPoint=({},{}), deepest={}, deepestName={}, deepestEnabled={}, deepestEditable={}, deepestCursor={}, owner={}, ownerCursor={}",
						overText,
						id,
						sourceComponent.getClass().getName(),
						srcName,
						panelPoint.x,
						panelPoint.y,
						deepestClass,
						deepestName,
						deepestEnabled,
						deepestEditable,
						deepestCursor,
						owner != null ? owner.getClass().getName() : "null",
						ownerCursor
				);
			}
		};

		Toolkit.getDefaultToolkit().addAWTEventListener(
				textCursorListener,
				AWTEvent.MOUSE_MOTION_EVENT_MASK
		);
	}

	private void uninstallTextCursorResolver() {
		if (textCursorListener == null) {
			return;
		}
		Toolkit.getDefaultToolkit().removeAWTEventListener(textCursorListener);
		textCursorListener = null;
		lastCursorOverText = null;
		lastCursorLogAtMs = 0L;
		cursorWindow = null;
	}

	private JComponent createMainAppSettingsPanel() {
		JPanel panel = new JPanel();
		panel.setLayout(new BoxLayout(panel, BoxLayout.X_AXIS));

		panel.add(Box.createHorizontalStrut(10));
		panel.add(createVerticalSeparator(searchField));
		panel.add(Box.createHorizontalStrut(10));
		panel.add(selectRegionButton);
		panel.add(Box.createHorizontalStrut(6));
		panel.add(settingsButton);

		return panel;
	}

	private static JComponent createVerticalSeparator(JComponent heightReference) {
		JSeparator separator = new JSeparator(SwingConstants.VERTICAL);
		int height = Math.max(18, heightReference.getPreferredSize().height);
		separator.setPreferredSize(new Dimension(1, height));
		separator.setForeground(UiColorPalette.UI_DIVIDER_FAINT);
		return separator;
	}

	private static JButton createMainAppButton(String label, String tooltip, Runnable action) {
		JButton button = new JButton(label);
		button.setToolTipText(tooltip);
		button.setFocusable(false);
		button.setMargin(new Insets(2, 10, 2, 10));
		button.addActionListener(e -> action.run());
		return button;
	}

	private static JButton createMainAppButton(ImageIcon icon, String fallbackLabel, String tooltip, Runnable action) {
		JButton button = icon != null ? new JButton(icon) : new JButton(fallbackLabel);
		button.setToolTipText(tooltip);
		button.setFocusable(false);
		if (icon != null) {
			button.setMargin(new Insets(2, 6, 2, 6));
		} else {
			button.setMargin(new Insets(2, 10, 2, 10));
		}
		button.addActionListener(e -> action.run());
		return button;
	}

	private ImageIcon loadScaledIconOrNull(String resourcePath, int width, int height) {
		try {
			java.net.URL iconUrl = getClass().getResource(resourcePath);
			if (iconUrl == null) {
				return null;
			}
			ImageIcon originalIcon = new ImageIcon(iconUrl);
			Image scaledImage = originalIcon.getImage().getScaledInstance(width, height, Image.SCALE_SMOOTH);
			return new ImageIcon(scaledImage);
		} catch (Exception e) {
			return null;
		}
	}

	/**
	 * Attaches real-time search listener to search field.
	 */
	private void attachSearchListener() {
		searchField.getDocument().addDocumentListener(new DocumentListener() {
			@Override
			public void insertUpdate(DocumentEvent e) {
				performSearch();
			}

			@Override
			public void removeUpdate(DocumentEvent e) {
				performSearch();
			}

			@Override
			public void changedUpdate(DocumentEvent e) {
				performSearch();
			}
		});
	}

	/**
	 * Performs fuzzy search and updates visual feedback.
	 */
	private void performSearch() {
		String query = searchField.getText();
		String trimmedQuery = query != null ? query.trim() : "";
		boolean hasQuery = !trimmedQuery.isEmpty();

		clearSearchButton.setVisible(hasQuery);

		if (!hasQuery) {
			resetSearchUi();
			return;
		}

		// Build search results for each category
		Map<String, CategorySearchState> searchStates = new LinkedHashMap<>();

		for (Map.Entry<String, List<AbilityItem>> entry : categoryAbilities.entrySet()) {
			String categoryName = entry.getKey();
			List<AbilityItem> abilities = entry.getValue();

			List<SearchResult> results = abilities.stream()
					.map(ability -> {
						int score = searchService.calculateMatchScore(trimmedQuery, ability.getDisplayName());
						return new SearchResult(ability, score);
					})
					.collect(Collectors.toList());

			searchStates.put(categoryName, new CategorySearchState(categoryName, results));
		}

		// Update visual feedback
		updateTabTitles(searchStates);
		selectClosestTabWithMatches(searchStates);
		updateAbilityCards(searchStates);
	}

	private void selectClosestTabWithMatches(Map<String, CategorySearchState> searchStates) {
		int selectedIndex = categoryTabs.getSelectedIndex();
		if (selectedIndex < 0) {
			return;
		}

		List<CategorySearchState> states = new ArrayList<>(searchStates.values());
		if (selectedIndex >= states.size()) {
			return;
		}
		if (states.get(selectedIndex).hasMatches()) {
			return;
		}

		int bestIndex = -1;
		int bestDistance = Integer.MAX_VALUE;
		for (int i = 0; i < states.size(); i++) {
			if (!states.get(i).hasMatches()) {
				continue;
			}
			int distance = Math.abs(i - selectedIndex);
			if (distance < bestDistance || (distance == bestDistance && i < bestIndex)) {
				bestIndex = i;
				bestDistance = distance;
			}
		}

		if (bestIndex >= 0 && bestIndex < categoryTabs.getTabCount()) {
			categoryTabs.setSelectedIndex(bestIndex);
		}
	}

	private void resetSearchUi() {
		int tabIndex = 0;
		for (String categoryName : categoryAbilities.keySet()) {
			if (tabIndex < categoryTabs.getTabCount()) {
				categoryTabs.setTitleAt(tabIndex, categoryName);
				categoryTabs.setEnabledAt(tabIndex, true);
			}
			tabIndex++;
		}

		for (Map.Entry<String, CategoryPanel> entry : categoryPanels.entrySet()) {
			CategoryPanel panel = entry.getValue();
			if (panel != null) {
				panel.clearSearch();
			}
		}
	}

	/**
	 * Updates tab titles with match counts.
	 */
	private void updateTabTitles(Map<String, CategorySearchState> searchStates) {
		int tabIndex = 0;
		for (Map.Entry<String, CategorySearchState> entry : searchStates.entrySet()) {
			String categoryName = entry.getKey();
			CategorySearchState state = entry.getValue();

			String tabTitle = state.getFormattedTabTitle();
			categoryTabs.setTitleAt(tabIndex, tabTitle);

			// Dim tab if no matches
			categoryTabs.setEnabledAt(tabIndex, state.hasMatches());

			tabIndex++;
		}
	}

	/**
	 * Updates ability card visual feedback based on search results.
	 */
	private void updateAbilityCards(Map<String, CategorySearchState> searchStates) {
		for (Map.Entry<String, CategorySearchState> entry : searchStates.entrySet()) {
			String categoryName = entry.getKey();
			CategorySearchState state = entry.getValue();
			CategoryPanel panel = categoryPanels.get(categoryName);

			if (panel != null) {
				panel.updateSearchResults(state.getResults());
			}
		}
	}

	/**
	 * Loads all categories and populates the tabs.
	 */
	private void loadCategories() {
		Map<String, List<String>> categories = categoryConfig.getCategories();
		Map<String, List<String>> categoriesWithFallback = new LinkedHashMap<>();

		if (categories != null) {
			categoriesWithFallback.putAll(categories);
		}

		// Build a default "Items" category from any abilities not already assigned to a category
		Set<String> allAbilityKeys = abilityConfig.getAbilities() != null
				? new LinkedHashSet<>(abilityConfig.getAbilities().keySet())
				: Collections.emptySet();

		Set<String> categorizedKeys = categoriesWithFallback.values().stream()
				.filter(Objects::nonNull)
				.flatMap(List::stream)
				.collect(Collectors.toCollection(LinkedHashSet::new));

		List<String> uncategorized = allAbilityKeys.stream()
				.filter(key -> !categorizedKeys.contains(key))
				.collect(Collectors.toList());

		if (!uncategorized.isEmpty()) {
			categoriesWithFallback
					.computeIfAbsent("Items", k -> new ArrayList<>())
					.addAll(uncategorized);
			logger.info("Added {} uncategorized abilities to default 'Items' category", uncategorized.size());
		} else if (categoriesWithFallback.isEmpty()) {
			logger.warn("No ability categories to display");
			return;
		}

		for (Map.Entry<String, List<String>> entry : categoriesWithFallback.entrySet()) {
			String categoryName = entry.getKey();
			List<String> abilityKeys = entry.getValue();

			// Create and cache ability items
			List<AbilityItem> abilityItems = abilityKeys.stream()
					.filter(Objects::nonNull)
					.distinct()
					.map(this::createAbilityItem)
					.filter(Objects::nonNull)
					.sorted(Comparator.comparingInt(AbilityItem::getLevel))
					.collect(Collectors.toList());

			categoryAbilities.put(categoryName, abilityItems);

			// Create category panel
			CategoryPanel categoryPanel = new CategoryPanel(abilityItems);
			categoryPanels.put(categoryName, categoryPanel);

			JScrollPane scrollPane = new JScrollPane(categoryPanel);
			scrollPane.setBorder(BorderFactory.createEmptyBorder());
			scrollPane.setViewportBorder(BorderFactory.createEmptyBorder());
			scrollPane.setVerticalScrollBarPolicy(JScrollPane.VERTICAL_SCROLLBAR_ALWAYS);
			scrollPane.getVerticalScrollBar().setUnitIncrement(5);

			categoryTabs.addTab(categoryName, scrollPane);
		}

		logger.info("Loaded {} ability categories", categoriesWithFallback.size());
	}

	/**
	 * Creates an AbilityItem from an ability key.
	 */
	private AbilityItem createAbilityItem(String abilityKey) {
		try {
			AbilityConfig.AbilityData abilityData = abilityConfig.getAbility(abilityKey);

			if (abilityData == null) {
				logger.debug("Ability data not found for key '{}'", abilityKey);
				ImageIcon icon = iconLoader.loadIcon(abilityKey);
				return new AbilityItem(abilityKey, abilityKey, 0, "Unknown", icon);
			}

			String displayName = getDisplayName(abilityData, abilityKey);
			int level = abilityData.getLevel() != null ? abilityData.getLevel() : 0;
			String type = abilityData.getType() != null ? abilityData.getType() : "Unknown";
			ImageIcon icon = iconLoader.loadIcon(abilityKey);

			return new AbilityItem(abilityKey, displayName, level, type, icon);

		} catch (Exception e) {
			logger.warn("Failed to create ability item for key: {}", abilityKey, e);
			return null;
		}
	}

	/**
	 * Gets the display name for an ability.
	 */
	private String getDisplayName(AbilityConfig.AbilityData abilityData, String fallbackKey) {
		String commonName = abilityData.getCommonName();
		if (commonName != null && !commonName.isEmpty()) {
			return commonName;
		}
		return fallbackKey;
	}

	/**
	 * Creates tooltip text for an ability item.
	 */
	private String createTooltipText(AbilityItem item) {
		return String.format("<html><b>%s</b><br/>Type: %s<br/>Level: %d</html>",
				item.getDisplayName(),
				item.getType(),
				item.getLevel());
	}

	/**
	 * Truncates text to the specified length.
	 */
	private String truncateText(String text, int maxLength) {
		if (text.length() <= maxLength) {
			return text;
		}
		return text.substring(0, maxLength - 3) + "...";
	}

	public JTextField getSearchField() {
		return searchField;
	}

	public JTabbedPane getCategoryTabs() {
		return categoryTabs;
	}

	/**
	 * Inner class representing a category panel with searchable ability cards.
	 */
	private class CategoryPanel extends HoverGlowContainerPanel {
		private final Map<AbilityItem, AbilityCard> cardMap;

		public CategoryPanel(List<AbilityItem> abilities) {
			super(component -> component instanceof AbilityCard card && !card.isDimmed());
			this.cardMap = new LinkedHashMap<>();

			setBorder(new EmptyBorder(10, 10, 10, 10));
			setLayout(new WrapLayout(FlowLayout.LEFT, 5, 5));

			// Create ability cards
			for (AbilityItem item : abilities) {
				AbilityCard card = new AbilityCard(item);
				cardMap.put(item, card);
				add(card);
			}
		}

		@Override
		public Dimension getPreferredSize() {
			Dimension d = super.getPreferredSize();
			if (getParent() instanceof JViewport) {
				d.width = getParent().getWidth();
			}
			return d;
		}

		/**
		 * Updates visual feedback for all cards based on search results.
		 */
		public void updateSearchResults(List<SearchResult> results) {
			for (SearchResult result : results) {
				AbilityCard card = cardMap.get(result.getAbility());
				if (card != null) {
					card.setDimmed(!result.matches());
				}
			}
		}

		public void clearSearch() {
			for (AbilityCard card : cardMap.values()) {
				card.setDimmed(false);
			}
		}
	}

	/**
	 * Inner class representing an individual ability card.
	 */
	private class AbilityCard extends JPanel {
		private final AbilityItem item;
		private final JLabel iconLabel;
		private final JLabel nameLabel;
		private boolean dimmed;

		public AbilityCard(AbilityItem item) {
			this.item = item;
			this.dimmed = false;

			setName("abilityCard");
			setLayout(new BoxLayout(this, BoxLayout.Y_AXIS));
			setBackground(UiColorPalette.UI_CARD_BACKGROUND);
			setBorder(UiColorPalette.CARD_BORDER);
			setMinimumSize(new Dimension(50, 68));
			setPreferredSize(new Dimension(50, 68));
			setMaximumSize(new Dimension(50, 68));

			// Icon
			iconLabel = new JLabel(item.getIcon());
			iconLabel.setAlignmentX(Component.CENTER_ALIGNMENT);
			iconLabel.setMinimumSize(new Dimension(50, 50));
			iconLabel.setPreferredSize(new Dimension(50, 50));
			iconLabel.setMaximumSize(new Dimension(50, 50));

			// Name
			String displayText = truncateText(item.getDisplayName(), 12);
			nameLabel = new JLabel(displayText);
			nameLabel.setFont(nameLabel.getFont().deriveFont(9f));
			nameLabel.setAlignmentX(Component.CENTER_ALIGNMENT);
			nameLabel.setMinimumSize(new Dimension(50, 16));
			nameLabel.setPreferredSize(new Dimension(50, 16));
			nameLabel.setMaximumSize(new Dimension(50, 16));
			nameLabel.setForeground(UiColorPalette.UI_TEXT_COLOR);

			add(iconLabel);
			add(Box.createRigidArea(new Dimension(0, 2)));
			add(nameLabel);

			setToolTipText(createTooltipText(item));

			// Drag support
			addMouseListener(new MouseAdapter() {
				@Override
				public void mousePressed(MouseEvent e) {
					// Only start drags from the palette with a physical left-button press.
					if (!dimmed && e.getButton() == MouseEvent.BUTTON1) {
						logger.info("Palette card pressed: {}", item.getKey());
						if (detailPanel != null) {
							detailPanel.startPaletteDrag(item, AbilityCard.this, e.getPoint());
						}
					}
				}
			});
		}

		public boolean isDimmed() {
			return dimmed;
		}

		/**
		 * Sets dimmed state and updates visual appearance.
		 */
		public void setDimmed(boolean dimmed) {
			if (this.dimmed != dimmed) {
				this.dimmed = dimmed;
				updateAppearance();
			}
		}

		/**
		 * Updates visual appearance based on dimmed state.
		 */
		private void updateAppearance() {
			if (dimmed) {
				setBackground(DIMMED_CARD_BACKGROUND);
				iconLabel.setEnabled(false);
				nameLabel.setEnabled(false);
				// Apply transparency to icon
				ImageIcon originalIcon = item.getIcon();
				if (originalIcon != null) {
					Image dimmedImage = createDimmedImage(originalIcon.getImage());
					iconLabel.setIcon(new ImageIcon(dimmedImage));
				}
			} else {
				setBackground(UiColorPalette.UI_CARD_BACKGROUND);
				iconLabel.setEnabled(true);
				nameLabel.setEnabled(true);
				iconLabel.setIcon(item.getIcon());
			}
			repaint();
		}

		/**
		 * Creates a dimmed version of an image.
		 */
		private Image createDimmedImage(Image original) {
			int width = original.getWidth(null);
			int height = original.getHeight(null);

			if (width <= 0 || height <= 0) {
				return original;
			}

			java.awt.image.BufferedImage dimmed = new java.awt.image.BufferedImage(
					width, height, java.awt.image.BufferedImage.TYPE_INT_ARGB);

			Graphics2D g2d = dimmed.createGraphics();
			g2d.setComposite(AlphaComposite.getInstance(AlphaComposite.SRC_OVER, DIM_OPACITY));
			g2d.drawImage(original, 0, 0, null);
			g2d.dispose();

			return dimmed;
		}
	}
}
