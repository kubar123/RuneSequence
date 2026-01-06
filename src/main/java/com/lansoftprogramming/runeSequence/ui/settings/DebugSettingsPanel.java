package com.lansoftprogramming.runeSequence.ui.settings;

import com.lansoftprogramming.runeSequence.infrastructure.config.ConfigManager;
import com.lansoftprogramming.runeSequence.ui.settings.debug.BackpackSaveDebugService;
import com.lansoftprogramming.runeSequence.ui.settings.debug.IconDetectionDebugService;
import com.lansoftprogramming.runeSequence.ui.theme.*;

import javax.swing.*;
import javax.swing.border.CompoundBorder;
import javax.swing.border.EmptyBorder;
import java.awt.*;
import java.io.File;
import java.nio.file.Path;
import java.util.Locale;

public class DebugSettingsPanel extends ThemedPanel implements IconDetectionDebugService.Listener {
	private static final String DIALOG_TITLE = "RuneSequence - Debug";
	private static final double DEFAULT_BACKPACK_TOLERANCE_PERCENT = 39.0d;

	private final ConfigManager configManager;
	private final IconDetectionDebugService debugService;
	private final JButton startButton;
	private final JButton stopButton;
	private final JButton openLogButton;
	private final JLabel progressLabel;
	private final JLabel totalGreenLabel;
	private final JLabel totalYellowLabel;
	private final JLabel totalNotFoundLabel;
	private final JLabel statusLabel;
	private final JSpinner yellowToleranceSpinner;
	private final JSpinner notFoundToleranceSpinner;
	private final JTextField manualTemplateField;
	private final JButton manualTestButton;
	private final JLabel manualResultLabel;
	private final java.util.concurrent.atomic.AtomicLong manualTestRequestId = new java.util.concurrent.atomic.AtomicLong(0L);
	private final JTextField backpackToleranceField;
	private final JButton saveBackpackButton;
	private final java.util.concurrent.atomic.AtomicLong backpackSaveRequestId = new java.util.concurrent.atomic.AtomicLong(0L);
	private volatile boolean savingBackpack = false;
	private final JTextArea greenArea;
	private final JTextArea yellowArea;
	private final JTextArea notFoundArea;
	private volatile File lastLogFile;

	public DebugSettingsPanel(ConfigManager configManager, IconDetectionDebugService debugService) {
		super(PanelStyle.TAB_CONTENT, new BorderLayout());
		this.configManager = configManager;
		this.debugService = debugService;

		setBorder(new CompoundBorder(getBorder(), new EmptyBorder(15, 15, 15, 15)));

		startButton = new JButton("Start Icon Detection Test");
		ThemedButtons.apply(startButton, ButtonStyle.DEFAULT);
		startButton.addActionListener(e -> handleStart());

		stopButton = new JButton("Stop");
		ThemedButtons.apply(stopButton, ButtonStyle.DEFAULT);
		stopButton.addActionListener(e -> handleStop());

		openLogButton = new JButton("Open icon_Detection.log");
		ThemedButtons.apply(openLogButton, ButtonStyle.DEFAULT);
		openLogButton.addActionListener(e -> handleOpenLog());

		progressLabel = new JLabel(" ");
		progressLabel.setOpaque(false);

		totalGreenLabel = createTotalLabel();
		totalYellowLabel = createTotalLabel();
		totalNotFoundLabel = createTotalLabel();

		statusLabel = new JLabel(" ");
		statusLabel.setOpaque(false);
		statusLabel.setForeground(UiColorPalette.TEXT_MUTED);

		yellowToleranceSpinner = new JSpinner(new SpinnerNumberModel(10, 0, 99, 1));
		notFoundToleranceSpinner = new JSpinner(new SpinnerNumberModel(20, 0, 99, 1));

		manualTemplateField = new JTextField();
		manualTestButton = new JButton("Test");
		ThemedButtons.apply(manualTestButton, ButtonStyle.DEFAULT);
		manualTestButton.addActionListener(e -> handleManualTest());
		manualTemplateField.addActionListener(e -> handleManualTest());

		manualResultLabel = new JLabel(" ");
		manualResultLabel.setOpaque(false);
		manualResultLabel.setForeground(UiColorPalette.TEXT_MUTED);

		backpackToleranceField = new JTextField(String.format(Locale.ROOT, "%.0f", DEFAULT_BACKPACK_TOLERANCE_PERCENT));
		backpackToleranceField.setColumns(4);

		saveBackpackButton = new JButton("Save backpack");
		ThemedButtons.apply(saveBackpackButton, ButtonStyle.DEFAULT);
		saveBackpackButton.addActionListener(e -> handleSaveBackpack());

		greenArea = createOutputArea();
		greenArea.setRows(14);

		yellowArea = createOutputArea();
		yellowArea.setRows(6);

		notFoundArea = createOutputArea();
		notFoundArea.setRows(8);

		add(createMainPanel(), BorderLayout.CENTER);
		add(statusLabel, BorderLayout.SOUTH);

		refreshControls();
	}

	@Override
	public void addNotify() {
		super.addNotify();
		if (debugService != null) {
			debugService.addListener(this);
		}
		refreshControls();
	}

	@Override
	public void removeNotify() {
		if (debugService != null) {
			debugService.removeListener(this);
		}
		super.removeNotify();
	}

	@Override
	public void onProgress(IconDetectionDebugService.ProgressSnapshot snapshot) {
		if (snapshot == null) {
			return;
		}
		SwingUtilities.invokeLater(() -> {
			String scanning = snapshot.currentlyScanning();
			String scanningLabel = (scanning != null && !scanning.isBlank()) ? scanning : "<idle>";
			progressLabel.setText(String.format(
					Locale.ROOT,
					"Scanning: %s",
					scanningLabel
			));
			renderTotals(snapshot.greenCount(), snapshot.yellowCount(), snapshot.notFoundCount());
			renderGreenList(snapshot.greenTemplates());
			renderYellowList(snapshot.yellowTemplates());
			renderNotFoundList(snapshot.notFoundTemplates());
			refreshControls();
		});
	}

	@Override
	public void onCompleted(IconDetectionDebugService.RunResult result) {
		SwingUtilities.invokeLater(() -> {
			renderCompleted(result);
			refreshControls();
		});
	}

	private JComponent createMainPanel() {
		JPanel left = createLeftPanel();

		JScrollPane greenScroll = new JScrollPane(greenArea);
		greenScroll.setBorder(UiColorPalette.CARD_BORDER);
		greenScroll.setColumnHeaderView(new JLabel("Green (Found)"));

		JScrollPane yellowScroll = new JScrollPane(yellowArea);
		yellowScroll.setBorder(UiColorPalette.CARD_BORDER);
		yellowScroll.setColumnHeaderView(new JLabel("Yellow"));

		JScrollPane notFoundScroll = new JScrollPane(notFoundArea);
		notFoundScroll.setBorder(UiColorPalette.CARD_BORDER);
		notFoundScroll.setColumnHeaderView(new JLabel("Not Found"));

		JSplitPane lowerSplit = new JSplitPane(JSplitPane.VERTICAL_SPLIT, yellowScroll, notFoundScroll);
		lowerSplit.setOpaque(false);
		lowerSplit.setBorder(null);
		lowerSplit.setResizeWeight(0.50);
		lowerSplit.setDividerLocation(260);

		JSplitPane rightSplit = new JSplitPane(JSplitPane.VERTICAL_SPLIT, greenScroll, lowerSplit);
		rightSplit.setOpaque(false);
		rightSplit.setBorder(null);
		rightSplit.setResizeWeight(0.60);
		rightSplit.setDividerLocation(380);

		JSplitPane split = new JSplitPane(JSplitPane.HORIZONTAL_SPLIT, left, rightSplit);
		split.setOpaque(false);
		split.setBorder(null);
		split.setResizeWeight(0.25);
		split.setDividerLocation(320);
		return split;
	}

	private JPanel createLeftPanel() {
		JPanel panel = new JPanel(new GridBagLayout());
		panel.setOpaque(false);

		GridBagConstraints gbc = new GridBagConstraints();
		gbc.insets = new Insets(4, 4, 4, 4);
		gbc.anchor = GridBagConstraints.WEST;
		gbc.fill = GridBagConstraints.HORIZONTAL;
		gbc.gridx = 0;
		gbc.gridy = 0;
		gbc.gridwidth = 4;

		JLabel title = new JLabel("Icon Detection Debug");
		title.setFont(title.getFont().deriveFont(Font.BOLD, title.getFont().getSize2D() + 2f));
		panel.add(title, gbc);

		gbc.gridy++;
		JLabel help = new JLabel("Validates ability icon templates against the live capture region.");
		Theme theme = ThemeManager.getTheme();
		help.setForeground(theme != null ? theme.getTextMutedColor() : UiColorPalette.DIALOG_MESSAGE_TEXT);
		panel.add(help, gbc);

		gbc.gridy++;
		gbc.gridwidth = 2;
		gbc.gridx = 0;
		panel.add(startButton, gbc);
		gbc.gridx = 2;
		panel.add(stopButton, gbc);

		gbc.gridy++;
		gbc.gridx = 0;
		gbc.gridwidth = 4;
		panel.add(progressLabel, gbc);

		gbc.gridy++;
		gbc.gridwidth = 1;
		gbc.gridx = 0;
		panel.add(new JLabel("Yellow tolerance (%):"), gbc);
		gbc.gridx = 1;
		panel.add(yellowToleranceSpinner, gbc);
		gbc.gridx = 2;
		panel.add(new JLabel("Not found cutoff (%):"), gbc);
		gbc.gridx = 3;
		panel.add(notFoundToleranceSpinner, gbc);

		gbc.gridy++;
		gbc.gridx = 0;
		gbc.gridwidth = 4;
		panel.add(openLogButton, gbc);

		gbc.gridy++;
		gbc.insets = new Insets(12, 4, 2, 4);
		JLabel manualTitle = new JLabel("Manual Template Test");
		manualTitle.setFont(manualTitle.getFont().deriveFont(Font.BOLD));
		panel.add(manualTitle, gbc);

		gbc.gridy++;
		gbc.insets = new Insets(2, 4, 2, 4);
		gbc.gridwidth = 3;
		gbc.gridx = 0;
		JPanel manualField = ThemedTextBoxes.wrap(manualTemplateField, TextBoxStyle.DEFAULT);
		panel.add(manualField, gbc);
		gbc.gridx = 3;
		gbc.gridwidth = 1;
		panel.add(manualTestButton, gbc);

		gbc.gridy++;
		gbc.gridx = 0;
		gbc.gridwidth = 4;
		panel.add(manualResultLabel, gbc);

		gbc.gridy++;
		gbc.insets = new Insets(12, 4, 2, 4);
		JLabel backpackTitle = new JLabel("Backpack Debug");
		backpackTitle.setFont(backpackTitle.getFont().deriveFont(Font.BOLD));
		panel.add(backpackTitle, gbc);

		gbc.gridy++;
		gbc.insets = new Insets(2, 4, 2, 4);
		JLabel backpackHelp = new JLabel("Saves full.png + crops into AppData/RuneSequence/debug/");
		Theme backpackTheme = ThemeManager.getTheme();
		backpackHelp.setForeground(backpackTheme != null ? backpackTheme.getTextMutedColor() : UiColorPalette.DIALOG_MESSAGE_TEXT);
		panel.add(backpackHelp, gbc);

		gbc.gridy++;
		gbc.gridwidth = 1;
		gbc.gridx = 0;
		panel.add(new JLabel("BG tolerance (%):"), gbc);
		gbc.gridx = 1;
		JPanel backpackTol = ThemedTextBoxes.wrap(backpackToleranceField, TextBoxStyle.DEFAULT);
		panel.add(backpackTol, gbc);
		gbc.gridx = 2;
		gbc.gridwidth = 2;
		panel.add(saveBackpackButton, gbc);

		gbc.gridy++;
		gbc.insets = new Insets(12, 4, 2, 4);
		panel.add(totalGreenLabel, gbc);
		gbc.gridy++;
		gbc.insets = new Insets(2, 4, 2, 4);
		panel.add(totalYellowLabel, gbc);
		gbc.gridy++;
		panel.add(totalNotFoundLabel, gbc);

		gbc.gridy++;
		gbc.weighty = 1.0;
		panel.add(Box.createVerticalGlue(), gbc);

		return panel;
	}

	private JLabel createTotalLabel() {
		JLabel label = new JLabel(" ");
		Theme theme = ThemeManager.getTheme();
		label.setForeground(theme != null ? theme.getTextPrimaryColor() : UiColorPalette.UI_TEXT_COLOR);
		label.setOpaque(false);
		return label;
	}

	private JTextArea createOutputArea() {
		JTextArea area = new JTextArea();
		area.setEditable(false);
		area.setLineWrap(false);
		area.setRows(4);
		area.setFont(new Font(Font.MONOSPACED, Font.PLAIN, area.getFont().getSize()));

		Theme theme = ThemeManager.getTheme();
		area.setForeground(theme != null ? theme.getTextPrimaryColor() : UiColorPalette.UI_TEXT_COLOR);
		area.setBackground(UiColorPalette.UI_CARD_BACKGROUND);
		area.setCaretPosition(0);

		return area;
	}

	private void handleStart() {
		if (debugService == null) {
			statusLabel.setForeground(UiColorPalette.TEXT_DANGER);
			statusLabel.setText("Debug service unavailable.");
			return;
		}

		lastLogFile = null;
		greenArea.setText("");
		yellowArea.setText("");
		notFoundArea.setText("");
		renderTotals(0, 0, 0);
		statusLabel.setForeground(UiColorPalette.TEXT_MUTED);
		statusLabel.setText("Running icon detection test...");
		debugService.start(
				((Number) yellowToleranceSpinner.getValue()).intValue(),
				((Number) notFoundToleranceSpinner.getValue()).intValue()
		);
		refreshControls();
	}

	private void handleStop() {
		if (debugService == null) {
			return;
		}
		statusLabel.setForeground(UiColorPalette.TEXT_MUTED);
		statusLabel.setText("Stopping...");
		debugService.stop();
		refreshControls();
	}

	private void handleManualTest() {
		if (debugService == null) {
			manualResultLabel.setForeground(UiColorPalette.TEXT_DANGER);
			manualResultLabel.setText("Debug service unavailable.");
			return;
		}

		String name = manualTemplateField.getText();
		long requestId = manualTestRequestId.incrementAndGet();
		manualResultLabel.setForeground(UiColorPalette.TEXT_MUTED);
		manualResultLabel.setText("Testing...");
		refreshControls();

		SwingWorker<IconDetectionDebugService.ManualTestResult, Void> worker = new SwingWorker<>() {
			@Override
			protected IconDetectionDebugService.ManualTestResult doInBackground() {
				return debugService.runManualTest(name);
			}

			@Override
			protected void done() {
				if (requestId != manualTestRequestId.get()) {
					return;
				}
				try {
					IconDetectionDebugService.ManualTestResult result = get();
					renderManualTestResult(result);
				} catch (Exception e) {
					manualResultLabel.setForeground(UiColorPalette.TEXT_DANGER);
					manualResultLabel.setText("Manual test failed: " + e.getMessage());
				} finally {
					refreshControls();
				}
			}
		};
		worker.execute();
	}

	private void renderManualTestResult(IconDetectionDebugService.ManualTestResult result) {
		if (result == null) {
			manualResultLabel.setForeground(UiColorPalette.TEXT_DANGER);
			manualResultLabel.setText("No result.");
			return;
		}
		if (result.error() != null && !result.error().isBlank()) {
			manualResultLabel.setForeground(UiColorPalette.TEXT_DANGER);
			manualResultLabel.setText(result.error());
			return;
		}

		String location = result.bestLocation() != null
				? String.format(Locale.ROOT, "(%d,%d)", result.bestLocation().x, result.bestLocation().y)
				: "<none>";

		String threshold = String.format(Locale.ROOT, "%.2f%%", result.requiredThreshold() * 100.0d);
		String confidence = String.format(Locale.ROOT, "%.2f%%", result.bestConfidence() * 100.0d);

		manualResultLabel.setForeground(result.found() ? UiColorPalette.TEXT_SUCCESS : UiColorPalette.TEXT_MUTED);
		manualResultLabel.setText(String.format(
				Locale.ROOT,
				"%s required=%s best=%s at=%s",
				result.found() ? "FOUND" : "NOT FOUND",
				threshold,
				confidence,
				location
		));
	}

	private void refreshControls() {
		boolean running = debugService != null && debugService.isRunning();
		startButton.setEnabled(!running);
		stopButton.setEnabled(running);
		yellowToleranceSpinner.setEnabled(!running);
		notFoundToleranceSpinner.setEnabled(!running);
		openLogButton.setEnabled(!running);
		manualTemplateField.setEnabled(!running);
		manualTestButton.setEnabled(!running);
		backpackToleranceField.setEnabled(!running && !savingBackpack);
		saveBackpackButton.setEnabled(!running && !savingBackpack);
	}

	private void renderCompleted(IconDetectionDebugService.RunResult result) {
		if (result == null) {
			statusLabel.setForeground(UiColorPalette.TEXT_DANGER);
			statusLabel.setText("Icon detection test failed.");
			return;
		}

		renderTotals(result.greenCount(),
				result.yellow() != null ? result.yellow().size() : 0,
				result.notFound() != null ? result.notFound().size() : 0);
		renderGreenList(result.greenTemplates());
		renderYellowList(result.yellow() != null
				? result.yellow().stream().map(IconDetectionDebugService.ResultEntry::templateName).toList()
				: java.util.List.of());
		renderNotFoundList(result.notFound() != null
				? result.notFound().stream().map(IconDetectionDebugService.ResultEntry::templateName).toList()
				: java.util.List.of());

		statusLabel.setForeground(UiColorPalette.TEXT_SUCCESS);
		statusLabel.setText(String.format(
				Locale.ROOT,
				"Completed: Green %d/%d in %s. Wrote %s",
				result.greenCount(),
				result.totalTemplates(),
				result.duration(),
				result.logFile() != null ? result.logFile().toString() : "<unknown>"
		));
		lastLogFile = result.logFile() != null ? result.logFile().toFile() : null;
	}

	private void renderTotals(int green, int yellow, int notFound) {
		totalGreenLabel.setText(String.format(Locale.ROOT, "Total green: %d", green));
		totalYellowLabel.setText(String.format(Locale.ROOT, "Total yellow: %d", yellow));
		totalNotFoundLabel.setText(String.format(Locale.ROOT, "Total not found: %d", notFound));
	}

	private void renderGreenList(java.util.List<String> greens) {
		if (greens == null || greens.isEmpty()) {
			greenArea.setText("");
			return;
		}
		StringBuilder sb = new StringBuilder();
		for (String name : greens) {
			if (name == null || name.isBlank()) {
				continue;
			}
			sb.append(name).append('\n');
		}
		greenArea.setText(sb.toString());
		greenArea.setCaretPosition(0);
	}

	private void renderYellowList(java.util.List<String> names) {
		if (names == null || names.isEmpty()) {
			yellowArea.setText("");
			return;
		}
		StringBuilder sb = new StringBuilder();
		for (String name : names) {
			if (name == null || name.isBlank()) {
				continue;
			}
			sb.append(name).append('\n');
		}
		yellowArea.setText(sb.toString());
		yellowArea.setCaretPosition(0);
	}

	private void renderNotFoundList(java.util.List<String> names) {
		if (names == null || names.isEmpty()) {
			notFoundArea.setText("");
			return;
		}
		StringBuilder sb = new StringBuilder();
		for (String name : names) {
			if (name == null || name.isBlank()) {
				continue;
			}
			sb.append(name).append('\n');
		}
		notFoundArea.setText(sb.toString());
		notFoundArea.setCaretPosition(0);
	}

	private void handleOpenLog() {
		File logFile = lastLogFile;
		if (logFile == null && debugService != null && debugService.getLastLogFile() != null) {
			logFile = debugService.getLastLogFile().toFile();
		}
		if (logFile == null || !logFile.exists()) {
			ThemedDialogs.showMessageDialog(this, DIALOG_TITLE, "icon_Detection.log not found yet. Run the test first.");
			return;
		}
		if (!Desktop.isDesktopSupported()) {
			ThemedDialogs.showMessageDialog(this, DIALOG_TITLE, "Opening files is not supported on this platform.");
			return;
		}
		try {
			Desktop.getDesktop().open(logFile);
		} catch (Exception ex) {
			ThemedDialogs.showMessageDialog(this, DIALOG_TITLE, "Failed to open log file: " + ex.getMessage());
		}
	}

	private void handleSaveBackpack() {
		Path configDir = configManager != null ? configManager.getConfigDir() : null;
		if (configDir == null) {
			statusLabel.setForeground(UiColorPalette.TEXT_DANGER);
			statusLabel.setText("Config directory unavailable.");
			return;
		}

		double tolerancePercent;
		try {
			tolerancePercent = Double.parseDouble(backpackToleranceField.getText().trim());
		} catch (Exception e) {
			ThemedDialogs.showMessageDialog(this, DIALOG_TITLE, "Backpack tolerance must be a number (0-100).");
			return;
		}

		if (Double.isNaN(tolerancePercent) || Double.isInfinite(tolerancePercent) || tolerancePercent < 0.0d || tolerancePercent > 100.0d) {
			ThemedDialogs.showMessageDialog(this, DIALOG_TITLE, "Backpack tolerance must be between 0 and 100.");
			return;
		}

		long requestId = backpackSaveRequestId.incrementAndGet();
		savingBackpack = true;
		statusLabel.setForeground(UiColorPalette.TEXT_MUTED);
		statusLabel.setText("Saving backpack...");
		refreshControls();

		SwingWorker<BackpackSaveDebugService.SaveResult, Void> worker = new SwingWorker<>() {
			@Override
			protected BackpackSaveDebugService.SaveResult doInBackground() throws Exception {
				return BackpackSaveDebugService.save(
						configDir.resolve("debug"),
						debugService != null ? debugService::captureCurrentFrame : null,
						tolerancePercent
				);
			}

			@Override
			protected void done() {
				if (requestId != backpackSaveRequestId.get()) {
					return;
				}
				savingBackpack = false;
				try {
					BackpackSaveDebugService.SaveResult result = get();
					String dialogMessage = "Saved to:\n" + result.runDir();
					if (result.warning() != null && !result.warning().isBlank()) {
						dialogMessage = dialogMessage + "\n\n" + result.warning();
						statusLabel.setForeground(UiColorPalette.TOAST_WARNING_ACCENT);
						statusLabel.setText(result.warning() + " Folder: " + result.runDir());
					} else {
						statusLabel.setForeground(UiColorPalette.TEXT_SUCCESS);
						statusLabel.setText("Saved backpack to: " + result.runDir());
					}
					ThemedDialogs.showMessageDialog(
							DebugSettingsPanel.this,
							DIALOG_TITLE,
							dialogMessage
					);
					openBackpackRunDir(result.runDir());
				} catch (Exception e) {
					String message = e.getMessage() != null ? e.getMessage() : e.toString();
					statusLabel.setForeground(UiColorPalette.TEXT_DANGER);
					statusLabel.setText("Save backpack failed: " + message);
					ThemedDialogs.showMessageDialog(DebugSettingsPanel.this, DIALOG_TITLE, "Save backpack failed:\n" + message);
				} finally {
					refreshControls();
				}
			}
		};
		worker.execute();
	}

	private void openBackpackRunDir(Path runDir) {
		if (runDir == null) {
			return;
		}
		File runFolder = runDir.toFile();
		if (!runFolder.exists()) {
			ThemedDialogs.showMessageDialog(this, DIALOG_TITLE, "Backpack folder not found:\n" + runDir);
			return;
		}
		if (!Desktop.isDesktopSupported()) {
			ThemedDialogs.showMessageDialog(this, DIALOG_TITLE, "Opening folders is not supported on this platform.");
			return;
		}
		try {
			Desktop.getDesktop().open(runFolder);
		} catch (Exception ex) {
			ThemedDialogs.showMessageDialog(this, DIALOG_TITLE, "Failed to open backpack folder:\n" + ex.getMessage());
		}
	}
}
