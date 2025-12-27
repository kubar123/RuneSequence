package com.lansoftprogramming.runeSequence.ui.presetManager.masterRotations;

import com.lansoftprogramming.runeSequence.application.SequenceRunService;
import com.lansoftprogramming.runeSequence.core.sequence.model.AbilitySettingsOverrides;
import com.lansoftprogramming.runeSequence.core.sequence.parser.RotationDslCodec;
import com.lansoftprogramming.runeSequence.ui.notification.NotificationService;
import com.lansoftprogramming.runeSequence.ui.presetManager.service.AbilityOverridesService;
import com.lansoftprogramming.runeSequence.ui.theme.*;

import javax.swing.*;
import javax.swing.border.Border;
import javax.swing.border.CompoundBorder;
import javax.swing.border.EmptyBorder;
import javax.swing.event.ListSelectionEvent;
import javax.swing.event.ListSelectionListener;
import java.awt.*;
import java.awt.datatransfer.Clipboard;
import java.awt.datatransfer.DataFlavor;
import java.awt.datatransfer.StringSelection;
import java.awt.datatransfer.UnsupportedFlavorException;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.awt.event.MouseMotionAdapter;
import java.beans.PropertyChangeListener;
import java.io.IOException;
import java.net.URL;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.function.Consumer;
import java.util.function.Predicate;

/**
 * A panel that displays a list of sequences and controls for managing them.
 * This acts as the "master" view in a master-detail interface, where selecting an item
 * in this panel can update a "detail" view elsewhere.
 */
public class SequenceMasterPanel extends ThemedPanel implements SequenceRunPresenter.SequenceRunView {
	private static final String HOVER_INDEX_KEY = SequenceMasterPanel.class.getName() + ".hoverIndex";

	private final SequenceListModel sequenceListModel;
	private final AbilityOverridesService overridesService;
	private final JList<SequenceListModel.SequenceEntry> sequenceList;
	private final JButton addButton;
	private final JButton deleteButton;
	private final JButton importButton;
	private final JButton exportButton;
	private final JPopupMenu exportMenu;
	private final JMenuItem exportShallowItem;
	private final JMenuItem exportDeepItem;
	private final SelectedSequenceIndicator selectedSequenceIndicator;
	private final SequenceRunService sequenceRunService;
	private final RunControlPanel runControlPanel;
	private final SequenceRunPresenter runPresenter;

	/** Listeners to be notified when the list selection changes. */
	private final List<Consumer<SequenceListModel.SequenceEntry>> selectionListeners;
	private final List<Runnable> addListeners;
	private final List<Consumer<SequenceListModel.SequenceEntry>> deleteListeners;
	private final List<Consumer<String>> importListeners;
	private Predicate<String> expressionValidator = s -> false;
	private NotificationService notificationService;
	private transient PropertyChangeListener themeListener;

	/**
	 * Constructs the master panel for sequence management.
	 * @param sequenceListModel The data model for the list of sequences.
	 */
	public SequenceMasterPanel(SequenceListModel sequenceListModel,
	                           AbilityOverridesService overridesService,
	                           SelectedSequenceIndicator selectedSequenceIndicator,
	                           SequenceRunService sequenceRunService) {
		super(PanelStyle.DETAIL, new BorderLayout());
		this.sequenceListModel = Objects.requireNonNull(sequenceListModel, "sequenceListModel cannot be null");
		this.overridesService = Objects.requireNonNull(overridesService, "overridesService cannot be null");
		this.selectedSequenceIndicator = Objects.requireNonNull(selectedSequenceIndicator, "selectedSequenceIndicator cannot be null");
		this.sequenceRunService = sequenceRunService;
		this.selectionListeners = new ArrayList<>();
		this.addListeners = new ArrayList<>();
		this.deleteListeners = new ArrayList<>();
		this.importListeners = new ArrayList<>();

		setMinimumSize(new Dimension(0, 0));

		sequenceList = new JList<>(sequenceListModel);
		sequenceList.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
		sequenceList.setCellRenderer(new SequenceListCellRenderer(this.selectedSequenceIndicator));
		sequenceList.addListSelectionListener(new SequenceSelectionHandler());
		sequenceList.setOpaque(false);
		installHoverTracking(sequenceList);

		addButton = new JButton("+");
		ThemedButtons.apply(addButton, ButtonStyle.DEFAULT);
		addButton.addActionListener(e -> notifyAddListeners());

		runControlPanel = new RunControlPanel();
		runPresenter = new SequenceRunPresenter(sequenceRunService, notificationService);
		runPresenter.attachView(this);
		runControlPanel.addStartListener(e -> runPresenter.onStartRequested());
		runControlPanel.addPauseListener(e -> runPresenter.onPauseRequested());
		runControlPanel.addRestartListener(e -> runPresenter.onRestartRequested());

		// Keep UI in sync with controller state
		if (this.sequenceRunService != null) {
			this.sequenceRunService.addStateChangeListener((oldState, newState) -> runPresenter.onStateChanged(oldState, newState));
			this.sequenceRunService.addProgressListener(runPresenter::onProgressChanged);
			runPresenter.refreshFromService();
		}

		ImageIcon trashIcon = null;
		try {
			// Load icon from resources
			URL iconUrl = getClass().getResource("/ui/trash-512.png");
			if (iconUrl != null) {
				ImageIcon originalIcon = new ImageIcon(iconUrl);
				Image image = originalIcon.getImage();
				// Scale the icon to fit the button.
				Image scaledImage = image.getScaledInstance(16, 16, Image.SCALE_SMOOTH);
				trashIcon = new ImageIcon(scaledImage);
			}
		} catch (Exception e) {
			e.printStackTrace(); // Log error for debugging
		}

		if (trashIcon != null) {
			deleteButton = new JButton(trashIcon);
		} else {
			deleteButton = new JButton("🗑"); // Fallback to text if icon fails to load
		}
		ThemedButtons.apply(deleteButton, ButtonStyle.DEFAULT);

		deleteButton.addActionListener(e -> notifyDeleteListeners(getSelectedSequenceEntry()));

		importButton = new JButton("Import");
		ThemedButtons.apply(importButton, ButtonStyle.DEFAULT);
		importButton.addActionListener(e -> importFromClipboard());
		exportButton = new JButton("Export \u25BE");
		ThemedButtons.apply(exportButton, ButtonStyle.DEFAULT);
		exportButton.setEnabled(false);
		exportButton.setToolTipText("Export to clipboard");
		exportButton.getAccessibleContext().setAccessibleName("Export menu");

		exportMenu = new JPopupMenu();
		exportShallowItem = new JMenuItem("Export (Shallow)");
		exportDeepItem = new JMenuItem("Export (Deep)");
		exportMenu.add(exportShallowItem);
		exportMenu.add(exportDeepItem);

		exportShallowItem.addActionListener(e -> copySelectedPresetExpression(false));
		exportDeepItem.addActionListener(e -> copySelectedPresetExpression(true));

		exportButton.addActionListener(e -> exportMenu.show(exportButton, 0, exportButton.getHeight()));
		updateExportButtonState(false);

		layoutComponents();
	}

	@Override
	public void addNotify() {
		super.addNotify();
		applyThemeToComponents();
		installThemeListener();
	}

	@Override
	public void removeNotify() {
		uninstallThemeListener();
		super.removeNotify();
	}

	/**
	 * Arranges the control buttons and the sequence list within the panel.
	 */
	private void layoutComponents() {
		JPanel controlsPanel = createCrudPanel();

		controlsPanel.setOpaque(false);

		JScrollPane listScrollPane = new JScrollPane(sequenceList);
		listScrollPane.setVerticalScrollBarPolicy(JScrollPane.VERTICAL_SCROLLBAR_AS_NEEDED);
		listScrollPane.getVerticalScrollBar().setUnitIncrement(5);
		listScrollPane.setBorder(BorderFactory.createEmptyBorder());
		listScrollPane.setOpaque(false);
		listScrollPane.getViewport().setOpaque(false);

		JPanel listContainer = new InsetContainerPanel(new BorderLayout(), new Insets(5, 6, 5, 6));
		listContainer.add(listScrollPane, BorderLayout.CENTER);

		add(controlsPanel, BorderLayout.NORTH);
		add(listContainer, BorderLayout.CENTER);
	}

	public JComponent getRunControlPanel() {
		return runControlPanel;
	}

	private JPanel createCrudPanel() {
		JPanel controlsPanel = new JPanel(new FlowLayout(FlowLayout.LEFT, 6, 0));
		controlsPanel.setMinimumSize(new Dimension(0, 0));
		controlsPanel.setBorder(new EmptyBorder(0, 0, 8, 0));
		controlsPanel.add(addButton);
		controlsPanel.add(createRowSeparator(addButton));
		controlsPanel.add(deleteButton);
		controlsPanel.add(createRowSeparator(deleteButton));
		controlsPanel.add(importButton);
		controlsPanel.add(createRowSeparator(importButton));
		controlsPanel.add(exportButton);
		return controlsPanel;
	}

	private static JComponent createRowSeparator(JComponent heightReference) {
		int height = heightReference.getPreferredSize().height;
		return new ThemedVerticalSeparator(height);
	}

	private void applyThemeToComponents() {
		Theme theme = ThemeManager.getTheme();
		Color textColor = theme != null ? theme.getTextPrimaryColor() : null;
		if (textColor != null) {
			sequenceList.setForeground(textColor);
			sequenceList.setSelectionForeground(textColor);
		}
		repaint();
	}

	private void installThemeListener() {
		if (themeListener != null) {
			return;
		}
		themeListener = evt -> applyThemeToComponents();
		ThemeManager.addThemeChangeListener(themeListener);
	}

	private void uninstallThemeListener() {
		if (themeListener == null) {
			return;
		}
		ThemeManager.removeThemeChangeListener(themeListener);
		themeListener = null;
	}

	private static void installHoverTracking(JList<?> list) {
		if (list == null) {
			return;
		}

		list.putClientProperty(HOVER_INDEX_KEY, -1);

		list.addMouseMotionListener(new MouseMotionAdapter() {
			@Override
			public void mouseMoved(MouseEvent e) {
				int next = list.locationToIndex(e.getPoint());
				if (next >= 0) {
					Rectangle bounds = list.getCellBounds(next, next);
					if (bounds != null && !bounds.contains(e.getPoint())) {
						next = -1;
					}
				}
				setHoverIndex(list, next);
			}
		});

		list.addMouseListener(new MouseAdapter() {
			@Override
			public void mouseExited(MouseEvent e) {
				setHoverIndex(list, -1);
			}
		});
	}

	private static void setHoverIndex(JList<?> list, int nextIndex) {
		Object current = list.getClientProperty(HOVER_INDEX_KEY);
		int currentIndex = current instanceof Integer i ? i : -1;
		if (currentIndex == nextIndex) {
			return;
		}

		list.putClientProperty(HOVER_INDEX_KEY, nextIndex);
		repaintListCell(list, currentIndex);
		repaintListCell(list, nextIndex);
	}

	private static void repaintListCell(JList<?> list, int index) {
		if (index < 0) {
			return;
		}
		Rectangle bounds = list.getCellBounds(index, index);
		if (bounds != null) {
			list.repaint(bounds);
		}
	}

	public void addSelectionListener(Consumer<SequenceListModel.SequenceEntry> listener) {
		selectionListeners.add(listener);
	}

	public void addAddListener(Runnable listener) {
		addListeners.add(listener);
	}

	public void addDeleteListener(Consumer<SequenceListModel.SequenceEntry> listener) {
		deleteListeners.add(listener);
	}

	public void addImportListener(Consumer<String> listener) {
		importListeners.add(listener);
	}

	public void setExpressionValidator(Predicate<String> expressionValidator) {
		if (expressionValidator != null) {
			this.expressionValidator = expressionValidator;
		}
	}

	public void refreshList() {
		sequenceList.repaint();
	}

	public void clearSelection() {
		sequenceList.clearSelection();
		updateExportButtonState(false);
	}

	public void selectSequenceById(String id) {
		if (id == null) {
			return;
		}

		int index = sequenceListModel.indexOf(id);
		if (index >= 0) {
			sequenceList.setSelectedIndex(index);
			sequenceList.ensureIndexIsVisible(index);
			updateExportButtonState(true);
		}
	}

	public SequenceListModel.SequenceEntry getSelectedSequenceEntry() {
		int selectedIndex = sequenceList.getSelectedIndex();
		return selectedIndex != -1 ? sequenceListModel.getElementAt(selectedIndex) : null;
	}

	/**
	 * Notifies all registered listeners of a selection change.
	 * @param entry The newly selected sequence entry, or null if selection is cleared.
	 */
	private void notifySelectionListeners(SequenceListModel.SequenceEntry entry) {
		for (Consumer<SequenceListModel.SequenceEntry> listener : selectionListeners) {
			listener.accept(entry);
		}
	}

	private void notifyAddListeners() {
		for (Runnable listener : addListeners) {
			listener.run();
		}
	}

	private void notifyDeleteListeners(SequenceListModel.SequenceEntry entry) {
		for (Consumer<SequenceListModel.SequenceEntry> listener : deleteListeners) {
			listener.accept(entry);
		}
	}

	private void notifyImportListeners(String expression) {
		for (Consumer<String> listener : importListeners) {
			listener.accept(expression);
		}
	}

	private void importFromClipboard() {
		String expression = getClipboardContent();
		if (expression == null) {
			return; // Toast shown in getClipboardContent
		}

		if (expression.trim().isEmpty()) {
			if (notificationService != null) {
				notificationService.showInfo("Clipboard is empty.");
			}
			return;
		}

		if (expressionValidator.test(expression)) {
			notifyImportListeners(expression);
		} else {
			if (notificationService != null) {
				notificationService.showError("Invalid ability expression in clipboard.");
			}
		}
	}

	private String getClipboardContent() {
		try {
			Clipboard clipboard = Toolkit.getDefaultToolkit().getSystemClipboard();
			if (clipboard.isDataFlavorAvailable(DataFlavor.stringFlavor)) {
				return (String) clipboard.getData(DataFlavor.stringFlavor);
			}
			return "";
		} catch (UnsupportedFlavorException | IOException | IllegalStateException e) {
			if (notificationService != null) {
				notificationService.showError("Could not read from clipboard.");
			}
			return null;
		}
	}

	/**
	 * Handles selection changes in the sequence list.
	 */
	private class SequenceSelectionHandler implements ListSelectionListener {
		@Override
		public void valueChanged(ListSelectionEvent e) {
			// This check prevents firing the event twice for a single mouse click.
			if (!e.getValueIsAdjusting()) {
				int selectedIndex = sequenceList.getSelectedIndex();
				SequenceListModel.SequenceEntry entry = selectedIndex != -1
						? sequenceListModel.getElementAt(selectedIndex)
						: null;
				updateExportButtonState(entry != null);
				notifySelectionListeners(entry);
			}
		}
	}

	private void copySelectedPresetExpression(boolean deepExport) {
		SequenceListModel.SequenceEntry entry = getSelectedSequenceEntry();
		if (entry == null || entry.getPresetData() == null) {
			Toolkit.getDefaultToolkit().beep();
			if (notificationService != null) {
				notificationService.showInfo("Please select a preset to export.");
			}
			return;
		}

		String expression = entry.getPresetData().getExpression();
		if (expression == null) {
			expression = "";
		}

		String exportText;
		if (deepExport) {
			java.util.Map<String, AbilitySettingsOverrides> overridesByLabel =
					overridesService.toDomainOverrides(entry.getPresetData().getAbilitySettings());
			exportText = RotationDslCodec.exportDeep(expression, overridesByLabel);
		} else {
			exportText = RotationDslCodec.exportSimple(expression);
		}

		try {
			StringSelection selection = new StringSelection(exportText);
			Toolkit.getDefaultToolkit().getSystemClipboard().setContents(selection, null);
			if (notificationService != null) {
				notificationService.showSuccess("Copied to clipboard.");
			}
		} catch (IllegalStateException clipboardUnavailable) {
			if (notificationService != null) {
				notificationService.showError("Clipboard is not available. Try again.");
			}
		}
	}

	private void updateExportButtonState(boolean hasSelection) {
		exportButton.setEnabled(hasSelection);
		exportShallowItem.setEnabled(hasSelection);
		exportDeepItem.setEnabled(hasSelection);
	}

	/**
	 * Optional injection of a centralized notifier for UI feedback.
	 */
	public void setNotificationService(NotificationService notificationService) {
		this.notificationService = notificationService;
		runPresenter.setNotificationService(notificationService);
	}

	@Override
	public void setStartButtonState(String label, boolean enabled, SequenceRunPresenter.StartAccent accent) {
		runControlPanel.setStartButtonState(label, enabled, accent);
	}

	@Override
	public void setPauseButtonState(boolean enabled, boolean highlighted) {
		runControlPanel.setPauseButtonState(enabled, highlighted);
	}

	@Override
	public void setRestartButtonEnabled(boolean enabled) {
		runControlPanel.setRestartButtonEnabled(enabled);
	}

	@Override
	public void setStatusText(String text) {
		runControlPanel.setStatusText(rewriteStatusRotationId(text));
	}

	private String rewriteStatusRotationId(String text) {
		if (text == null || text.isBlank()) {
			return text != null ? text : "";
		}

		int openBracket = text.lastIndexOf('[');
		if (openBracket < 0) {
			return text;
		}
		int closeBracket = text.indexOf(']', openBracket);
		if (closeBracket < 0) {
			return text;
		}

		String id = text.substring(openBracket + 1, closeBracket).trim();
		String commonName = sequenceListModel.commonNameForId(id);
		if (commonName == null) {
			return text;
		}

		return text.substring(0, openBracket + 1) + commonName + text.substring(closeBracket);
	}

	/**
	 * Custom renderer to control the appearance of each item in the sequence list.
	 */
	private static class SequenceListCellRenderer extends JPanel implements ListCellRenderer<SequenceListModel.SequenceEntry> {
		private static final int HOVER_ALPHA = 28;
		private static final int SELECTED_ALPHA = 64;
		private static final int SELECTED_GLOW_ALPHA = 140;
		private static final int FOCUS_ALPHA = 110;

		private final JLabel textLabel = new JLabel();
		private final JLabel iconLabel = new JLabel();
		private final SelectedSequenceIndicator selectedIndicator;
		private final EmptyBorder padding = new EmptyBorder(5, 10, 5, 10);
		private final Border outerPadding = new EmptyBorder(1, 1, 1, 1);
		private boolean hovered;
		private boolean selected;
		private boolean focused;
		private Color hoverOverlay;
		private Color selectedOverlay;
		private Color selectedGlow;
		private Color focusRing;

		private SequenceListCellRenderer(SelectedSequenceIndicator selectedIndicator) {
			super(new BorderLayout());
			this.selectedIndicator = selectedIndicator;
			setOpaque(false);

			textLabel.setOpaque(false);
			iconLabel.setOpaque(false);
			iconLabel.setHorizontalAlignment(SwingConstants.RIGHT);
			iconLabel.setBorder(new EmptyBorder(0, 0, 0, 10));

			add(textLabel, BorderLayout.CENTER);
			add(iconLabel, BorderLayout.EAST);
		}

		@Override
		public Component getListCellRendererComponent(
				JList<? extends SequenceListModel.SequenceEntry> list,
				SequenceListModel.SequenceEntry value,
				int index,
				boolean isSelected,
				boolean cellHasFocus) {

			String name = value != null ? value.getPresetData().getName() : "";
			textLabel.setText(name != null ? name : "");
			textLabel.setFont(list.getFont());

			Theme theme = ThemeManager.getTheme();
			Color textColor = theme != null ? theme.getTextPrimaryColor() : list.getForeground();
			textLabel.setForeground(textColor != null ? textColor : list.getForeground());

			Icon selectedIcon = value != null ? selectedIndicator.iconFor(value.getId()) : null;
			iconLabel.setIcon(selectedIcon);
			Icon columnIcon = selectedIndicator.getSelectedIcon();
			int reservedWidth = columnIcon != null ? columnIcon.getIconWidth() + 10 : 20;
			iconLabel.setPreferredSize(new Dimension(reservedWidth,
					list.getFixedCellHeight() > 0 ? list.getFixedCellHeight() : textLabel.getPreferredSize().height));

			Color accentHover = theme != null ? theme.getAccentHoverColor() : null;
			Color accentPrimary = theme != null ? theme.getAccentPrimaryColor() : null;
			hoverOverlay = withAlpha(accentHover != null ? accentHover : list.getForeground(), HOVER_ALPHA);
			selectedOverlay = withAlpha(accentPrimary != null ? accentPrimary : list.getForeground(), SELECTED_ALPHA);
			selectedGlow = withAlpha(accentPrimary != null ? accentPrimary : list.getForeground(), SELECTED_GLOW_ALPHA);
			focusRing = withAlpha(accentHover != null ? accentHover : list.getForeground(), FOCUS_ALPHA);

			Object hoverIndex = list.getClientProperty(HOVER_INDEX_KEY);
			hovered = (hoverIndex instanceof Integer i) && i == index && !isSelected;
			selected = isSelected;
			focused = cellHasFocus;

			setBorder(new CompoundBorder(outerPadding, padding));

			setToolTipText(value != null && selectedIndicator.isSelected(value.getId())
					? "Currently selected rotation"
					: null);

			return this;
		}

		@Override
		protected void paintComponent(Graphics graphics) {
			super.paintComponent(graphics);

			int width = getWidth();
			int height = getHeight();
			if (width <= 0 || height <= 0) {
				return;
			}

			if (!hovered && !selected && !focused) {
				return;
			}

			Graphics2D g2 = graphics instanceof Graphics2D ? (Graphics2D) graphics.create() : null;
			if (g2 == null) {
				return;
			}

			try {
				g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);

				if (selected && selectedOverlay != null) {
					g2.setColor(selectedOverlay);
					g2.fillRect(0, 0, width, height);
					if (selectedGlow != null) {
						g2.setColor(selectedGlow);
						g2.drawRect(0, 0, width - 1, height - 1);
					}
				} else if (hovered && hoverOverlay != null) {
					g2.setColor(hoverOverlay);
					g2.fillRect(0, 0, width, height);
				}

				if (focused && focusRing != null) {
					g2.setColor(focusRing);
					g2.drawRect(1, 1, width - 3, height - 3);
				}
			} finally {
				g2.dispose();
			}
		}

		private static Color withAlpha(Color color, int alpha) {
			if (color == null) {
				return null;
			}
			int clamped = Math.max(0, Math.min(255, alpha));
			return new Color(color.getRed(), color.getGreen(), color.getBlue(), clamped);
		}
	}

	private static final class ThemedVerticalSeparator extends JComponent {
		private final int height;

		private ThemedVerticalSeparator(int height) {
			this.height = Math.max(0, height);
			setOpaque(false);
			setPreferredSize(new Dimension(1, this.height));
		}

		@Override
		protected void paintComponent(Graphics graphics) {
			super.paintComponent(graphics);
			int h = getHeight();
			if (h <= 0) {
				return;
			}

			Theme theme = ThemeManager.getTheme();
			Color line = theme != null ? theme.getInsetBorderColor() : null;
			if (line == null) {
				return;
			}

			Graphics2D g2 = graphics instanceof Graphics2D ? (Graphics2D) graphics.create() : null;
			if (g2 == null) {
				return;
			}
			try {
				g2.setColor(line);
				g2.drawLine(0, 0, 0, h - 1);
			} finally {
				g2.dispose();
			}
		}
	}

	private static final class InsetContainerPanel extends JPanel {
		private InsetContainerPanel(LayoutManager layout, Insets contentPadding) {
			super(layout);
			setOpaque(false);
			Insets padding = contentPadding != null ? contentPadding : new Insets(0, 0, 0, 0);
			setBorder(new EmptyBorder(
					1 + padding.top,
					1 + padding.left,
					1 + padding.bottom,
					1 + padding.right
			));
		}

		@Override
		protected void paintComponent(Graphics graphics) {
			super.paintComponent(graphics);

			int width = getWidth();
			int height = getHeight();
			if (width <= 0 || height <= 0) {
				return;
			}

			Theme theme = ThemeManager.getTheme();
			if (theme == null) {
				return;
			}

			java.awt.image.BufferedImage background = theme.getPanelBackgroundImage(PanelStyle.TAB_CONTENT);
			Color insetBorder = theme.getInsetBorderColor();

			Graphics2D g2 = graphics instanceof Graphics2D ? (Graphics2D) graphics.create() : null;
			if (g2 == null) {
				return;
			}

			try {
				g2.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BILINEAR);
				if (background != null) {
					Rectangle target = new Rectangle(0, 0, width, height);
					com.lansoftprogramming.runeSequence.ui.theme.BackgroundFillPainter.paintTopLeftCropScale(g2, background, target);
				}

				if (insetBorder != null) {
					g2.setColor(insetBorder);
					g2.drawRect(0, 0, width - 1, height - 1);
				}
			} finally {
				g2.dispose();
			}
		}
	}
}