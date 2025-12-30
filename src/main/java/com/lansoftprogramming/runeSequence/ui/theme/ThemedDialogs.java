package com.lansoftprogramming.runeSequence.ui.theme;

import javax.swing.*;
import javax.swing.border.EmptyBorder;
import java.awt.*;
import java.awt.event.ActionEvent;
import java.util.Objects;

public final class ThemedDialogs {

	public enum Result {
		OK,
		CANCEL,
		YES,
		NO,
		CLOSED
	}

	private static final float TITLE_FONT_SIZE = 40f;

	private ThemedDialogs() {
	}

	public static void showMessageDialog(Component parent, String title, String message) {
		showDialog(parent, title, createMessageComponent(message), new DialogButton[]{
				new DialogButton("OK", Result.OK)
		});
	}

	public static boolean showConfirmDialog(Component parent, String title, String message) {
		Result result = showDialog(parent, title, createMessageComponent(message), new DialogButton[]{
				new DialogButton("YES", Result.YES),
				new DialogButton("NO", Result.NO)
		});
		return result == Result.YES;
	}

	public static Result showOkCancelDialog(Component parent, String title, JComponent content) {
		return showDialog(parent, title, content, new DialogButton[]{
				new DialogButton("OK", Result.OK),
				new DialogButton("CANCEL", Result.CANCEL)
		});
	}

	public static Result showDialog(Component parent, String title, JComponent content, DialogButton[] buttons) {
		Objects.requireNonNull(buttons, "buttons");
		Window owner = parent != null ? SwingUtilities.getWindowAncestor(parent) : null;
		String dialogTitle = title != null ? title : "";
		JDialog dialog = new JDialog(owner, dialogTitle, Dialog.ModalityType.APPLICATION_MODAL);
		ThemedWindowDecorations.applyTitleBar(dialog);

		ThemedPanel root = new ThemedPanel(PanelStyle.DIALOG, new BorderLayout());
		root.setOpaque(false);

		JPanel body = new JPanel();
		body.setOpaque(false);
		body.setLayout(new BoxLayout(body, BoxLayout.Y_AXIS));
		body.setBorder(new EmptyBorder(18, 22, 18, 22));

		JLabel titleLabel = new JLabel(dialogTitle);
		titleLabel.setAlignmentX(Component.CENTER_ALIGNMENT);
		titleLabel.setHorizontalAlignment(SwingConstants.CENTER);
		titleLabel.setForeground(UiColorPalette.DIALOG_TITLE_GOLD);
		titleLabel.setFont(ThemeManager.getTheme().getDialogTitleFont(TITLE_FONT_SIZE));
		body.add(titleLabel);

		body.add(Box.createVerticalStrut(4));
		JLabel divider = createDividerLabel();
		if (divider != null) {
			divider.setAlignmentX(Component.CENTER_ALIGNMENT);
			body.add(divider);
			body.add(Box.createVerticalStrut(8));
		} else {
			body.add(Box.createVerticalStrut(12));
		}

		if (content != null) {
			content.setAlignmentX(Component.CENTER_ALIGNMENT);
			content.setOpaque(false);
			body.add(content);
			body.add(Box.createVerticalStrut(16));
		} else {
			body.add(Box.createVerticalStrut(6));
		}

		JPanel buttonRow = new JPanel(new FlowLayout(FlowLayout.CENTER, 16, 0));
		buttonRow.setOpaque(false);
		ResultHolder holder = new ResultHolder(Result.CLOSED);
		JButton defaultButton = null;
		for (DialogButton button : buttons) {
			JButton themedButton = ThemedButtons.create(button.label());
			themedButton.addActionListener(event -> {
				holder.result = button.result();
				dialog.dispose();
			});
			buttonRow.add(themedButton);
			if (defaultButton == null) {
				defaultButton = themedButton;
			}
		}
		body.add(buttonRow);

		root.add(body, BorderLayout.CENTER);
		dialog.setContentPane(root);
		dialog.setResizable(false);
		dialog.pack();
		dialog.setLocationRelativeTo(owner);
		if (defaultButton != null) {
			dialog.getRootPane().setDefaultButton(defaultButton);
		}
		dialog.getRootPane().registerKeyboardAction(
				(ActionEvent e) -> {
					holder.result = Result.CANCEL;
					dialog.dispose();
				},
				KeyStroke.getKeyStroke("ESCAPE"),
				JComponent.WHEN_IN_FOCUSED_WINDOW
		);
		dialog.setVisible(true);
		return holder.result;
	}

	private static JComponent createMessageComponent(String message) {
		if (message == null || message.isBlank()) {
			return null;
		}
		String html = "<html><div style='text-align:center;'>" + escapeHtml(message).replace("\n", "<br>") + "</div></html>";
		JLabel label = new JLabel(html);
		label.setHorizontalAlignment(SwingConstants.CENTER);
		label.setForeground(UiColorPalette.DIALOG_MESSAGE_TEXT);
		return label;
	}

	private static JLabel createDividerLabel() {
		ImageIcon icon = null;
		try {
			Image image = ThemeManager.getTheme().getDialogDividerImage(DialogStyle.DEFAULT);
			if (image != null) {
				icon = new ImageIcon(image);
			}
		} catch (RuntimeException ignored) {
			return null;
		}
		if (icon == null) {
			return null;
		}
		return new JLabel(icon);
	}

	private static String escapeHtml(String message) {
		return message
				.replace("&", "&amp;")
				.replace("<", "&lt;")
				.replace(">", "&gt;")
				.replace("\"", "&quot;")
				.replace("'", "&#39;");
	}

	public record DialogButton(String label, Result result) {
	}

	private static final class ResultHolder {
		private Result result;

		private ResultHolder(Result result) {
			this.result = result;
		}
	}
}
