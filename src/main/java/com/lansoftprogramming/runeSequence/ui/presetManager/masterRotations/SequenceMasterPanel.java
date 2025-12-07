package com.lansoftprogramming.runeSequence.ui.presetManager.masterRotations;

import com.lansoftprogramming.runeSequence.application.SequenceController;
import com.lansoftprogramming.runeSequence.application.SequenceManager;
import com.lansoftprogramming.runeSequence.application.SequenceRunService;
import com.lansoftprogramming.runeSequence.ui.notification.NotificationService;
import com.lansoftprogramming.runeSequence.ui.theme.UiColorPalette;

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
public class SequenceMasterPanel extends JPanel {
	private final SequenceListModel sequenceListModel;
	private final JList<SequenceListModel.SequenceEntry> sequenceList;
	private final JButton addButton;
	private final JButton deleteButton;
	private final JButton importButton;
	private final JButton exportButton;
	private final JButton startButton;
	private final JButton pauseButton;
	private final JButton restartButton;
	private final JLabel statusLabel;
	private final SelectedSequenceIndicator selectedSequenceIndicator;
	private final SequenceRunService sequenceRunService;
	private final Color defaultStartBg;
	private final Color defaultPauseBg;
	private final Color highlightStartBg;
	private final Color highlightPauseBg;
	private final Color defaultStartFg;
	private final Color defaultPauseFg;
	private final Color highlightText;

	/** Listeners to be notified when the list selection changes. */
	private final List<Consumer<SequenceListModel.SequenceEntry>> selectionListeners;
	private final List<Runnable> addListeners;
	private final List<Consumer<SequenceListModel.SequenceEntry>> deleteListeners;
	private final List<Consumer<String>> importListeners;
	private Predicate<String> expressionValidator = s -> false;
	private NotificationService notificationService;

	/**
	 * Constructs the master panel for sequence management.
	 * @param sequenceListModel The data model for the list of sequences.
	 */
	public SequenceMasterPanel(SequenceListModel sequenceListModel,
	                           SelectedSequenceIndicator selectedSequenceIndicator,
	                           SequenceRunService sequenceRunService) {
		this.sequenceListModel = Objects.requireNonNull(sequenceListModel, "sequenceListModel cannot be null");
		this.selectedSequenceIndicator = Objects.requireNonNull(selectedSequenceIndicator, "selectedSequenceIndicator cannot be null");
		this.sequenceRunService = sequenceRunService;
		this.selectionListeners = new ArrayList<>();
		this.addListeners = new ArrayList<>();
		this.deleteListeners = new ArrayList<>();
		this.importListeners = new ArrayList<>();

		setLayout(new BorderLayout());
		setBorder(new EmptyBorder(10, 10, 10, 10));

		sequenceList = new JList<>(sequenceListModel);
		sequenceList.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
		sequenceList.setCellRenderer(new SequenceListCellRenderer(this.selectedSequenceIndicator));
		sequenceList.addListSelectionListener(new SequenceSelectionHandler());

		addButton = new JButton("+");
		addButton.addActionListener(e -> notifyAddListeners());
		startButton = new JButton("Arm");
		startButton.addActionListener(e -> handleStart());
		pauseButton = new JButton("Pause");
		pauseButton.addActionListener(e -> handlePause());
		restartButton = new JButton("Restart");
		restartButton.addActionListener(e -> handleRestart());
		statusLabel = new JLabel("Status: Ready");
		defaultStartBg = startButton.getBackground();
		defaultPauseBg = pauseButton.getBackground();
		defaultStartFg = startButton.getForeground();
		defaultPauseFg = pauseButton.getForeground();
		highlightStartBg = UiColorPalette.SELECTION_ACTIVE_FILL;
		highlightPauseBg = UiColorPalette.TOAST_WARNING_ACCENT;
		highlightText = UiColorPalette.TEXT_INVERSE;

		// Keep UI in sync with controller state
		if (this.sequenceRunService != null) {
			this.sequenceRunService.addStateChangeListener((oldState, newState) -> updateStateIndicator(newState));
			this.sequenceRunService.addProgressListener(progress -> SwingUtilities.invokeLater(this::refreshStatusLabel));
			updateStateIndicator(this.sequenceRunService.getCurrentState());
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

		deleteButton.addActionListener(e -> notifyDeleteListeners(getSelectedSequenceEntry()));

		importButton = new JButton("Import");
		importButton.addActionListener(e -> importFromClipboard());
		exportButton = new JButton("Export");
		exportButton.setEnabled(false);
		exportButton.addActionListener(e -> copySelectedPresetExpression());

		layoutComponents();
	}

	/**
	 * Arranges the control buttons and the sequence list within the panel.
	 */
	private void layoutComponents() {
		JPanel runPanel = createRunControlPanel();
		JPanel controlsPanel = createCrudPanel();

		JPanel topPanel = new JPanel();
		topPanel.setLayout(new BoxLayout(topPanel, BoxLayout.Y_AXIS));
		runPanel.setAlignmentX(Component.LEFT_ALIGNMENT);
		controlsPanel.setAlignmentX(Component.LEFT_ALIGNMENT);
		topPanel.add(runPanel);
		topPanel.add(Box.createVerticalStrut(8));
		topPanel.add(controlsPanel);

		JScrollPane listScrollPane = new JScrollPane(sequenceList);
		listScrollPane.setVerticalScrollBarPolicy(JScrollPane.VERTICAL_SCROLLBAR_AS_NEEDED);
		listScrollPane.getVerticalScrollBar().setUnitIncrement(5);

		add(topPanel, BorderLayout.NORTH);
		add(listScrollPane, BorderLayout.CENTER);
	}

	private JPanel createRunControlPanel() {
		JPanel panel = new JPanel(new BorderLayout(5, 2));

		JPanel buttonRow = new JPanel(new FlowLayout(FlowLayout.LEFT, 5, 0));
		buttonRow.add(startButton);
		buttonRow.add(pauseButton);
		buttonRow.add(restartButton);

		JPanel statusRow = new JPanel(new FlowLayout(FlowLayout.LEFT, 0, 0));
		statusRow.add(statusLabel);

		panel.add(statusRow, BorderLayout.NORTH);
		panel.add(buttonRow, BorderLayout.CENTER);
		return panel;
	}

	private JPanel createCrudPanel() {
		JPanel controlsPanel = new JPanel(new GridLayout(2, 2, 5, 5));
		controlsPanel.add(addButton);
		controlsPanel.add(deleteButton);
		controlsPanel.add(importButton);
		controlsPanel.add(exportButton);
		return controlsPanel;
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

	private void handleStart() {
		if (sequenceRunService == null) {
			if (notificationService != null) {
				notificationService.showError("Start action unavailable.");
			}
			return;
		}
		SequenceManager.SequenceProgress progress = sequenceRunService.getProgressSnapshot();

		if (progress != null && progress.isSequenceComplete()) {
			sequenceRunService.prepareReadyState();
		}

		sequenceRunService.start();
		updateStateIndicator(sequenceRunService.getCurrentState());
		if (notificationService != null) {
			notificationService.showInfo("Start requested.");
		}
	}

	private void handlePause() {
		if (sequenceRunService == null) {
			if (notificationService != null) {
				notificationService.showError("Pause action unavailable.");
			}
			return;
		}
		sequenceRunService.pause();
		updateStateIndicator(sequenceRunService.getCurrentState());
		if (notificationService != null) {
			notificationService.showInfo("Detection paused.");
		}
	}

	private void handleRestart() {
		if (sequenceRunService == null) {
			if (notificationService != null) {
				notificationService.showError("Restart action unavailable.");
			}
			return;
		}
		sequenceRunService.restart();
		updateStateIndicator(sequenceRunService.getCurrentState());
		if (notificationService != null) {
			notificationService.showSuccess("Restart requested.");
		}
	}

	private void updateStateIndicator(SequenceController.State state) {
		SwingUtilities.invokeLater(() -> {
			boolean pauseActive = state == SequenceController.State.PAUSED;
			boolean startActive = state == SequenceController.State.READY;

			startButton.setBackground(startActive ? highlightStartBg : defaultStartBg);
			pauseButton.setBackground(pauseActive ? highlightPauseBg : defaultPauseBg);
			startButton.setForeground(startActive ? highlightText : defaultStartFg);
			pauseButton.setForeground(pauseActive ? highlightText : defaultPauseFg);
			startButton.setOpaque(true);
			pauseButton.setOpaque(true);
			updateStartButtonLabel(state);
			boolean canStart = state == SequenceController.State.READY || state == SequenceController.State.PAUSED;
			startButton.setEnabled(canStart);
			restartButton.setEnabled(true);
			restartButton.setVisible(true);

			refreshStatusLabel();
		});
	}

	private void updateStartButtonLabel(SequenceController.State state) {
		String label = switch (state) {
			case RUNNING -> "Running";
			case ARMED -> "Armed";
			case PAUSED -> "Start";
			default -> "Arm";
		};
		startButton.setText(label);
	}

	private void refreshStatusLabel() {
		if (sequenceRunService == null) {
			statusLabel.setText("Status: Controls unavailable");
			return;
		}

		SequenceController.State state = sequenceRunService.getCurrentState();
		SequenceManager.SequenceProgress progress = sequenceRunService.getProgressSnapshot();
		boolean detectionRunning = sequenceRunService.isDetectionRunning();
		String statusText = buildStatusText(state, progress, detectionRunning);
		statusLabel.setText(statusText);
	}

	private String buildStatusText(SequenceController.State state,
	                               SequenceManager.SequenceProgress progress,
	                               boolean detectionRunning) {
		if (progress == null || !progress.hasActiveSequence()) {
			return "Status: No rotation selected";
		}

		if (progress.isSequenceComplete()) {
			return "Status: Rotation complete. Restart to run again.";
		}

		String abilities = describeAbilities(progress);
		int totalSteps = progress.getTotalSteps();
		int displayStep = progress.getCurrentStepIndex() >= 0 ? progress.getCurrentStepIndex() + 1 : 0;
		String stepInfo = totalSteps > 0 && displayStep > 0
				? "step " + displayStep + "/" + totalSteps
				: "current step";

		return switch (state) {
			case PAUSED -> "Status: Paused - detection stopped.";
			case ARMED -> "Status: Armed - waiting for latch (" + abilities + ").";
			case RUNNING -> "Status: Running " + stepInfo + " (" + abilities + ").";
			case READY -> {
				if (!detectionRunning) {
					yield "Status: Ready - detection halted.";
				}
				yield "Status: Ready - standing by (" + abilities + ").";
			}
			default -> "Status: " + state;
		};
	}

	private String describeAbilities(SequenceManager.SequenceProgress progress) {
		if (progress == null || progress.getCurrentStepAbilities() == null
				|| progress.getCurrentStepAbilities().isEmpty()) {
			return "looking for abilities";
		}
		return String.join(" / ", progress.getCurrentStepAbilities());
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

	private void copySelectedPresetExpression() {
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

		try {
			StringSelection selection = new StringSelection(expression);
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
	}

	/**
	 * Optional injection of a centralized notifier for UI feedback.
	 */
	public void setNotificationService(NotificationService notificationService) {
		this.notificationService = notificationService;
	}

	/**
	 * Custom renderer to control the appearance of each item in the sequence list.
	 */
	private static class SequenceListCellRenderer extends JPanel implements ListCellRenderer<SequenceListModel.SequenceEntry> {
		private final JLabel textLabel = new JLabel();
		private final JLabel iconLabel = new JLabel();
		private final SelectedSequenceIndicator selectedIndicator;
		private final EmptyBorder padding = new EmptyBorder(5, 10, 5, 10);
		private final Border focusBorder = UIManager.getBorder("List.focusCellHighlightBorder");
		private final Border noFocusBorder = new EmptyBorder(1, 1, 1, 1);

		private SequenceListCellRenderer(SelectedSequenceIndicator selectedIndicator) {
			super(new BorderLayout());
			this.selectedIndicator = selectedIndicator;
			setOpaque(true);

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
			textLabel.setForeground(isSelected ? list.getSelectionForeground() : list.getForeground());

			Icon selectedIcon = value != null ? selectedIndicator.iconFor(value.getId()) : null;
			iconLabel.setIcon(selectedIcon);
			Icon columnIcon = selectedIndicator.getSelectedIcon();
			int reservedWidth = columnIcon != null ? columnIcon.getIconWidth() + 10 : 20;
			iconLabel.setPreferredSize(new Dimension(reservedWidth,
					list.getFixedCellHeight() > 0 ? list.getFixedCellHeight() : textLabel.getPreferredSize().height));

			setBackground(isSelected ? list.getSelectionBackground() : list.getBackground());
			setForeground(isSelected ? list.getSelectionForeground() : list.getForeground());

			Border outer = cellHasFocus && focusBorder != null ? focusBorder : noFocusBorder;
			setBorder(new CompoundBorder(outer, padding));

			setToolTipText(value != null && selectedIndicator.isSelected(value.getId())
					? "Currently selected rotation"
					: null);

			return this;
		}
	}
}
