package com.lansoftprogramming.runeSequence.ui.presetManager.detail;

import com.lansoftprogramming.runeSequence.infrastructure.config.RotationConfig;
import com.lansoftprogramming.runeSequence.ui.overlay.toast.ToastClient;
import com.lansoftprogramming.runeSequence.ui.presetManager.model.SequenceElement;
import com.lansoftprogramming.runeSequence.ui.shared.model.AbilityItem;
import com.lansoftprogramming.runeSequence.ui.theme.UiColorPalette;

import javax.swing.*;
import javax.swing.border.EmptyBorder;
import java.awt.*;
import java.awt.datatransfer.Clipboard;
import java.awt.datatransfer.DataFlavor;
import java.awt.datatransfer.UnsupportedFlavorException;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.awt.image.BufferedImage;
import java.io.IOException;
import java.util.List;

public class SequenceDetailPanel extends JPanel implements SequenceDetailPresenter.View {
	private final JTextField sequenceNameField;
	private final JPanel insertButton;
	private final JButton settingsButton;
	private final JButton saveButton;
	private final JButton discardButton;
	private final AbilityFlowView abilityFlowView;
	private final SequenceDetailPresenter presenter;
	private final SequenceDetailService detailService;
	private final ImageIcon insertIcon;
	private ToastClient toastClient;

	public SequenceDetailPanel(SequenceDetailService detailService) {
		this.detailService = detailService;
		setLayout(new BorderLayout());
		setBorder(new EmptyBorder(10, 10, 10, 10));

		sequenceNameField = new JTextField();
		insertIcon = createInsertIcon();
		insertButton = createInsertButton();
		settingsButton = new JButton("Settings");
		saveButton = new JButton("Save");
		discardButton = new JButton("Discard");
		abilityFlowView = new AbilityFlowView(detailService);
		presenter = new SequenceDetailPresenter(detailService, abilityFlowView, this);

		alignInsertButtonSize();
		layoutComponents();
		registerEventHandlers();
	}

	private void layoutComponents() {
		JPanel headerPanel = createHeaderPanel();
		JScrollPane contentScrollPane = createContentScrollPane();

		add(headerPanel, BorderLayout.NORTH);
		add(contentScrollPane, BorderLayout.CENTER);
	}

	private JPanel createHeaderPanel() {
		JPanel headerPanel = new JPanel(new BorderLayout(10, 0));

		JPanel namePanel = new JPanel(new BorderLayout(5, 0));
		namePanel.add(new JLabel("Sequence Name:"), BorderLayout.WEST);
		namePanel.add(sequenceNameField, BorderLayout.CENTER);

		JPanel buttonPanel = new JPanel(new FlowLayout(FlowLayout.RIGHT, 5, 0));
		buttonPanel.add(insertButton);
		buttonPanel.add(settingsButton);
		buttonPanel.add(discardButton);
		buttonPanel.add(saveButton);

		headerPanel.add(namePanel, BorderLayout.CENTER);
		headerPanel.add(buttonPanel, BorderLayout.EAST);

		return headerPanel;
	}

	private JPanel createInsertButton() {
		JPanel panel = new JPanel();
		panel.setName("insertClipboardButton");
		panel.setLayout(new BoxLayout(panel, BoxLayout.Y_AXIS));
		Color baseColor = UIManager.getColor("Button.background");
		if (baseColor == null) {
			baseColor = UiColorPalette.UI_CARD_BACKGROUND;
		}
		Color hoverColor = baseColor.brighter();
		panel.setOpaque(true);
		panel.setBackground(baseColor);
		panel.setBorder(BorderFactory.createCompoundBorder(
				BorderFactory.createLineBorder(new Color(180, 180, 180)),
				new EmptyBorder(2, 2, 2, 2)
		));
		panel.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));

//		JLabel iconLabel = new JLabel(insertIcon);
		JLabel iconLabel = new JLabel("⋮⋮ ▧▧");
		iconLabel.setAlignmentX(Component.CENTER_ALIGNMENT);
		panel.add(Box.createVerticalGlue());
		panel.add(iconLabel);
		panel.add(Box.createVerticalGlue());

		panel.setToolTipText("Drag to insert clipboard rotation");
		Color finalBaseColor = baseColor;
		Color finalHoverColor = hoverColor;
		panel.addMouseListener(new MouseAdapter() {
			@Override
			public void mouseEntered(MouseEvent e) {
				panel.setBackground(finalHoverColor);
			}

			@Override
			public void mouseExited(MouseEvent e) {
				panel.setBackground(finalBaseColor);
			}
		});
		return panel;
	}

	private void alignInsertButtonSize() {
		int referenceHeight = Math.max(
				saveButton.getPreferredSize().height,
				Math.max(discardButton.getPreferredSize().height, settingsButton.getPreferredSize().height)
		);
	}

	private JScrollPane createContentScrollPane() {
		JScrollPane scrollPane = new JScrollPane(abilityFlowView);
		scrollPane.setBorder(BorderFactory.createLineBorder(Color.LIGHT_GRAY));
		scrollPane.setHorizontalScrollBarPolicy(JScrollPane.HORIZONTAL_SCROLLBAR_NEVER);
		scrollPane.setVerticalScrollBarPolicy(JScrollPane.VERTICAL_SCROLLBAR_AS_NEEDED);
		scrollPane.getVerticalScrollBar().setUnitIncrement(5);
		return scrollPane;
	}

	private void registerEventHandlers() {
		saveButton.addActionListener(e -> presenter.saveSequence());
		discardButton.addActionListener(e -> presenter.discardChanges());
		registerInsertDragHandler();
	}

	public void discardChanges() {
		presenter.discardChanges();
	}

	public void startPaletteDrag(AbilityItem item, JPanel card, Point startPoint) {
		presenter.startPaletteDrag(item, card, startPoint);
	}

	public void loadSequence(RotationConfig.PresetData presetData) {
		presenter.loadSequence(presetData);
	}

	public void loadSequence(String presetId, RotationConfig.PresetData presetData) {
		presenter.loadSequence(presetId, presetData);
	}

	public void startNewSequence(String presetId, RotationConfig.PresetData presetData) {
		loadSequence(presetId, presetData);
		highlightSequenceNameField();
	}

	public void setToastClient(ToastClient toastClient) {
		this.toastClient = toastClient;
	}

	public void highlightSequenceNameField() {
		SwingUtilities.invokeLater(() -> {
			sequenceNameField.requestFocusInWindow();
			sequenceNameField.selectAll();
		});
	}

	public void clear() {
		presenter.clear();
	}

	public void addSaveListener(SaveListener listener) {
		presenter.addSaveListener(listener);
	}

	public void saveSequence() {
		presenter.saveSequence();
	}

	public boolean hasUnsavedChanges() {
		return presenter.hasUnsavedChanges();
	}

	@Override
	public void setSequenceName(String name) {
		sequenceNameField.setText(name);
	}

	@Override
	public String getSequenceName() {
		return sequenceNameField.getText();
	}

	@Override
	public void showSaveDialog(String message, int messageType) {
		if (toastClient != null) {
			switch (messageType) {
				case JOptionPane.ERROR_MESSAGE -> toastClient.error(message);
				case JOptionPane.WARNING_MESSAGE -> toastClient.warn(message);
				case JOptionPane.INFORMATION_MESSAGE -> toastClient.success(message);
				default -> toastClient.info(message);
			}
			return;
		}

		JOptionPane.showMessageDialog(
			this,
			message,
			"Save Sequence",
			messageType
		);
	}


	@Override
	public JComponent asComponent() {
		return this;
	}

	public interface SaveListener {
		void onSequenceSaved(SequenceDetailService.SaveResult result);
	}

	private void registerInsertDragHandler() {
		insertButton.addMouseListener(new MouseAdapter() {
			@Override
			public void mousePressed(MouseEvent e) {
				if (!SwingUtilities.isLeftMouseButton(e)) {
					return;
				}
				startClipboardInsertDrag(e);
			}
		});
	}

	private void startClipboardInsertDrag(MouseEvent triggerEvent) {
		String expression = readClipboardContent();
		if (expression == null || expression.trim().isEmpty()) {
			return;
		}

		// Parse directly; the service handles validation and returns empty list on failure
		List<SequenceElement> elements = detailService.parseSequenceExpression(expression);
		if (elements == null || elements.isEmpty()) {
			showToast("Clipboard does not contain a valid rotation.", true);
			return;
		}

		ClipboardInsertItem item = new ClipboardInsertItem(
				"clipboard-rotation",
				"Clipboard",
				insertIcon,
				elements,
				expression
		);

		abilityFlowView.startPaletteDrag(item, insertButton, triggerEvent.getPoint());
	}

	private ImageIcon createInsertIcon() {
		int size = 18;
		int padding = 3;
		int cornerRadius = 8;
		BufferedImage image = new BufferedImage(size, size, BufferedImage.TYPE_INT_ARGB);
		Graphics2D g2d = image.createGraphics();
		g2d.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
		g2d.setColor(new Color(66, 133, 244));
		g2d.fillRoundRect(padding, padding, size - (padding * 2), size - (padding * 2), cornerRadius, cornerRadius);
		g2d.setStroke(new BasicStroke(2f));
		g2d.setColor(Color.WHITE);
		int mid = size / 2;
		int arm = mid - padding - 1;
		g2d.drawLine(mid, mid - arm, mid, mid + arm);
		g2d.drawLine(mid - arm, mid, mid + arm, mid);
		g2d.dispose();
		return new ImageIcon(image);
	}

	private String readClipboardContent() {
		try {
			Clipboard clipboard = Toolkit.getDefaultToolkit().getSystemClipboard();
			if (clipboard.isDataFlavorAvailable(DataFlavor.stringFlavor)) {
				return (String) clipboard.getData(DataFlavor.stringFlavor);
			}
			showToast("Clipboard is empty.", false);
			return null;
		} catch (UnsupportedFlavorException | IOException | IllegalStateException e) {
			showToast("Could not read from clipboard.", true);
			return null;
		}
	}

	private void showToast(String message, boolean error) {
		if (toastClient == null) {
			Toolkit.getDefaultToolkit().beep();
			return;
		}
		if (error) {
			toastClient.error(message);
		} else {
			toastClient.info(message);
		}
	}
}
