package com.lansoftprogramming.runeSequence.ui.presetManager.masterRotations;

import com.lansoftprogramming.runeSequence.ui.theme.UiColorPalette;

import javax.swing.*;
import javax.swing.border.Border;
import javax.swing.border.CompoundBorder;
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

	private final Color defaultStartBg;
	private final Color defaultPauseBg;
	private final Color highlightPauseBg;
	private final Color defaultStartFg;
	private final Color defaultPauseFg;
	private final Color highlightText;
	private final Color startedAccent;
	private final Color armedAccent;
	private final Color headerNeutralBg;
	private final Color headerStartedBg;
	private final Color headerArmedBg;
	private final Color headerPausedBg;
	private final Border operationalHeaderBorder;

	private SequenceRunPresenter.StartAccent currentStartAccent = SequenceRunPresenter.StartAccent.NEUTRAL;
	private boolean pauseHighlighted;

	RunControlPanel() {
		setOpaque(false);
		setLayout(new BorderLayout());

		startButton = new JButton("Arm");
		pauseButton = new JButton("Pause");
		restartButton = new JButton("Restart");
		statusLabel = new JLabel("Status: Ready");
		operationalHeader = new JPanel(new BorderLayout(10, 0));

		defaultStartBg = startButton.getBackground();
		defaultPauseBg = pauseButton.getBackground();
		defaultStartFg = startButton.getForeground();
		defaultPauseFg = pauseButton.getForeground();
		highlightPauseBg = UiColorPalette.TOAST_WARNING_ACCENT;
		highlightText = UiColorPalette.TEXT_INVERSE;
		startedAccent = UiColorPalette.RUN_STATE_STARTED_ACCENT;
		armedAccent = UiColorPalette.RUN_STATE_ARMED_ACCENT;
		headerNeutralBg = UiColorPalette.RUN_HEADER_NEUTRAL_BACKGROUND;
		headerStartedBg = UiColorPalette.RUN_HEADER_STARTED_BACKGROUND;
		headerArmedBg = UiColorPalette.RUN_HEADER_ARMED_BACKGROUND;
		headerPausedBg = UiColorPalette.RUN_HEADER_PAUSED_BACKGROUND;
		operationalHeaderBorder = new CompoundBorder(
				BorderFactory.createMatteBorder(1, 0, 0, 0, UiColorPalette.UI_CARD_BORDER_SUBTLE),
				new EmptyBorder(3, 4, 3, 4)
		);

		JPanel buttonRow = new JPanel(new FlowLayout(FlowLayout.LEFT, 5, 0));
		buttonRow.add(startButton);
		buttonRow.add(pauseButton);
		buttonRow.add(restartButton);

		operationalHeader.setOpaque(true);
		operationalHeader.setBorder(operationalHeaderBorder);
		buttonRow.setOpaque(false);
		statusLabel.setOpaque(false);
		statusLabel.setBorder(new EmptyBorder(0, 2, 0, 0));
		statusLabel.setFont(statusLabel.getFont().deriveFont(Font.BOLD));
		statusLabel.setHorizontalAlignment(SwingConstants.LEFT);
		operationalHeader.add(buttonRow, BorderLayout.WEST);
		operationalHeader.add(statusLabel, BorderLayout.CENTER);
		updateOperationalHeaderBackground();

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
		applyHighlight(pauseButton, highlighted, highlightPauseBg, defaultPauseBg, defaultPauseFg);
		updateOperationalHeaderBackground();
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
		applyAccent(startButton, currentStartAccent);
		updateOperationalHeaderBackground();
		repaint();
	}

	private void updateOperationalHeaderBackground() {
		Color nextBackground;
		if (pauseHighlighted) {
			nextBackground = headerPausedBg;
		} else if (currentStartAccent == SequenceRunPresenter.StartAccent.STARTED) {
			nextBackground = headerStartedBg;
		} else if (currentStartAccent == SequenceRunPresenter.StartAccent.ARMED) {
			nextBackground = headerArmedBg;
		} else {
			nextBackground = headerNeutralBg;
		}

		if (!nextBackground.equals(operationalHeader.getBackground())) {
			operationalHeader.setBackground(nextBackground);
			operationalHeader.repaint();
		}

		Color labelColor = computeReadableForeground(nextBackground);
		if (labelColor != null) {
			statusLabel.setForeground(labelColor);
		}
	}

	private void applyAccent(JButton button, SequenceRunPresenter.StartAccent accent) {
		if (accent == null || accent == SequenceRunPresenter.StartAccent.NEUTRAL) {
			button.setOpaque(true);
			button.setBackground(defaultStartBg);
			button.setForeground(defaultStartFg);
			return;
		}

		button.setOpaque(true);
		button.setForeground(highlightText);
		if (accent == SequenceRunPresenter.StartAccent.STARTED) {
			button.setBackground(startedAccent);
		} else {
			button.setBackground(armedAccent);
		}
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

	private static Color computeReadableForeground(Color background) {
		if (background == null) {
			return null;
		}
		// Perceived luminance; pick black/white for contrast (portable across LAFs/OSes).
		double r = background.getRed() / 255.0;
		double g = background.getGreen() / 255.0;
		double b = background.getBlue() / 255.0;
		double luminance = 0.2126 * r + 0.7152 * g + 0.0722 * b;
		return luminance > 0.56 ? Color.BLACK : Color.WHITE;
	}
}
