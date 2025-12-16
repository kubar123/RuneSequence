package com.lansoftprogramming.runeSequence.ui.presetManager.detail;

import com.lansoftprogramming.runeSequence.infrastructure.config.RotationConfig;
import com.lansoftprogramming.runeSequence.ui.notification.NotificationService;
import com.lansoftprogramming.runeSequence.ui.presetManager.model.SequenceElement;
import com.lansoftprogramming.runeSequence.ui.presetManager.service.AbilityOverridesService;
import com.lansoftprogramming.runeSequence.ui.shared.component.HoverGlowContainerPanel;
import com.lansoftprogramming.runeSequence.ui.shared.model.AbilityItem;
import com.lansoftprogramming.runeSequence.ui.theme.UiColorPalette;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.swing.*;
import javax.swing.border.EmptyBorder;
import javax.swing.event.DocumentEvent;
import javax.swing.event.DocumentListener;
import javax.swing.text.JTextComponent;
import java.awt.*;
import java.awt.datatransfer.Clipboard;
import java.awt.datatransfer.DataFlavor;
import java.awt.datatransfer.UnsupportedFlavorException;
import java.awt.event.AWTEventListener;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.awt.event.MouseMotionAdapter;
import java.awt.image.BufferedImage;
import java.io.IOException;
import java.util.List;

public class SequenceDetailPanel extends JPanel implements SequenceDetailPresenter.View {
	private static final Logger logger = LoggerFactory.getLogger(SequenceDetailPanel.class);
	private static final String ICON_COGWHEEL_DARK = "/ui/dark/PresetManagerWindow.cogWheel.png";
	private static final String ICON_INSERT_CLIPBOARD_DARK = "/ui/dark/PresetManagerWindow.insertFromClipboard.png";
	private static final String ICON_TEXT_ADD_DARK = "/ui/dark/PresetWindowManager.textAdd.png";

	private final JTextField sequenceNameField;
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
	private transient AWTEventListener textCursorListener;
	private transient Boolean lastCursorOverText;
	private transient long lastCursorLogAtMs;
	private transient Window cursorWindow;

	public SequenceDetailPanel(SequenceDetailService detailService,
	                           AbilityOverridesService overridesService,
	                           NotificationService notifications) {
		this.detailService = detailService;
		this.overridesService = overridesService;
		this.notifications = notifications;
		setLayout(new BorderLayout());
		setBorder(new EmptyBorder(10, 10, 10, 10));

		sequenceNameField = new JTextField();
		installTextCursor(sequenceNameField);
		sequenceNameField.setMargin(new Insets(2, 6, 2, 6));
		insertIcon = loadScaledIconOrFallback(ICON_INSERT_CLIPBOARD_DARK, 18, 18, this::createInsertIcon);
		tooltipIcon = loadScaledIconOrFallback(ICON_TEXT_ADD_DARK, 18, 18, this::createTooltipIcon);
		insertButton = createInsertButton();
		tooltipButton = createTooltipButton();
		settingsButton = new JButton("Settings");
		saveButton = new JButton("Save");
		discardButton = new JButton("Discard");
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
		installTextCursor(sequenceNameField);
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
		JPanel headerPanel = new JPanel(new BorderLayout(10, 0));
		headerPanel.setBorder(new EmptyBorder(2, 0, 2, 0));

		JPanel palettePanel = new HoverGlowContainerPanel(
				new FlowLayout(FlowLayout.LEFT, 6, 2),
				component -> component instanceof JComponent jc && ("tooltipPaletteButton".equals(jc.getName()) || "insertClipboardButton".equals(jc.getName()))
		);
		palettePanel.setOpaque(false);
		palettePanel.add(tooltipButton);
		palettePanel.add(insertButton);

		JPanel namePanel = new JPanel(new BorderLayout(5, 0));
		namePanel.setBorder(new EmptyBorder(2, 0, 2, 0));
		namePanel.add(new JLabel("Sequence Name:"), BorderLayout.WEST);
		namePanel.add(sequenceNameField, BorderLayout.CENTER);

		JPanel buttonPanel = new JPanel(new FlowLayout(FlowLayout.RIGHT, 6, 2));
		buttonPanel.add(settingsButton);
		buttonPanel.add(discardButton);
		buttonPanel.add(saveButton);

		headerPanel.add(palettePanel, BorderLayout.WEST);
		headerPanel.add(namePanel, BorderLayout.CENTER);
		headerPanel.add(buttonPanel, BorderLayout.EAST);

		return headerPanel;
	}

	private JPanel createInsertButton() {
		ImageIcon icon = loadScaledIconOrNull(ICON_INSERT_CLIPBOARD_DARK, 18, 18);
		return createHeaderPaletteButton(
				"insertClipboardButton",
				icon,
				"\uD83D\uDCCB",
				"Clipboard",
				"Drag to insert clipboard rotation"
		);
	}

	private JPanel createTooltipButton() {
		ImageIcon icon = loadScaledIconOrNull(ICON_TEXT_ADD_DARK, 18, 18);
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
		JPanel panel = new JPanel();
		panel.setName(name);
		panel.setLayout(new BoxLayout(panel, BoxLayout.X_AXIS));

		Color baseColor = UIManager.getColor("Button.background");
		if (baseColor == null) {
			baseColor = UiColorPalette.UI_CARD_DIMMED_BACKGROUND;
		}
		Color mutedColor = blend(baseColor, UiColorPalette.TEXT_MUTED, 0.12f);
		Color hoverColor = baseColor;

		Color baseForeground = UIManager.getColor("Label.foreground");
		if (baseForeground == null) {
			baseForeground = UiColorPalette.TEXT_PRIMARY;
		}
		Color finalBaseForeground = baseForeground;
		Color mutedForeground = blend(baseForeground, UiColorPalette.TEXT_MUTED, 0.35f);

		panel.setOpaque(true);
		panel.setBackground(mutedColor);
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
				panel.setBackground(hoverColor);
				symbolLabel.setForeground(finalBaseForeground);
				textLabel.setForeground(finalBaseForeground);
			}

			@Override
			public void mouseExited(MouseEvent e) {
				panel.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
				panel.setBackground(mutedColor);
				symbolLabel.setForeground(mutedForeground);
				textLabel.setForeground(mutedForeground);
			}
		});

		return panel;
	}

	private static Color blend(Color a, Color b, float bWeight) {
		if (a == null) {
			return b;
		}
		if (b == null) {
			return a;
		}
		float clamped = Math.max(0f, Math.min(1f, bWeight));
		int r = Math.round((a.getRed() * (1f - clamped)) + (b.getRed() * clamped));
		int g = Math.round((a.getGreen() * (1f - clamped)) + (b.getGreen() * clamped));
		int bl = Math.round((a.getBlue() * (1f - clamped)) + (b.getBlue() * clamped));
		return new Color(r, g, bl);
	}

	private static void installTextCursor(JComponent component) {
		if (component == null) {
			return;
		}
		Cursor textCursor = Cursor.getPredefinedCursor(Cursor.TEXT_CURSOR);
		component.setCursor(textCursor);
		component.addMouseListener(new MouseAdapter() {
			@Override
			public void mouseEntered(MouseEvent e) {
				component.setCursor(textCursor);
			}
		});
		component.addMouseMotionListener(new MouseMotionAdapter() {
			@Override
			public void mouseMoved(MouseEvent e) {
				component.setCursor(textCursor);
			}
		});
	}

	private void installTextCursorResolver() {
		if (textCursorListener != null) {
			return;
		}
		Cursor textCursor = Cursor.getPredefinedCursor(Cursor.TEXT_CURSOR);
		Cursor defaultCursor = Cursor.getDefaultCursor();
		cursorWindow = SwingUtilities.getWindowAncestor(this);

		textCursorListener = event -> {
			if (!(event instanceof MouseEvent mouseEvent)) {
				return;
			}
			int id = mouseEvent.getID();
			if (id != MouseEvent.MOUSE_MOVED && id != MouseEvent.MOUSE_DRAGGED && id != MouseEvent.MOUSE_EXITED) {
				return;
			}
			Object src = mouseEvent.getSource();
			if (!(src instanceof Component sourceComponent)) {
				return;
			}
			if (!SwingUtilities.isDescendingFrom(sourceComponent, this)) {
				return;
			}

			Point panelPoint = SwingUtilities.convertPoint(sourceComponent, mouseEvent.getPoint(), this);
			Component deepest = SwingUtilities.getDeepestComponentAt(this, panelPoint.x, panelPoint.y);
			boolean overText = deepest instanceof JTextComponent;
			// Set the cursor at the Window level; this wins even if another container overlays the field.
			Window owner = cursorWindow != null ? cursorWindow : SwingUtilities.getWindowAncestor(this);
			if (owner != null) {
				owner.setCursor(overText ? textCursor : defaultCursor);
			} else {
				setCursor(overText ? textCursor : defaultCursor);
			}

			// Debug logging: emit on state changes (or at most ~2x/sec while inside the panel).
			long now = System.currentTimeMillis();
			boolean shouldLog = lastCursorOverText == null
					|| lastCursorOverText != overText
					|| (now - lastCursorLogAtMs) > 500;
			if (shouldLog && logger.isDebugEnabled()) {
				lastCursorOverText = overText;
				lastCursorLogAtMs = now;
				String srcName = sourceComponent.getName();
				String deepestName = deepest != null ? deepest.getName() : null;
				String deepestClass = deepest != null ? deepest.getClass().getName() : "null";
				Cursor deepestCursor = deepest != null ? deepest.getCursor() : null;
				boolean deepestEnabled = deepest == null || deepest.isEnabled();
				boolean deepestEditable = !(deepest instanceof JTextComponent tc) || tc.isEditable();
				Cursor ownerCursor = owner != null ? owner.getCursor() : null;
				logger.debug(
						"Cursor debug: overText={}, eventId={}, src={}, srcName={}, panelPoint=({},{}), deepest={}, deepestName={}, deepestEnabled={}, deepestEditable={}, deepestCursor={}, owner={}, ownerCursor={}",
						overText,
						id,
						sourceComponent.getClass().getName(),
						srcName,
						panelPoint.x,
						panelPoint.y,
						deepestClass,
						deepestName,
						deepestEnabled,
						deepestEditable,
						deepestCursor,
						owner != null ? owner.getClass().getName() : "null",
						ownerCursor
				);
			}
		};

		Toolkit.getDefaultToolkit().addAWTEventListener(
				textCursorListener,
				AWTEvent.MOUSE_MOTION_EVENT_MASK
		);
	}

	private void uninstallTextCursorResolver() {
		if (textCursorListener == null) {
			return;
		}
		Toolkit.getDefaultToolkit().removeAWTEventListener(textCursorListener);
		textCursorListener = null;
		lastCursorOverText = null;
		lastCursorLogAtMs = 0L;
		cursorWindow = null;
	}

	private void applyButtonStyles() {
		applySettingsButtonStyle();
		applySaveDiscardButtonStyles();
	}

	private JScrollPane createContentScrollPane() {
		JScrollPane scrollPane = new JScrollPane(abilityFlowView);
		scrollPane.setBorder(UiColorPalette.SCROLL_BORDER);
		scrollPane.setHorizontalScrollBarPolicy(JScrollPane.HORIZONTAL_SCROLLBAR_NEVER);
		scrollPane.setVerticalScrollBarPolicy(JScrollPane.VERTICAL_SCROLLBAR_AS_NEEDED);
		scrollPane.getVerticalScrollBar().setUnitIncrement(5);
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
		ImageIcon settingsIcon = loadScaledIconOrNull(ICON_COGWHEEL_DARK, 16, 16);
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

	private void applySaveDiscardButtonStyles() {
		saveButton.setBackground(UiColorPalette.TOAST_SUCCESS_ACCENT);
		saveButton.setForeground(UiColorPalette.TEXT_INVERSE);
		saveButton.setOpaque(true);
		saveButton.setFocusPainted(false);

		discardButton.setBackground(UiColorPalette.UI_CARD_DIMMED_BACKGROUND);
		discardButton.setForeground(UiColorPalette.TEXT_PRIMARY);
		discardButton.setOpaque(true);
		discardButton.setFocusPainted(false);
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

	private ImageIcon loadScaledIconOrNull(String resourcePath, int width, int height) {
		try {
			java.net.URL iconUrl = getClass().getResource(resourcePath);
			if (iconUrl == null) {
				return null;
			}
			ImageIcon originalIcon = new ImageIcon(iconUrl);
			Image scaledImage = originalIcon.getImage().getScaledInstance(width, height, Image.SCALE_SMOOTH);
			return new ImageIcon(scaledImage);
		} catch (Exception ignored) {
			return null;
		}
	}

	private ImageIcon loadScaledIconOrFallback(String resourcePath,
	                                          int width,
	                                          int height,
	                                          java.util.function.Supplier<ImageIcon> fallback) {
		ImageIcon icon = loadScaledIconOrNull(resourcePath, width, height);
		return icon != null ? icon : fallback.get();
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