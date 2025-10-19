package com.lansoftprogramming.runeSequence.ui.presetManager.palette;

import com.lansoftprogramming.runeSequence.infrastructure.config.AbilityCategoryConfig;
import com.lansoftprogramming.runeSequence.infrastructure.config.AbilityConfig;
import com.lansoftprogramming.runeSequence.ui.presetManager.detail.SequenceDetailPanel;
import com.lansoftprogramming.runeSequence.ui.shared.model.AbilityItem;
import com.lansoftprogramming.runeSequence.ui.shared.service.AbilityIconLoader;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.swing.*;
import javax.swing.border.EmptyBorder;
import java.awt.*;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Collectors;

/**
 * Panel component that displays the ability palette with categorized tabs.
 * Follows the Single Responsibility Principle by handling only the palette display logic.
 */
public class AbilityPalettePanel extends JPanel {
	private static final Logger logger = LoggerFactory.getLogger(AbilityPalettePanel.class);

	private final AbilityConfig abilityConfig;
	private final AbilityCategoryConfig categoryConfig;
	private final AbilityIconLoader iconLoader;

	private JTextField searchField;
	private JTabbedPane categoryTabs;
	private SequenceDetailPanel detailPanel; // Reference to detail panel for drag coordination

	public AbilityPalettePanel(AbilityConfig abilityConfig,
	                           AbilityCategoryConfig categoryConfig,
	                           AbilityIconLoader iconLoader) {
		this.abilityConfig = Objects.requireNonNull(abilityConfig, "Ability config cannot be null");
		this.categoryConfig = Objects.requireNonNull(categoryConfig, "Category config cannot be null");
		this.iconLoader = Objects.requireNonNull(iconLoader, "Icon loader cannot be null");

		initializeComponents();
		layoutComponents();
		loadCategories();
	}

	public void setDetailPanel(SequenceDetailPanel detailPanel) {
		this.detailPanel = detailPanel;
	}

	private void initializeComponents() {
		setLayout(new BorderLayout());
		setBorder(new EmptyBorder(10, 10, 10, 10));

		searchField = new JTextField();
		searchField.setToolTipText("Search abilities...");

		categoryTabs = new JTabbedPane();
	}

	private void layoutComponents() {
		// Search bar at top
		JPanel searchPanel = new JPanel(new BorderLayout(5, 0));
		searchPanel.add(new JLabel("Search:"), BorderLayout.WEST);
		searchPanel.add(searchField, BorderLayout.CENTER);
		searchPanel.setBorder(new EmptyBorder(0, 0, 10, 0));

		add(searchPanel, BorderLayout.NORTH);
		add(categoryTabs, BorderLayout.CENTER);
	}

	/**
	 * Loads all categories and populates the tabs.
	 */
	private void loadCategories() {
		Map<String, List<String>> categories = categoryConfig.getCategories();

		if (categories == null || categories.isEmpty()) {
			logger.warn("No ability categories to display");
			return;
		}

		for (Map.Entry<String, List<String>> entry : categories.entrySet()) {
			String categoryName = entry.getKey();
			List<String> abilityKeys = entry.getValue();

			JPanel categoryPanel = createCategoryPanel(abilityKeys);
			JScrollPane scrollPane = new JScrollPane(categoryPanel);
			// Set minimal insets to fit the content tightly with no extra margins
			scrollPane.setBorder(BorderFactory.createEmptyBorder());
			scrollPane.setViewportBorder(BorderFactory.createEmptyBorder());
			scrollPane.setVerticalScrollBarPolicy(JScrollPane.VERTICAL_SCROLLBAR_ALWAYS);
			scrollPane.getVerticalScrollBar().setUnitIncrement(16);

			categoryTabs.addTab(categoryName, scrollPane);
		}

		logger.info("Loaded {} ability categories", categories.size());
	}

	/**
	 * Creates a panel for a specific category with its abilities.
	 */
	private JPanel createCategoryPanel(List<String> abilityKeys) {
		// Convert keys to AbilityItems and sort by level
		List<AbilityItem> abilityItems = abilityKeys.stream()
				.map(this::createAbilityItem)
				.filter(Objects::nonNull)
				.sorted(Comparator.comparingInt(AbilityItem::getLevel))
				.collect(Collectors.toList());

	    JPanel categoryPanel = new JPanel() {
	        @Override
	        public Dimension getPreferredSize() {
	            // Override to respect viewport width for proper wrapping
	            Dimension d = super.getPreferredSize();
	            if (getParent() instanceof JViewport) {
	                d.width = getParent().getWidth();
	            }
	            return d;
	        }
	    };
	    categoryPanel.setBorder(new EmptyBorder(10, 10, 10, 10));

	    // Use FlowLayout to wrap cards naturally with minimal gaps
	    FlowLayout flowLayout = new FlowLayout(FlowLayout.LEFT, 5, 5);
	    categoryPanel.setLayout(flowLayout);

	    // Add ability cards to the panel
	    for (AbilityItem item : abilityItems) {
	        JPanel abilityCard = createAbilityCard(item);
	        categoryPanel.add(abilityCard);
	    }

		return categoryPanel;
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
	 * Gets the display name for an ability, falling back to the key if needed.
	 */
	private String getDisplayName(AbilityConfig.AbilityData abilityData, String fallbackKey) {
		String commonName = abilityData.getCommonName();
		if (commonName != null && !commonName.isEmpty()) {
			return commonName;
		}
		return fallbackKey;
	}

	/**
	 * Creates a compact visual card for an ability item.
	 */
	private JPanel createAbilityCard(AbilityItem item) {
	    // Panel representing a single ability (icon + label tightly packed)
	    JPanel card = new JPanel();
	    card.setLayout(new BoxLayout(card, BoxLayout.Y_AXIS));
	    card.setBackground(Color.WHITE);
	    card.setBorder(BorderFactory.createLineBorder(Color.LIGHT_GRAY));

	    // Compact dimensions: 50x50 icon + ~18px for label
	    card.setMinimumSize(new Dimension(50, 68));
	    card.setPreferredSize(new Dimension(50, 68));
	    card.setMaximumSize(new Dimension(50, 68));

	    // Icon (50x50 centered in card)
	    JLabel iconLabel = new JLabel(item.getIcon());
	    iconLabel.setAlignmentX(Component.CENTER_ALIGNMENT);
	    iconLabel.setMinimumSize(new Dimension(50, 50));
	    iconLabel.setPreferredSize(new Dimension(50, 50));
	    iconLabel.setMaximumSize(new Dimension(50, 50));

	    // Name label directly under the icon
	    String displayText = truncateText(item.getDisplayName(), 12);
	    JLabel nameLabel = new JLabel(displayText);
	    nameLabel.setFont(nameLabel.getFont().deriveFont(9f));
	    nameLabel.setAlignmentX(Component.CENTER_ALIGNMENT);
	    nameLabel.setMinimumSize(new Dimension(50, 16));
	    nameLabel.setPreferredSize(new Dimension(50, 16));
	    nameLabel.setMaximumSize(new Dimension(50, 16));

	    // Add components with minimal gap
	    card.add(iconLabel);
	    card.add(Box.createRigidArea(new Dimension(0, 2)));
	    card.add(nameLabel);

	    // Tooltip with ability details
	    card.setToolTipText(createTooltipText(item));

	    // Enable dragging from palette
	    card.addMouseListener(new MouseAdapter() {
	    	@Override
	    	public void mousePressed(MouseEvent e) {
	    		logger.info("Palette card pressed: {}", item.getKey());
	    		if (detailPanel != null) {
	    			detailPanel.startPaletteDrag(item, card, e.getPoint());
	    		}
	    	}
	    });

		return card;
	}

	/**
	 * Truncates text to the specified length, adding ellipsis if needed.
	 */
	private String truncateText(String text, int maxLength) {
		if (text.length() <= maxLength) {
			return text;
		}
		return text.substring(0, maxLength - 3) + "...";
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
	 * Gets the search field for external access.
	 */
	public JTextField getSearchField() {
		return searchField;
	}

	/**
	 * Gets the category tabs for external access.
	 */
	public JTabbedPane getCategoryTabs() {
		return categoryTabs;
	}
}