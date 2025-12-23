package com.lansoftprogramming.runeSequence.ui.regionSelector;

import com.lansoftprogramming.runeSequence.infrastructure.config.AppSettings;
import com.lansoftprogramming.runeSequence.infrastructure.config.ConfigManager;
import com.lansoftprogramming.runeSequence.ui.notification.NotificationService;
import com.lansoftprogramming.runeSequence.ui.theme.ButtonStyle;
import com.lansoftprogramming.runeSequence.ui.theme.ThemedButtons;
import com.lansoftprogramming.runeSequence.ui.theme.ThemedDialogs;
import com.lansoftprogramming.runeSequence.ui.theme.UiColorPalette;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.swing.*;
import javax.swing.border.EmptyBorder;
import java.awt.*;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.net.URL;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.UUID;
import java.util.function.Supplier;

public class RegionManagerWindow extends JFrame {
	private static final Logger logger = LoggerFactory.getLogger(RegionManagerWindow.class);

	private final ConfigManager configManager;
	private final Supplier<NotificationService> notificationsSupplier;
	private final List<RegionEntry> regions;
	private final JPanel regionsListPanel;
	private final JButton addButton;
	private final JButton removeButton;
	private final ButtonGroup selectionGroup;
	private final List<RegionEntryPanel> regionPanels;

	private RegionPreviewOverlay previewOverlay;

	public RegionManagerWindow(ConfigManager configManager, Supplier<NotificationService> notificationsSupplier) {
		super("Regions");
		this.configManager = Objects.requireNonNull(configManager, "configManager");
		this.notificationsSupplier = Objects.requireNonNull(notificationsSupplier, "notificationsSupplier");
		this.regions = new ArrayList<>();
		this.selectionGroup = new ButtonGroup();
		this.regionPanels = new ArrayList<>();

		setAlwaysOnTop(true);
		setDefaultCloseOperation(JFrame.DISPOSE_ON_CLOSE);
		setLayout(new BorderLayout());

		JPanel topBar = new JPanel(new BorderLayout());
		topBar.setBorder(new EmptyBorder(12, 12, 12, 12));
		JLabel title = new JLabel("Capture Regions");
		title.setForeground(UiColorPalette.UI_TEXT_COLOR);
		topBar.add(title, BorderLayout.WEST);

		JPanel actions = new JPanel();
		actions.setLayout(new BoxLayout(actions, BoxLayout.X_AXIS));
		addButton = new JButton("+");
		ThemedButtons.apply(addButton, ButtonStyle.DEFAULT);
		addButton.setFocusable(false);
		addButton.setToolTipText("Add region");
		removeButton = createDeleteButtonOrFallback();
		ThemedButtons.apply(removeButton, ButtonStyle.DEFAULT);
		removeButton.setFocusable(false);
		removeButton.setToolTipText("Delete selected region");
		actions.add(addButton);
		actions.add(Box.createHorizontalStrut(6));
		actions.add(removeButton);
		topBar.add(actions, BorderLayout.EAST);

		add(topBar, BorderLayout.NORTH);

		regionsListPanel = new JPanel();
		regionsListPanel.setLayout(new BoxLayout(regionsListPanel, BoxLayout.Y_AXIS));
		regionsListPanel.setBorder(new EmptyBorder(12, 12, 12, 12));

		JScrollPane scrollPane = new JScrollPane(regionsListPanel);
		scrollPane.setBorder(BorderFactory.createEmptyBorder());
		add(scrollPane, BorderLayout.CENTER);

		loadFromSettings();
		rebuildRegionPanels();
		installActions();
		pack();
		setMinimumSize(new Dimension(520, 280));
		setLocationRelativeTo(null);
	}

	private JButton createDeleteButtonOrFallback() {
		ImageIcon trashIcon = null;
		try {
			URL iconUrl = getClass().getResource("/ui/trash-512.png");
			if (iconUrl != null) {
				ImageIcon originalIcon = new ImageIcon(iconUrl);
				Image image = originalIcon.getImage();
				Image scaledImage = image.getScaledInstance(16, 16, Image.SCALE_SMOOTH);
				trashIcon = new ImageIcon(scaledImage);
			}
		} catch (Exception e) {
			logger.debug("Failed to load trash icon for delete button", e);
		}

		if (trashIcon != null) {
			JButton deleteButton = new JButton(trashIcon);
			ThemedButtons.apply(deleteButton, ButtonStyle.DEFAULT);
			return deleteButton;
		}
		JButton deleteButton = new JButton("Delete");
		ThemedButtons.apply(deleteButton, ButtonStyle.DEFAULT);
		return deleteButton;
	}

	private void installActions() {
		addButton.addActionListener(e -> addRegion());
		removeButton.addActionListener(e -> removeSelectedRegion());

		addWindowListener(new java.awt.event.WindowAdapter() {
			@Override
			public void windowOpened(java.awt.event.WindowEvent e) {
				ensurePreviewOverlay();
				updatePreviewOverlay();
				if (previewOverlay != null) {
					previewOverlay.showOverlay();
				}
				bringWindowToFront();
			}

			@Override
			public void windowClosed(java.awt.event.WindowEvent e) {
				disposePreviewOverlay();
			}

			@Override
			public void windowIconified(java.awt.event.WindowEvent e) {
				if (previewOverlay != null) {
					previewOverlay.hideOverlay();
				}
			}

			@Override
			public void windowDeiconified(java.awt.event.WindowEvent e) {
				ensurePreviewOverlay();
				updatePreviewOverlay();
				if (previewOverlay != null) {
					previewOverlay.showOverlay();
				}
				bringWindowToFront();
			}
		});
	}

	private void ensurePreviewOverlay() {
		if (previewOverlay != null) {
			return;
		}
		previewOverlay = new RegionPreviewOverlay();
	}

	private void disposePreviewOverlay() {
		if (previewOverlay == null) {
			return;
		}
		previewOverlay.close();
		previewOverlay = null;
	}

	private void updatePreviewOverlay() {
		if (previewOverlay == null) {
			return;
		}
		List<RegionPreviewOverlay.RegionOverlayItem> overlayItems = new ArrayList<>();
		for (int i = 0; i < regions.size(); i++) {
			RegionEntry entry = regions.get(i);
			if (entry == null || entry.region == null || entry.region.width <= 0 || entry.region.height <= 0) {
				continue;
			}
			overlayItems.add(new RegionPreviewOverlay.RegionOverlayItem(
					i,
					entry.getDisplayName(i),
					new Rectangle(entry.region)
			));
		}
		previewOverlay.setRegions(overlayItems);
	}

	private void loadFromSettings() {
		regions.clear();
		AppSettings settings = configManager.getSettings();
		if (settings == null) {
			regions.add(RegionEntry.placeholder("Abilities", true));
			regions.add(RegionEntry.placeholder("Chat box", true));
			regions.add(RegionEntry.placeholder("Buff Bar", true));
			regions.add(RegionEntry.placeholder("Debuff Bar", true));
			regions.add(RegionEntry.placeholder("Timer", true));
			return;
		}

		List<AppSettings.RegionSettings> storedRegions = settings.getRegions();
		if (storedRegions == null || storedRegions.isEmpty()) {
			regions.add(RegionEntry.placeholder("Abilities", true));
			return;
		}

		for (AppSettings.RegionSettings regionSettings : storedRegions) {
			RegionEntry entry = RegionEntry.fromSettings(regionSettings);
			if (entry != null) {
				regions.add(entry);
			}
		}
	}

	private void rebuildRegionPanels() {
		regionsListPanel.removeAll();
		selectionGroup.clearSelection();
		regionPanels.clear();

		for (int i = 0; i < regions.size(); i++) {
			RegionEntry entry = regions.get(i);
			RegionEntryPanel panel = new RegionEntryPanel(i, entry);
			regionPanels.add(panel);
			regionsListPanel.add(panel);
			regionsListPanel.add(Box.createVerticalStrut(10));
		}

		if (!regionPanels.isEmpty()) {
			regionPanels.getFirst().select();
		}

		updatePreviewOverlay();
		updateRemoveButtonState();
		regionsListPanel.revalidate();
		regionsListPanel.repaint();
	}

	private void updateRemoveButtonState() {
		int selectedIndex = getSelectedIndex();
		boolean enabled = selectedIndex >= 0
				&& selectedIndex < regions.size()
				&& regions.get(selectedIndex).isRemovable();
		removeButton.setEnabled(enabled);
	}

	private void bringWindowToFront() {
		if (!isDisplayable()) {
			return;
		}
		SwingUtilities.invokeLater(() -> {
			if (!isDisplayable()) {
				return;
			}
			setAlwaysOnTop(true);
			toFront();
			repaint();
			requestFocus();
		});
	}

	private void addRegion() {
		String name = generateCustomRegionName();
		regions.add(RegionEntry.createCustom(name));
		rebuildRegionPanels();
		if (!regionPanels.isEmpty()) {
			regionPanels.getLast().select();
		}
	}

	private String generateCustomRegionName() {
		int counter = 1;
		while (counter < 1000) {
			String candidate = "Region " + counter;
			boolean exists = regions.stream()
					.anyMatch(entry -> entry != null && entry.hasName(candidate));
			if (!exists) {
				return candidate;
			}
			counter++;
		}
		return "Region " + UUID.randomUUID().toString().substring(0, 4);
	}

	private void removeSelectedRegion() {
		int selectedIndex = getSelectedIndex();
		if (selectedIndex < 0 || selectedIndex >= regions.size()) {
			return;
		}

		RegionEntry entry = regions.get(selectedIndex);
		NotificationService notifications = notificationsSupplier.get();

		if (!entry.isRemovable()) {
			if (notifications != null) {
				notifications.showInfo("This region cannot be deleted.");
			}
			return;
		}

		boolean confirmed = requestDeleteConfirmation(entry);
		if (!confirmed) {
			return;
		}

		if (entry.persisted) {
			try {
				deleteRegionFromSettings(entry);
			} catch (Exception ex) {
				logger.error("Failed to delete region from settings.", ex);
				if (notifications != null) {
					notifications.showError("Error deleting region: " + ex.getMessage());
				}
				return;
			}
		}

		regions.remove(selectedIndex);
		rebuildRegionPanels();
	}

	private boolean requestDeleteConfirmation(RegionEntry entry) {
		String title = "Delete Region";
		String name = entry != null ? entry.getDisplayName(regions.indexOf(entry)) : "region";
		String message = "Delete the saved region \"" + name + "\"?";

		NotificationService notifications = notificationsSupplier.get();
		if (notifications != null) {
			return notifications.showConfirmDialog(title, message);
		}

		return ThemedDialogs.showConfirmDialog(this, title, message);
	}

	private int getSelectedIndex() {
		for (RegionEntryPanel panel : regionPanels) {
			if (panel.isSelected()) {
				return panel.index;
			}
		}
		return -1;
	}

	private void setRegion(int index) {
		if (index < 0 || index >= regions.size()) {
			return;
		}

		ensurePreviewOverlay();
		if (previewOverlay != null) {
			previewOverlay.hideOverlay();
		}

		RegionSelectorWindow selectorWindow = RegionSelectorWindow.selectRegion();
		if (selectorWindow == null || !selectorWindow.isSelectionMade()) {
			if (previewOverlay != null && isShowing()) {
				previewOverlay.showOverlay();
				bringWindowToFront();
			}
			return;
		}

		Rectangle selectedRegion = selectorWindow.getSelectedRegion();
		Rectangle screenBounds = selectorWindow.getScreenBounds();
		if (selectedRegion == null || screenBounds == null) {
			if (previewOverlay != null && isShowing()) {
				previewOverlay.showOverlay();
				bringWindowToFront();
			}
			return;
		}

		RegionEntry entry = regions.get(index);
		entry.region = new Rectangle(selectedRegion);
		entry.screen = new Dimension(screenBounds.width, screenBounds.height);
		entry.updatedAt = Instant.now();

		NotificationService notifications = notificationsSupplier.get();
		try {
			saveRegionToSettings(entry);
			if (notifications != null) {
				notifications.showSuccess("Region saved successfully!");
			}
		} catch (Exception ex) {
			logger.error("Failed to save region settings.", ex);
			if (notifications != null) {
				notifications.showError("Error saving region: " + ex.getMessage());
			}
		}

		for (RegionEntryPanel panel : regionPanels) {
			if (panel.index == index) {
				panel.refresh(entry);
				break;
			}
		}

		updatePreviewOverlay();
		if (previewOverlay != null && isShowing()) {
			previewOverlay.showOverlay();
			bringWindowToFront();
		}
	}

	private void saveRegionToSettings(RegionEntry entry) throws Exception {
		AppSettings settings = configManager.getSettings();
		if (settings == null) {
			throw new IllegalStateException("Settings are not loaded. Cannot save region.");
		}
		List<AppSettings.RegionSettings> storedRegions = settings.getRegions();
		AppSettings.RegionSettings regionSettings = findRegionSettingsByKey(storedRegions, entry.key);
		if (regionSettings == null) {
			regionSettings = new AppSettings.RegionSettings();
			regionSettings.setKey(entry.key != null ? entry.key : createRegionKey());
			storedRegions.add(regionSettings);
		}

		if (entry.name != null && !entry.name.trim().isEmpty()) {
			regionSettings.setName(entry.name);
		}
		regionSettings.setLocked(entry.locked);

		Rectangle region = entry.region != null ? entry.region : new Rectangle(0, 0, 0, 0);
		regionSettings.setX(region.x);
		regionSettings.setY(region.y);
		regionSettings.setWidth(region.width);
		regionSettings.setHeight(region.height);

		Dimension screen = entry.screen != null ? entry.screen : new Dimension(0, 0);
		regionSettings.setScreenWidth(screen.width);
		regionSettings.setScreenHeight(screen.height);

		Instant timestamp = entry.updatedAt != null ? entry.updatedAt : Instant.now();
		regionSettings.setTimestamp(timestamp);

		entry.persisted = true;
		entry.key = regionSettings.getKey();
		configManager.saveSettings();
	}

	private void deleteRegionFromSettings(RegionEntry entry) throws Exception {
		AppSettings settings = configManager.getSettings();
		if (settings == null) {
			throw new IllegalStateException("Settings are not loaded. Cannot delete region.");
		}
		List<AppSettings.RegionSettings> storedRegions = settings.getRegions();
		if (storedRegions == null) {
			return;
		}
		boolean removed = storedRegions.removeIf(regionSettings ->
				regionSettings != null && Objects.equals(entry.key, regionSettings.getKey()));
		if (removed) {
			configManager.saveSettings();
		}
	}

	private AppSettings.RegionSettings findRegionSettingsByKey(List<AppSettings.RegionSettings> regions, String key) {
		if (regions == null || key == null) {
			return null;
		}
		for (AppSettings.RegionSettings regionSettings : regions) {
			if (regionSettings != null && key.equals(regionSettings.getKey())) {
				return regionSettings;
			}
		}
		return null;
	}

	private static final class RegionEntry {
		private String key;
		private String name;
		private boolean locked;
		private boolean persisted;
		private Rectangle region;
		private Dimension screen;
		private Instant updatedAt;

		private RegionEntry(String key, String name, boolean locked, boolean persisted,
		                    Rectangle region, Dimension screen, Instant updatedAt) {
			this.key = key;
			this.name = name;
			this.locked = locked;
			this.persisted = persisted;
			this.region = region != null ? new Rectangle(region) : new Rectangle(0, 0, 0, 0);
			this.screen = screen != null ? new Dimension(screen) : new Dimension(0, 0);
			this.updatedAt = updatedAt != null ? updatedAt : Instant.now();
		}

		static RegionEntry fromSettings(AppSettings.RegionSettings settings) {
			if (settings == null) {
				return null;
			}
			Rectangle rect = settings.toRectangle();
			Dimension screen = new Dimension(settings.getScreenWidth(), settings.getScreenHeight());
			return new RegionEntry(
					settings.getKey(),
					settings.getName(),
					settings.isLocked(),
					true,
					rect,
					screen,
					settings.getTimestamp()
			);
		}

		static RegionEntry createCustom(String name) {
			return new RegionEntry(
					createRegionKey(),
					name,
					false,
					false,
					new Rectangle(0, 0, 0, 0),
					new Dimension(0, 0),
					Instant.now()
			);
		}

		static RegionEntry placeholder(String name, boolean locked) {
			return new RegionEntry(
					createRegionKey(),
					name,
					locked,
					false,
					new Rectangle(0, 0, 0, 0),
					new Dimension(0, 0),
					Instant.now()
			);
		}

		String getDisplayName(int fallbackIndex) {
			if (name != null && !name.trim().isEmpty()) {
				return name;
			}
			return "Region " + (fallbackIndex + 1);
		}

		boolean hasName(String candidate) {
			if (name == null || candidate == null) {
				return false;
			}
			return name.equalsIgnoreCase(candidate);
		}

		boolean isRemovable() {
			return !locked;
		}
	}

	private final class RegionEntryPanel extends JPanel {
		private final int index;
		private final JRadioButton selectButton;
		private final JLabel nameLabel;
		private final JLabel coordsLabel;
		private final JLabel statusLabel;
		private final JButton setButton;

		private RegionEntryPanel(int index, RegionEntry entry) {
			this.index = index;
			setLayout(new BorderLayout(10, 0));
			setBorder(BorderFactory.createCompoundBorder(
					BorderFactory.createLineBorder(UiColorPalette.UI_DIVIDER_FAINT),
					new EmptyBorder(10, 10, 10, 10)
			));
			setOpaque(false);

			selectButton = new JRadioButton();
			selectButton.setOpaque(false);
			selectButton.setFocusable(false);
			selectionGroup.add(selectButton);
			selectButton.addActionListener(e -> select());

			JPanel left = new JPanel(new BorderLayout(8, 0));
			left.setOpaque(false);
			left.add(selectButton, BorderLayout.WEST);

			JPanel info = new JPanel();
			info.setOpaque(false);
			info.setLayout(new BoxLayout(info, BoxLayout.Y_AXIS));

			nameLabel = new JLabel();
			nameLabel.setForeground(UiColorPalette.UI_TEXT_COLOR);
			coordsLabel = new JLabel();
			coordsLabel.setForeground(UiColorPalette.UI_TEXT_COLOR);
			statusLabel = new JLabel();
			statusLabel.setForeground(UiColorPalette.TEXT_MUTED);

			info.add(nameLabel);
			info.add(Box.createVerticalStrut(4));
			info.add(coordsLabel);
			info.add(Box.createVerticalStrut(4));
			info.add(statusLabel);

			left.add(info, BorderLayout.CENTER);
			add(left, BorderLayout.CENTER);

			setButton = new JButton("Set");
			ThemedButtons.apply(setButton, ButtonStyle.DEFAULT);
			setButton.setFocusable(false);
			setButton.addActionListener(e -> {
				select();
				setRegion(this.index);
			});
			add(setButton, BorderLayout.EAST);

			MouseAdapter selectOnClick = new MouseAdapter() {
				@Override
				public void mouseClicked(MouseEvent e) {
					select();
				}
			};
			addMouseListener(selectOnClick);
			left.addMouseListener(selectOnClick);
			info.addMouseListener(selectOnClick);
			nameLabel.addMouseListener(selectOnClick);
			coordsLabel.addMouseListener(selectOnClick);
			statusLabel.addMouseListener(selectOnClick);

			refresh(entry);
		}

		void refresh(RegionEntry entry) {
			nameLabel.setText(entry != null ? entry.getDisplayName(index) : "Region " + (index + 1));

			Rectangle rect = entry != null && entry.region != null ? entry.region : new Rectangle(0, 0, 0, 0);
			Dimension screen = entry != null && entry.screen != null ? entry.screen : new Dimension(0, 0);
			String coords = String.format("x=%d, y=%d, w=%d, h=%d (screen %dx%d)", rect.x, rect.y, rect.width, rect.height, screen.width, screen.height);
			coordsLabel.setText(coords);

			if (entry == null) {
				statusLabel.setText("");
			} else if (!entry.persisted) {
				statusLabel.setText("Not saved yet");
			} else if (entry.locked) {
				statusLabel.setText("Default region (locked)");
			} else {
				statusLabel.setText("Saved in settings");
			}
		}

		boolean isSelected() {
			return selectButton.isSelected();
		}

		void select() {
			selectButton.setSelected(true);
			updateRemoveButtonState();
		}
	}

	private static String createRegionKey() {
		return "region-" + UUID.randomUUID();
	}
}