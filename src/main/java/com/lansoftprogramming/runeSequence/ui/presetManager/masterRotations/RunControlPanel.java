package com.lansoftprogramming.runeSequence.ui.presetManager.masterRotations;

import com.lansoftprogramming.runeSequence.ui.theme.*;

import javax.swing.*;
import javax.swing.border.EmptyBorder;
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
	private final JPanel operationalHeader;

	private SequenceRunPresenter.StartAccent currentStartAccent = SequenceRunPresenter.StartAccent.NEUTRAL;
	private boolean pauseHighlighted;

	RunControlPanel() {
		setOpaque(false);
		setLayout(new BorderLayout());

		startButton = new JButton("Start");
		pauseButton = new JButton("Pause");
		restartButton = new JButton("Restart");
		ThemedButtons.apply(startButton, ButtonStyle.DEFAULT);
		ThemedButtons.apply(pauseButton, ButtonStyle.DEFAULT);
		ThemedButtons.apply(restartButton, ButtonStyle.DEFAULT);
		startButton.setFocusable(false);
		pauseButton.setFocusable(false);
		restartButton.setFocusable(false);

		statusLabel = new JLabel("Status: Ready");

		JPanel buttonRow = new JPanel(new FlowLayout(FlowLayout.LEFT, 5, 0));
		buttonRow.add(startButton);
		buttonRow.add(pauseButton);
		buttonRow.add(restartButton);

		operationalHeader = new JPanel(new BorderLayout(10, 0));
		operationalHeader.setOpaque(false);
		operationalHeader.setBorder(new EmptyBorder(3, 4, 3, 4));
		buttonRow.setOpaque(false);
		statusLabel.setOpaque(false);
		statusLabel.setBorder(new EmptyBorder(0, 2, 0, 0));
		statusLabel.setFont(statusLabel.getFont().deriveFont(Font.BOLD));
		statusLabel.setHorizontalAlignment(SwingConstants.LEFT);
		operationalHeader.add(buttonRow, BorderLayout.WEST);
		operationalHeader.add(statusLabel, BorderLayout.CENTER);
		updateOperationalHeaderStyle();

		add(operationalHeader, BorderLayout.CENTER);
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

	void setPauseButtonState(boolean enabled, boolean highlighted) {
		pauseHighlighted = highlighted;
		pauseButton.setEnabled(enabled);
		updateOperationalHeaderStyle();
		repaint();
	}

	void setRestartButtonEnabled(boolean enabled) {
		restartButton.setEnabled(enabled);
		restartButton.setVisible(true);
		repaint();
	}

	void setStatusText(String text) {
		statusLabel.setText(text != null ? text : "");
		repaint();
	}

	void setStartButtonState(String label, boolean enabled, SequenceRunPresenter.StartAccent accent) {
		startButton.setText(label != null ? label : "");
		startButton.setEnabled(enabled);
		currentStartAccent = accent != null ? accent : SequenceRunPresenter.StartAccent.NEUTRAL;
		updateOperationalHeaderStyle();
		repaint();
	}

	private void updateOperationalHeaderStyle() {
		Theme theme = ThemeManager.getTheme();
		statusLabel.setForeground(theme != null ? theme.getTextPrimaryColor() : UiColorPalette.UI_TEXT_COLOR);
	}
}
