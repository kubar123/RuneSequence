package com.lansoftprogramming.runeSequence.ui.presetManager.detail;

import com.lansoftprogramming.runeSequence.infrastructure.config.RotationConfig;
import com.lansoftprogramming.runeSequence.ui.notification.NotificationService;
import com.lansoftprogramming.runeSequence.ui.presetManager.model.SequenceElement;
import com.lansoftprogramming.runeSequence.ui.presetManager.service.AbilityOverridesService;
import com.lansoftprogramming.runeSequence.ui.shared.component.HoverGlowContainerPanel;
import com.lansoftprogramming.runeSequence.ui.shared.cursor.TextCursorSupport;
import com.lansoftprogramming.runeSequence.ui.shared.icons.IconLoader;
import com.lansoftprogramming.runeSequence.ui.shared.icons.ResourceIcons;
import com.lansoftprogramming.runeSequence.ui.shared.model.AbilityItem;
import com.lansoftprogramming.runeSequence.ui.shared.util.ClipboardStrings;
import com.lansoftprogramming.runeSequence.ui.theme.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.swing.*;
import javax.swing.border.EmptyBorder;
import javax.swing.event.DocumentEvent;
import javax.swing.event.DocumentListener;
import java.awt.*;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.awt.image.BufferedImage;
import java.util.List;

public class SequenceDetailPanel extends ThemedPanel implements SequenceDetailPresenter.View {
	private static final Logger logger = LoggerFactory.getLogger(SequenceDetailPanel.class);

	private final JTextField sequenceNameField;
	private final ThemedTextBoxPanel sequenceNamePanel;
	private final JPanel tooltipButton;
	private final JPanel insertButton;
	private final JButton settingsButton;
	private final JButton saveButton;
	private final JButton discardButton;
	private final AbilityFlowView abilityFlowView;
	private final SequenceDetailPresenter presenter;
	private final SequenceDetailService detailService;
	private final AbilityOverridesService overridesService;
	private final ImageIcon insertIcon;
	private final ImageIcon tooltipIcon;
	private final NotificationService notifications;
	private final Timer dirtyStateTimer;
	private Boolean lastDirtyState;
	private transient TextCursorSupport.WindowTextCursorResolver textCursorResolver;

	public SequenceDetailPanel(SequenceDetailService detailService,
	                           AbilityOverridesService overridesService,
	                           NotificationService notifications) {
		super(PanelStyle.DETAIL, new BorderLayout());
		this.detailService = detailService;
		this.overridesService = overridesService;
		this.notifications = notifications;

		sequenceNameField = new JTextField();
		TextCursorSupport.installTextCursor(sequenceNameField);
		sequenceNamePanel = ThemedTextBoxes.wrap(sequenceNameField);
		TextCursorSupport.installTextCursor(sequenceNamePanel);
		ImageIcon loadedInsertIcon = IconLoader.loadScaledOrNull(ResourceIcons.PRESET_MANAGER_INSERT_CLIPBOARD_DARK, 18, 18);
		insertIcon = loadedInsertIcon != null ? loadedInsertIcon : createInsertIcon();
		ImageIcon loadedTooltipIcon = IconLoader.loadScaledOrNull(ResourceIcons.PRESET_MANAGER_TEXT_ADD_DARK, 18, 18);
		tooltipIcon = loadedTooltipIcon != null ? loadedTooltipIcon : createTooltipIcon();
		insertButton = createInsertButton();
		tooltipButton = createTooltipButton();
		settingsButton = new JButton("Settings");
		saveButton = new JButton("Save");
		discardButton = new JButton("Discard");
		ThemedButtons.apply(settingsButton, ButtonStyle.DEFAULT);
		ThemedButtons.apply(saveButton, ButtonStyle.DEFAULT);
		ThemedButtons.apply(discardButton, ButtonStyle.DEFAULT);
		abilityFlowView = new AbilityFlowView(detailService);
		presenter = new SequenceDetailPresenter(detailService, overridesService, abilityFlowView, this, notifications);

		applyButtonStyles();
		layoutComponents();
		registerEventHandlers();
		dirtyStateTimer = new Timer(250, e -> refreshDirtyState());
		dirtyStateTimer.setRepeats(true);
		refreshDirtyState();
	}

	@Override
	public void addNotify() {
		super.addNotify();
		// Ensure look-and-feel/UI updates don't override the text cursor.
		TextCursorSupport.installTextCursor(sequenceNameField);
		TextCursorSupport.installTextCursor(sequenceNamePanel);
		installTextCursorResolver();
		dirtyStateTimer.start();
	}

	@Override
	public void removeNotify() {
		dirtyStateTimer.stop();
		uninstallTextCursorResolver();
		super.removeNotify();
	}

	private void layoutComponents() {
		JPanel headerPanel = createHeaderPanel();
		JScrollPane contentScrollPane = createContentScrollPane();

		add(headerPanel, BorderLayout.NORTH);
		add(contentScrollPane, BorderLayout.CENTER);
	}

	private JPanel createHeaderPanel() {
		ThemedPanel headerPanel = new ThemedPanel(PanelStyle.DETAIL_HEADER, new BorderLayout());
		JPanel headerContent = new JPanel(new BorderLayout(10, 0));
		headerContent.setOpaque(false);
		headerContent.setBorder(new EmptyBorder(2, 0, 2, 0));

		JPanel palettePanel = new HoverGlowContainerPanel(
				new FlowLayout(FlowLayout.LEFT, 6, 2),
				component -> component instanceof JComponent jc && ("tooltipPaletteButton".equals(jc.getName()) || "insertClipboardButton".equals(jc.getName()))
		);
		palettePanel.setOpaque(false);
		palettePanel.add(tooltipButton);
		palettePanel.add(insertButton);

		JPanel namePanel = new JPanel(new BorderLayout(5, 0));
		namePanel.setOpaque(false);
		namePanel.add(new JLabel("Sequence Name:"), BorderLayout.WEST);
		namePanel.add(sequenceNamePanel, BorderLayout.CENTER);

		JPanel buttonPanel = new JPanel(new FlowLayout(FlowLayout.RIGHT, 6, 2));
		buttonPanel.setOpaque(false);
		buttonPanel.add(settingsButton);
		buttonPanel.add(discardButton);
		buttonPanel.add(saveButton);

		headerContent.add(palettePanel, BorderLayout.WEST);
		headerContent.add(namePanel, BorderLayout.CENTER);
		headerContent.add(buttonPanel, BorderLayout.EAST);
		headerPanel.add(headerContent, BorderLayout.CENTER);

		return headerPanel;
	}

	private JPanel createInsertButton() {
		ImageIcon icon = IconLoader.loadScaledOrNull(ResourceIcons.PRESET_MANAGER_INSERT_CLIPBOARD_DARK, 18, 18);
		return createHeaderPaletteButton(
				"insertClipboardButton",
				icon,
				"\uD83D\uDCCB",
				"Clipboard",
				"Drag to insert clipboard rotation"
		);
	}

	private JPanel createTooltipButton() {
		ImageIcon icon = IconLoader.loadScaledOrNull(ResourceIcons.PRESET_MANAGER_TEXT_ADD_DARK, 18, 18);
		return createHeaderPaletteButton(
				"tooltipPaletteButton",
				icon,
				"+",
				"Text",
				"Drag to add a tooltip message"
		);
	}

	private JPanel createHeaderPaletteButton(String name,
	                                        ImageIcon icon,
	                                        String fallbackSymbol,
	                                        String text,
		String tooltip) {
		Theme theme = ThemeManager.getTheme();
		Color baseBackground = UiColorPalette.UI_CARD_BACKGROUND;
		Color mutedBackground = baseBackground.darker();
		Color baseForeground = theme != null ? theme.getTextPrimaryColor() : UiColorPalette.UI_TEXT_COLOR;
		Color mutedForeground = theme != null ? theme.getTextMutedColor() : UiColorPalette.DIALOG_MESSAGE_TEXT;

		JPanel panel = new JPanel();
		panel.setName(name);
		panel.setLayout(new BoxLayout(panel, BoxLayout.X_AXIS));

		panel.setOpaque(true);
		panel.setBackground(mutedBackground);
		panel.setBorder(UiColorPalette.paddedLineBorder(UiColorPalette.UI_CARD_BORDER_STRONG, 4));
		panel.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));

		JLabel symbolLabel = icon != null ? new JLabel(icon) : new JLabel(fallbackSymbol);
		symbolLabel.setBorder(new EmptyBorder(0, 2, 0, 4));
		symbolLabel.setForeground(mutedForeground);

		JLabel textLabel = new JLabel(text);
		textLabel.setForeground(mutedForeground);

		panel.add(symbolLabel);
		panel.add(textLabel);

		panel.setToolTipText(tooltip);
		panel.addMouseListener(new MouseAdapter() {
			@Override
			public void mouseEntered(MouseEvent e) {
				panel.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
				panel.setBackground(baseBackground);
				symbolLabel.setForeground(baseForeground);
				textLabel.setForeground(baseForeground);
			}

			@Override
			public void mouseExited(MouseEvent e) {
				panel.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
				panel.setBackground(mutedBackground);
				symbolLabel.setForeground(mutedForeground);
				textLabel.setForeground(mutedForeground);
			}
		});

		return panel;
	}

	private void installTextCursorResolver() {
		if (textCursorResolver != null) {
			return;
		}
		textCursorResolver = TextCursorSupport.installWindowTextCursorResolver(this, logger);
	}

	private void uninstallTextCursorResolver() {
		if (textCursorResolver == null) {
			return;
		}
		textCursorResolver.uninstall();
		textCursorResolver = null;
	}

	private void applyButtonStyles() {
		applySettingsButtonStyle();
	}

	private JScrollPane createContentScrollPane() {
		JScrollPane scrollPane = new JScrollPane(abilityFlowView);
		scrollPane.setBorder(UiColorPalette.SCROLL_BORDER);
		scrollPane.setHorizontalScrollBarPolicy(JScrollPane.HORIZONTAL_SCROLLBAR_NEVER);
		scrollPane.setVerticalScrollBarPolicy(JScrollPane.VERTICAL_SCROLLBAR_AS_NEEDED);
		scrollPane.getVerticalScrollBar().setUnitIncrement(5);
		scrollPane.setOpaque(false);
		scrollPane.getViewport().setOpaque(false);
		abilityFlowView.setOpaque(false);
		return scrollPane;
	}

	private void registerEventHandlers() {
		saveButton.addActionListener(e -> {
			presenter.saveSequence();
			refreshDirtyState();
		});
		discardButton.addActionListener(e -> {
			presenter.discardChanges();
			refreshDirtyState();
		});
		settingsButton.addActionListener(e -> presenter.openRotationSettings());
		registerInsertDragHandler();
		registerTooltipDragHandler();
		registerSequenceNameDirtyHandler();
	}

	public void discardChanges() {
		presenter.discardChanges();
	}

	public void startPaletteDrag(AbilityItem item, JPanel card, Point startPoint) {
		presenter.startPaletteDrag(item, card, startPoint);
	}

	public void loadSequence(RotationConfig.PresetData presetData) {
		presenter.loadSequence(presetData);
		refreshDirtyState();
	}

	public void loadSequence(String presetId, RotationConfig.PresetData presetData) {
		presenter.loadSequence(presetId, presetData);
		refreshDirtyState();
	}

	public void startNewSequence(String presetId, RotationConfig.PresetData presetData) {
		loadSequence(presetId, presetData);
		highlightSequenceNameField();
	}

	public void highlightSequenceNameField() {
		SwingUtilities.invokeLater(() -> {
			sequenceNameField.requestFocusInWindow();
			sequenceNameField.selectAll();
		});
	}

	public void clear() {
		presenter.clear();
		refreshDirtyState();
	}

	public void addSaveListener(SaveListener listener) {
		presenter.addSaveListener(listener);
	}

	public void saveSequence() {
		presenter.saveSequence();
		refreshDirtyState();
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
	public JComponent asComponent() {
		return this;
	}

	@Override
	public void showRotationSettings(String presetId, RotationConfig.PresetData presetData) {
		Window owner = SwingUtilities.getWindowAncestor(this);
		String rotationName = sequenceNameField.getText();
		RotationSettingsDialog dialog = new RotationSettingsDialog(owner, rotationName, presetData);
		dialog.setVisible(true);
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

	private void registerTooltipDragHandler() {
		tooltipButton.addMouseListener(new MouseAdapter() {
			@Override
			public void mousePressed(MouseEvent e) {
				if (!SwingUtilities.isLeftMouseButton(e)) {
					return;
				}
				startTooltipDrag(e);
			}
		});
	}

	private void registerSequenceNameDirtyHandler() {
		sequenceNameField.getDocument().addDocumentListener(new DocumentListener() {
			@Override
			public void insertUpdate(DocumentEvent e) {
				refreshDirtyState();
			}

			@Override
			public void removeUpdate(DocumentEvent e) {
				refreshDirtyState();
			}

			@Override
			public void changedUpdate(DocumentEvent e) {
				refreshDirtyState();
			}
		});
	}

	private void refreshDirtyState() {
		boolean dirty = presenter.hasUnsavedChanges();
		if (lastDirtyState != null && lastDirtyState == dirty) {
			return;
		}
		lastDirtyState = dirty;
		saveButton.setEnabled(dirty);
		discardButton.setEnabled(dirty);
		saveButton.setToolTipText(dirty ? "Save changes" : "No changes to save");
		discardButton.setToolTipText(dirty ? "Discard changes" : "No changes to discard");
	}

	private void applySettingsButtonStyle() {
		ImageIcon settingsIcon = IconLoader.loadScaledOrNull(ResourceIcons.PRESET_MANAGER_COGWHEEL_DARK, 16, 16);
		if (settingsIcon != null) {
			settingsButton.setText(null);
			settingsButton.setIcon(settingsIcon);
			settingsButton.setMargin(new Insets(2, 6, 2, 6));
		} else {
			settingsButton.setText("\u2699");
			settingsButton.setMargin(new Insets(2, 8, 2, 8));
		}
		settingsButton.setToolTipText("Settings");
		settingsButton.setFocusable(false);
		Font base = settingsButton.getFont();
		if (base != null) {
			settingsButton.setFont(base.deriveFont(Math.max(10f, base.getSize2D() - 2f)));
		}
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

	private void startTooltipDrag(MouseEvent triggerEvent) {
		String defaultMessage = "New tooltip";
		String key = "tooltip-" + System.nanoTime();

		com.lansoftprogramming.runeSequence.ui.shared.model.TooltipItem item =
				new com.lansoftprogramming.runeSequence.ui.shared.model.TooltipItem(
						key,
						defaultMessage,
						defaultMessage,
						tooltipIcon
				);

		abilityFlowView.startPaletteDrag(item, tooltipButton, triggerEvent.getPoint());
	}

	private ImageIcon createInsertIcon() {
		int size = 18;
		int padding = 3;
		int cornerRadius = 8;
		BufferedImage image = new BufferedImage(size, size, BufferedImage.TYPE_INT_ARGB);
		Graphics2D g2d = image.createGraphics();
		g2d.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
		g2d.setColor(UiColorPalette.INSERT_ICON_FILL);
		g2d.fillRoundRect(padding, padding, size - (padding * 2), size - (padding * 2), cornerRadius, cornerRadius);
		g2d.setStroke(new BasicStroke(2f));
		g2d.setColor(UiColorPalette.TEXT_INVERSE);
		int mid = size / 2;
		int arm = mid - padding - 1;
		g2d.drawLine(mid, mid - arm, mid, mid + arm);
		g2d.drawLine(mid - arm, mid, mid + arm, mid);
		g2d.dispose();
		return new ImageIcon(image);
	}

	private ImageIcon createTooltipIcon() {
		int size = 18;
		int padding = 3;
		int cornerRadius = 8;
		BufferedImage image = new BufferedImage(size, size, BufferedImage.TYPE_INT_ARGB);
		Graphics2D g2d = image.createGraphics();
		g2d.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
		g2d.setColor(UiColorPalette.INSERT_ICON_FILL);
		g2d.fillRoundRect(padding, padding, size - (padding * 2), size - (padding * 2), cornerRadius, cornerRadius);
		g2d.setStroke(new BasicStroke(2f));
		g2d.setColor(UiColorPalette.TEXT_INVERSE);
		int mid = size / 2;
		int arm = mid - padding - 1;
		g2d.drawLine(mid - arm, mid, mid + arm, mid);
		g2d.dispose();
		return new ImageIcon(image);
	}

	private String readClipboardContent() {
		ClipboardStrings.ReadResult result = ClipboardStrings.readSystemClipboardString();
		return switch (result.status()) {
			case SUCCESS -> result.text();
			case NO_STRING -> {
				showToast("Clipboard is empty.", false);
				yield null;
			}
			case UNAVAILABLE -> {
				showToast("Could not read from clipboard.", true);
				yield null;
			}
		};
	}

	private void showToast(String message, boolean error) {
		if (notifications == null) {
			Toolkit.getDefaultToolkit().beep();
			return;
		}
		if (error) {
			notifications.showError(message);
		} else {
			notifications.showInfo(message);
		}
	}
}
