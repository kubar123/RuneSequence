package com.lansoftprogramming.runeSequence.ui.regionSelector;

import com.lansoftprogramming.runeSequence.infrastructure.config.AppSettings;
import com.lansoftprogramming.runeSequence.infrastructure.config.ConfigManager;
import com.lansoftprogramming.runeSequence.ui.notification.NotificationService;
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
		addButton.setFocusable(false);
		addButton.setToolTipText("Add region");
		removeButton = createDeleteButtonOrFallback();
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
			return new JButton(trashIcon);
		}
		return new JButton("Delete");
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
				toFront();
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
				toFront();
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
					entry.name != null ? entry.name : ("Region " + (i + 1)),
					new Rectangle(entry.region)
			));
		}
		previewOverlay.setRegions(overlayItems);
	}

	private void loadFromSettings() {
		regions.clear();
		AppSettings settings = configManager.getSettings();
		if (settings == null) {
			regions.add(RegionEntry.persisted("Region 1", new Rectangle(0, 0, 0, 0), new Dimension(0, 0)));
			return;
		}
		AppSettings.RegionSettings regionSettings = settings.getRegion();
		Rectangle rect = regionSettings != null ? regionSettings.toRectangle() : new Rectangle(0, 0, 0, 0);
		Dimension screen = regionSettings != null ? new Dimension(regionSettings.getScreenWidth(), regionSettings.getScreenHeight()) : new Dimension(0, 0);
		regions.add(RegionEntry.persisted("Region 1", rect, screen));
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
		regionsListPanel.revalidate();
		regionsListPanel.repaint();
	}

	private void addRegion() {
		int index = regions.size() + 1;
		regions.add(RegionEntry.transientRegion("Region " + index, new Rectangle(0, 0, 0, 0), new Dimension(0, 0)));
		rebuildRegionPanels();
		if (!regionPanels.isEmpty()) {
			regionPanels.getLast().select();
		}
	}

	private void removeSelectedRegion() {
		int selectedIndex = getSelectedIndex();
		if (selectedIndex < 0 || selectedIndex >= regions.size()) {
			return;
		}

		RegionEntry entry = regions.get(selectedIndex);
		NotificationService notifications = notificationsSupplier.get();

		boolean confirmed = requestDeleteConfirmation(regions.size(), entry);
		if (!confirmed) {
			return;
		}

		if (regions.size() == 1 && entry.persisted) {
			clearRegionAt(selectedIndex, true);
		} else if (entry.persisted) {
			clearRegionAt(selectedIndex, true);
			regions.remove(selectedIndex);
		} else {
			regions.remove(selectedIndex);
		}

		rebuildRegionPanels();
	}

	private void clearRegionAt(int index, boolean persistClear) {
		RegionEntry entry = regions.get(index);
		if (entry == null) {
			return;
		}
		entry.region = new Rectangle(0, 0, 0, 0);
		entry.screen = new Dimension(0, 0);
		entry.updatedAt = Instant.now();
		if (persistClear) {
			try {
				saveRegionToSettings(entry);
			} catch (Exception ex) {
				logger.error("Failed to clear region settings.", ex);
				NotificationService notifications = notificationsSupplier.get();
				if (notifications != null) {
					notifications.showError("Error clearing region: " + ex.getMessage());
				}
			}
		}
	}

	private boolean requestDeleteConfirmation(int regionCount, RegionEntry entry) {
		String title;
		String message;
		if (regionCount == 1 && entry.persisted) {
			title = "Clear Region";
			message = "Clear the saved region coordinates?";
		} else {
			title = "Delete Region";
			message = "Delete the saved region?";
		}

		NotificationService notifications = notificationsSupplier.get();
		if (notifications != null) {
			return notifications.showConfirmDialog(title, message);
		}

		return JOptionPane.showConfirmDialog(
				this,
				message,
				title,
				JOptionPane.YES_NO_OPTION,
				JOptionPane.QUESTION_MESSAGE
		) == JOptionPane.YES_OPTION;
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
			}
			return;
		}

		Rectangle selectedRegion = selectorWindow.getSelectedRegion();
		Rectangle screenBounds = selectorWindow.getScreenBounds();
		if (selectedRegion == null || screenBounds == null) {
			if (previewOverlay != null && isShowing()) {
				previewOverlay.showOverlay();
			}
			return;
		}

		RegionEntry entry = regions.get(index);
		entry.region = new Rectangle(selectedRegion);
		entry.screen = new Dimension(screenBounds.width, screenBounds.height);
		entry.updatedAt = Instant.now();

		NotificationService notifications = notificationsSupplier.get();
		if (entry.persisted) {
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
		} else if (notifications != null) {
			notifications.showInfo("Region updated (not yet saved).");
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
		}
	}

	private void saveRegionToSettings(RegionEntry entry) throws Exception {
		AppSettings settings = configManager.getSettings();
		if (settings == null) {
			throw new IllegalStateException("Settings are not loaded. Cannot save region.");
		}
		AppSettings.RegionSettings regionSettings = settings.getRegion();
		if (regionSettings == null) {
			regionSettings = new AppSettings.RegionSettings();
			settings.setRegion(regionSettings);
		}

		Rectangle region = entry.region != null ? entry.region : new Rectangle(0, 0, 0, 0);
		regionSettings.setX(region.x);
		regionSettings.setY(region.y);
		regionSettings.setWidth(region.width);
		regionSettings.setHeight(region.height);

		Dimension screen = entry.screen != null ? entry.screen : new Dimension(0, 0);
		regionSettings.setScreenWidth(screen.width);
		regionSettings.setScreenHeight(screen.height);

		regionSettings.setTimestamp(Instant.now());
		configManager.saveSettings();
	}

	private static final class RegionEntry {
		private final String name;
		private final boolean persisted;
		private Rectangle region;
		private Dimension screen;
		private Instant updatedAt;

		private RegionEntry(String name, boolean persisted, Rectangle region, Dimension screen) {
			this.name = name;
			this.persisted = persisted;
			this.region = region;
			this.screen = screen;
			this.updatedAt = Instant.now();
		}

		static RegionEntry persisted(String name, Rectangle region, Dimension screen) {
			return new RegionEntry(name, true, region, screen);
		}

		static RegionEntry transientRegion(String name, Rectangle region, Dimension screen) {
			return new RegionEntry(name, false, region, screen);
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
			setButton.setFocusable(false);
			setButton.addActionListener(e -> setRegion(this.index));
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
			nameLabel.setText(entry != null && entry.name != null ? entry.name : ("Region " + (index + 1)));

			Rectangle rect = entry != null && entry.region != null ? entry.region : new Rectangle(0, 0, 0, 0);
			Dimension screen = entry != null && entry.screen != null ? entry.screen : new Dimension(0, 0);
			String coords = String.format("x=%d, y=%d, w=%d, h=%d (screen %dx%d)", rect.x, rect.y, rect.width, rect.height, screen.width, screen.height);
			coordsLabel.setText(coords);

			if (entry != null && entry.persisted) {
				statusLabel.setText("Saved in settings");
			} else {
				statusLabel.setText("Not saved yet");
			}
		}

		boolean isSelected() {
			return selectButton.isSelected();
		}

		void select() {
			selectButton.setSelected(true);
		}
	}
}
