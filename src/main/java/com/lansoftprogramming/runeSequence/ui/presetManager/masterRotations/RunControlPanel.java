package com.lansoftprogramming.runeSequence.ui.presetManager.masterRotations;

import com.lansoftprogramming.runeSequence.ui.theme.UiColorPalette;

import javax.swing.*;
import java.awt.*;
import java.awt.event.ActionListener;

/**
 * Encapsulates the run control buttons and status label for the master panel.
 */
class RunControlPanel extends JPanel {
	private final JButton startButton;
	private final JButton pauseButton;
	private final JButton restartButton;
	private final JLabel statusLabel;

	private final Color defaultStartBg;
	private final Color defaultPauseBg;
	private final Color highlightStartBg;
	private final Color highlightPauseBg;
	private final Color defaultStartFg;
	private final Color defaultPauseFg;
	private final Color highlightText;

	RunControlPanel() {
		setLayout(new BorderLayout(5, 2));

		startButton = new JButton("Arm");
		pauseButton = new JButton("Pause");
		restartButton = new JButton("Restart");
		statusLabel = new JLabel("Status: Ready");

		defaultStartBg = startButton.getBackground();
		defaultPauseBg = pauseButton.getBackground();
		defaultStartFg = startButton.getForeground();
		defaultPauseFg = pauseButton.getForeground();
		highlightStartBg = UiColorPalette.SELECTION_ACTIVE_FILL;
		highlightPauseBg = UiColorPalette.TOAST_WARNING_ACCENT;
		highlightText = UiColorPalette.TEXT_INVERSE;

		JPanel buttonRow = new JPanel(new FlowLayout(FlowLayout.LEFT, 5, 0));
		buttonRow.add(startButton);
		buttonRow.add(pauseButton);
		buttonRow.add(restartButton);

		JPanel statusRow = new JPanel(new FlowLayout(FlowLayout.LEFT, 0, 0));
		statusRow.add(statusLabel);

		add(statusRow, BorderLayout.NORTH);
		add(buttonRow, BorderLayout.CENTER);
	}

	void addStartListener(ActionListener listener) {
		if (listener != null) {
			startButton.addActionListener(listener);
		}
	}

	void addPauseListener(ActionListener listener) {
		if (listener != null) {
			pauseButton.addActionListener(listener);
		}
	}

	void addRestartListener(ActionListener listener) {
		if (listener != null) {
			restartButton.addActionListener(listener);
		}
	}

	void setStartButtonState(String label, boolean enabled, boolean highlighted) {
		startButton.setText(label != null ? label : "");
		startButton.setEnabled(enabled);
		applyHighlight(startButton, highlighted, highlightStartBg, defaultStartBg, defaultStartFg);
	}

	void setPauseButtonState(boolean enabled, boolean highlighted) {
		pauseButton.setEnabled(enabled);
		applyHighlight(pauseButton, highlighted, highlightPauseBg, defaultPauseBg, defaultPauseFg);
	}

	void setRestartButtonEnabled(boolean enabled) {
		restartButton.setEnabled(enabled);
		restartButton.setVisible(true);
	}

	void setStatusText(String text) {
		statusLabel.setText(text != null ? text : "");
	}

	private void applyHighlight(JButton button, boolean highlighted, Color highlightBg, Color defaultBg, Color defaultFg) {
		button.setOpaque(true);
		if (highlighted) {
			button.setBackground(highlightBg);
			button.setForeground(highlightText);
		} else {
			button.setBackground(defaultBg);
			button.setForeground(defaultFg);
		}
	}
}
