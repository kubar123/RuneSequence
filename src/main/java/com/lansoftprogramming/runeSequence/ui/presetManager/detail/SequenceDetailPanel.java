package com.lansoftprogramming.runeSequence.ui.presetManager.detail;

import com.lansoftprogramming.runeSequence.infrastructure.config.RotationConfig;
import com.lansoftprogramming.runeSequence.ui.presetManager.drag.handler.AbilityDragController;
import com.lansoftprogramming.runeSequence.ui.presetManager.drag.model.DropPreview;
import com.lansoftprogramming.runeSequence.ui.presetManager.model.SequenceElement;
import com.lansoftprogramming.runeSequence.ui.presetManager.service.ExpressionBuilder;
import com.lansoftprogramming.runeSequence.ui.shared.component.WrapLayout;
import com.lansoftprogramming.runeSequence.ui.shared.model.AbilityItem;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.swing.*;
import javax.swing.border.EmptyBorder;
import java.awt.*;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

public class SequenceDetailPanel extends JPanel {
	private static final Logger logger = LoggerFactory.getLogger(SequenceDetailPanel.class);

	private final SequenceDetailService detailService;
	private final ExpressionBuilder expressionBuilder;
	private final AbilityDragController dragHandler;

	private final JTextField sequenceNameField;
	private final JButton settingsButton;
	private final JButton saveButton;
	private final JButton discardButton;
	private final JPanel abilityFlowPanel;

	private List<SequenceElement> currentElements;
	private List<SequenceElement> previewElements;
	private final List<SaveListener> saveListeners;
	private RotationConfig.PresetData currentPreset;
	private String currentPresetId;
	private DropPreview currentPreview;

	//track if highlighting is active
	private boolean isHighlightActive = false;
	private boolean isDragOutsidePanel = false;

	public SequenceDetailPanel(SequenceDetailService detailService) {
		this.detailService = detailService;
		this.expressionBuilder = new ExpressionBuilder();
		this.currentElements = new ArrayList<>();
		this.previewElements = new ArrayList<>();
		this.saveListeners = new ArrayList<>();
		this.currentPresetId = null;

		setLayout(new BorderLayout());
		setBorder(new EmptyBorder(10, 10, 10, 10));

		sequenceNameField = new JTextField();
		settingsButton = new JButton("Settings");
		saveButton = new JButton("Save");
		discardButton = new JButton("Discard");
		abilityFlowPanel = new JPanel(new WrapLayout(FlowLayout.LEFT, 10, 10));

		dragHandler = new AbilityDragController(abilityFlowPanel, new AbilityDragController.DragCallback() {
			@Override
			public void onDragStart(AbilityItem item, boolean isFromPalette) {
				handleDragStart(item, isFromPalette);
			}

			@Override
			public void onDragMove(AbilityItem draggedItem, Point cursorPos, DropPreview preview) {
				handleDragMove(draggedItem, cursorPos, preview);
			}

			@Override
			public void onDragEnd(AbilityItem draggedItem, boolean commit) {
				handleDragEnd(draggedItem, commit);
			}

			@Override
			public List<SequenceElement> getCurrentElements() {
				return previewElements;
			}

			@Override
			public Component[] getAllCards() {
				return getAbilityCardArray();
			}
		});

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
		namePanel.add(new JLabel("️Sequence Name:"), BorderLayout.WEST);
		namePanel.add(sequenceNameField, BorderLayout.CENTER);

		JPanel buttonPanel = new JPanel(new FlowLayout(FlowLayout.RIGHT, 5, 0));
		buttonPanel.add(settingsButton);
		buttonPanel.add(discardButton);
		buttonPanel.add(saveButton);

		headerPanel.add(namePanel, BorderLayout.CENTER);
		headerPanel.add(buttonPanel, BorderLayout.EAST);

		return headerPanel;
	}

	private JScrollPane createContentScrollPane() {
		JScrollPane scrollPane = new JScrollPane(abilityFlowPanel);
		scrollPane.setBorder(BorderFactory.createLineBorder(Color.LIGHT_GRAY));
		scrollPane.setHorizontalScrollBarPolicy(JScrollPane.HORIZONTAL_SCROLLBAR_NEVER);
		scrollPane.setVerticalScrollBarPolicy(JScrollPane.VERTICAL_SCROLLBAR_AS_NEEDED);
		return scrollPane;
	}

	private void registerEventHandlers() {
		saveButton.addActionListener(e -> handleSave());
	}

	private void handleDragStart(AbilityItem item, boolean isFromPalette) {
	    if (!isFromPalette) {
	        previewElements = expressionBuilder.removeAbility(new ArrayList<>(currentElements), item.getKey());
	    } else {
	        previewElements = new ArrayList<>(currentElements);
	    }
	}

	private void handleDragMove(AbilityItem draggedItem, Point cursorPos, DropPreview preview) {
	    currentPreview = preview;
	    clearAllHighlights();
	    isHighlightActive = false;

	    // cursorPos is already in flowPanel coordinates, need to convert to this panel's coordinates
	    Point detailPanelPoint = SwingUtilities.convertPoint(abilityFlowPanel, cursorPos, this);
	    isDragOutsidePanel = !this.contains(detailPanelPoint);

	    // Notify drag controller about outside state for transparency
	    dragHandler.setDragOutsidePanel(isDragOutsidePanel);

	    if (preview.isValid() && preview.getTargetAbilityIndex() >= 0) {
	        highlightDropZone(preview);
	        isHighlightActive = true;
	    }
	}

	private void handleDragEnd(AbilityItem draggedItem, boolean commit) {
	    clearAllHighlights();

	    // Determine if this was from the sequence (not palette)
	    boolean isFromSequence = !previewElements.equals(currentElements);

	    if (commit) {
	        // Dropped inside panel
	        if (isHighlightActive && currentPreview != null && currentPreview.isValid()) {
	            // Valid drop with highlighting - insert at new position
	            currentElements = expressionBuilder.insertAbility(
				    new ArrayList<>(previewElements),
				    draggedItem.getKey(),
				    currentPreview.getInsertIndex(),
				    currentPreview.getZoneType(),
				    currentPreview.getDropSide()  // Add this parameter
				);
	            updateExpression();
	        } else {
	            // No highlighting - cancel drag, restore original
	            currentElements = new ArrayList<>(currentElements);
	            previewElements = new ArrayList<>(currentElements);
	        }
	    } else {
	        // Dropped outside panel
	        if (isFromSequence) {
	            // Delete the ability (keep previewElements which has it removed)
	            currentElements = new ArrayList<>(previewElements);
	            updateExpression();
	        } else {
	            // Was from palette - just cancel
	            previewElements = new ArrayList<>(currentElements);
	        }
	    }

	    isHighlightActive = false;
	    isDragOutsidePanel = false;
	    currentPreview = null;
	    renderSequenceElements(currentElements);
	}

	private void updateExpression() {
		if (currentPreset != null) {
			String newExpression = expressionBuilder.buildExpression(currentElements);
			currentPreset.setExpression(newExpression);
			logger.debug("Updated expression: {}", newExpression);
		}
	}

	public void startPaletteDrag(AbilityItem item, JPanel card, Point startPoint) {
		MouseAdapter dragListener = dragHandler.createCardDragListener(item, card, true);

		MouseEvent syntheticEvent = new MouseEvent(
			card, MouseEvent.MOUSE_PRESSED, System.currentTimeMillis(), 0,
			startPoint.x, startPoint.y, 1, false
		);
		dragListener.mousePressed(syntheticEvent);

		card.addMouseMotionListener(dragListener);
		card.addMouseListener(new MouseAdapter() {
			@Override
			public void mouseReleased(MouseEvent e) {
				dragListener.mouseReleased(e);
				card.removeMouseMotionListener(dragListener);
				card.removeMouseListener(this);
			}
		});
	}

	private void clearAllHighlights() {
		for (Component card : getAbilityCardArray()) {
			if (card instanceof JPanel) {
				card.setBackground(Color.WHITE);
				((JPanel) card).setBorder(BorderFactory.createLineBorder(Color.LIGHT_GRAY));
			}
		}
		abilityFlowPanel.repaint();
	}

	private void highlightDropZone(DropPreview preview) {
		Component[] cards = getAbilityCardArray();
		int targetIndex = preview.getTargetAbilityIndex();

		if (targetIndex >= 0 && targetIndex < cards.length && cards[targetIndex] instanceof JPanel) {
			JPanel targetCard = (JPanel) cards[targetIndex];

			Color highlightColor;
			switch (preview.getZoneType()) {
				case AND:
					highlightColor = new Color(170, 255, 171);
					break;
				case OR:
					highlightColor = new Color(158, 99, 220);
					break;
				case NEXT:
					highlightColor = new Color(250, 117, 159);
					break;
				default:
					highlightColor = Color.WHITE;
			}

			targetCard.setBackground(highlightColor);
			targetCard.setBorder(BorderFactory.createLineBorder(highlightColor.darker(), 2));
			abilityFlowPanel.repaint();
		}
	}

	private Component[] getAbilityCardArray() {
	    List<Component> cards = new ArrayList<>();
	    for (Component comp : abilityFlowPanel.getComponents()) {
	        collectAbilityCardsRecursive(comp, cards);
	    }
	    return cards.toArray(new Component[0]);
	}

	private void collectAbilityCardsRecursive(Component comp, List<Component> out) {
	    if (comp == null) return;
	    if (comp instanceof JPanel && "abilityCard".equals(comp.getName())) {
	        out.add(comp);
	        return;
	    }
	    if (comp instanceof Container) {
	        for (Component child : ((Container) comp).getComponents()) {
	            collectAbilityCardsRecursive(child, out);
	        }
	    }
	}

	public void loadSequence(String presetId, RotationConfig.PresetData presetData) {
		if (presetData == null) {
			clear();
			return;
		}

		this.currentPresetId = presetId;
		this.currentPreset = presetData;
		sequenceNameField.setText(presetData.getName());

		currentElements = new ArrayList<>(detailService.parseSequenceExpression(presetData.getExpression()));
		previewElements = new ArrayList<>(currentElements);
		renderSequenceElements(currentElements);

		long abilityCount = currentElements.stream().filter(SequenceElement::isAbility).count();
		logger.debug("Loaded sequence: {} with {} abilities", presetData.getName(), abilityCount);
	}

	public void clear() {
		sequenceNameField.setText("");
		currentElements.clear();
		previewElements.clear();
		currentPreset = null;
		currentPresetId = null;
		renderSequenceElements(currentElements);
	}

	/**
	 * Renders sequence elements as visual cards.
	 * Tags each card with its element index for stable drop targeting.
	 */
	private void renderSequenceElements(List<SequenceElement> elements) {

		abilityFlowPanel.removeAll();

		int i = 0;
		while (i < elements.size()) {
			SequenceElement currentElement = elements.get(i);

			if (currentElement.isAbility()) {
				// Check if this ability starts a group
				if (i + 1 < elements.size()) {
					SequenceElement nextElement = elements.get(i + 1);
					if (nextElement.isPlus() || nextElement.isSlash()) {
						i = renderAbilityGroup(elements, i, nextElement.getType());
						i++;
						continue;
					}
				}
				// Standalone ability
				addStandaloneAbility(currentElement, i);
			} else if (currentElement.isSeparator()) {
				abilityFlowPanel.add(createSeparatorLabel(currentElement));
			}

			i++;
		}

		abilityFlowPanel.revalidate();
		abilityFlowPanel.repaint();
	}

	/**
	 * Renders a group of abilities connected by + or /.
	 * Tags each card with its element index for stable drop targeting.
	 */
	private int renderAbilityGroup(List<SequenceElement> elements, int startIndex, SequenceElement.Type groupType) {
		Color groupColor = groupType == SequenceElement.Type.PLUS
				? new Color(220, 255, 223)
				: new Color(196, 163, 231);

		AbilityGroupPanel groupPanel = new AbilityGroupPanel(groupColor);

		// Add first ability
		SequenceElement firstElement = elements.get(startIndex);
		AbilityItem firstItem = detailService.createAbilityItem(firstElement.getValue());
		if (firstItem != null) {
			JPanel card = createAbilityCard(firstItem);
			card.putClientProperty("elementIndex", startIndex);
			groupPanel.add(card);
		}

		// Process remaining abilities in the group
		int currentIndex = startIndex + 1;
		while (currentIndex < elements.size()) {
			SequenceElement separator = elements.get(currentIndex);

			// Stop if different separator type
			if (separator.getType() != groupType) {
				break;
			}

			// Add separator visual
			groupPanel.add(createSeparatorLabel(separator));

			// Add next ability if exists
			if (currentIndex + 1 < elements.size() && elements.get(currentIndex + 1).isAbility()) {
				int abilityElementIndex = currentIndex + 1;
				SequenceElement abilityElement = elements.get(abilityElementIndex);
				AbilityItem item = detailService.createAbilityItem(abilityElement.getValue());
				if (item != null) {
					JPanel card = createAbilityCard(item);
					card.putClientProperty("elementIndex", abilityElementIndex);
					groupPanel.add(card);
				}
				currentIndex += 2;
			} else {
				break;
			}
		}

		abilityFlowPanel.add(groupPanel);
		return currentIndex - 1;
	}

	/**
	 * Adds standalone ability card (not in a group).
	 */
	private void addStandaloneAbility(SequenceElement element, int elementIndex) {
		AbilityItem item = detailService.createAbilityItem(element.getValue());
		if (item != null) {
			JPanel card = createAbilityCard(item);
			card.putClientProperty("elementIndex", elementIndex);
			abilityFlowPanel.add(card);
		}
	}

	private JLabel createSeparatorLabel(SequenceElement element) {
		JLabel label = new JLabel(" " + element.getValue() + " ");
		label.setFont(new Font(Font.SANS_SERIF, Font.BOLD, 18));
		label.setForeground(Color.RED);
		label.setBorder(new EmptyBorder(0, 5, 0, 5));
		return label;
	}

	private String truncateText(String text, int maxLength) {
		if (text.length() <= maxLength) {
			return text;
		}
		return text.substring(0, maxLength - 3) + "...";
	}

	/**
	 * Creates an ability card visual component.
	 */
	private JPanel createAbilityCard(AbilityItem item) {
	    JPanel card = new JPanel();
	    card.setName("abilityCard");
	    card.setLayout(new BoxLayout(card, BoxLayout.Y_AXIS));
	    card.setBackground(Color.WHITE);
	    card.setBorder(BorderFactory.createLineBorder(Color.LIGHT_GRAY));
	    card.setMinimumSize(new Dimension(50, 68));
	    card.setPreferredSize(new Dimension(50, 68));
	    card.setMaximumSize(new Dimension(50, 68));

	    JLabel iconLabel = new JLabel(item.getIcon());
	    iconLabel.setAlignmentX(Component.CENTER_ALIGNMENT);
	    iconLabel.setMinimumSize(new Dimension(50, 50));
	    iconLabel.setPreferredSize(new Dimension(50, 50));
	    iconLabel.setMaximumSize(new Dimension(50, 50));

	    String displayText = truncateText(item.getDisplayName(), 12);
	    JLabel nameLabel = new JLabel(displayText);
	    nameLabel.setFont(nameLabel.getFont().deriveFont(9f));
	    nameLabel.setAlignmentX(Component.CENTER_ALIGNMENT);
	    nameLabel.setMinimumSize(new Dimension(50, 16));
	    nameLabel.setPreferredSize(new Dimension(50, 16));
	    nameLabel.setMaximumSize(new Dimension(50, 16));

	    card.add(iconLabel);
	    card.add(nameLabel);

	    MouseAdapter dragListener = dragHandler.createCardDragListener(item, card, false);
	    card.addMouseListener(dragListener);
	    card.addMouseMotionListener(dragListener);
	    return card;
	}

	private static class AbilityGroupPanel extends JPanel {
		public AbilityGroupPanel(Color groupColor) {
			setLayout(new FlowLayout(FlowLayout.LEFT, 5, 5));
			setBackground(groupColor);
			setBorder(BorderFactory.createCompoundBorder(
					BorderFactory.createLineBorder(groupColor.darker()),
					BorderFactory.createEmptyBorder(5, 5, 5, 5)
			));
		}
	}

	public void addSaveListener(SaveListener listener) {
		if (listener != null) {
			saveListeners.add(listener);
		}
	}

	private void notifySaveListeners(SequenceDetailService.SaveResult result) {
		for (SaveListener listener : saveListeners) {
			try {
				listener.onSequenceSaved(result);
			} catch (Exception listenerException) {
				logger.warn("Save listener threw exception", listenerException);
			}
		}
	}

	private void handleSave() {
		String trimmedName = sequenceNameField.getText() != null
				? sequenceNameField.getText().trim()
				: "";

		String expression = expressionBuilder.buildExpression(currentElements);

		try {
			SequenceDetailService.SaveResult result = detailService.saveSequence(
					currentPresetId,
					currentPreset,
					trimmedName,
					expression
			);

			currentPresetId = result.getPresetId();
			currentPreset = result.getPresetData();
			if (currentPreset != null) {
				currentPreset.setName(trimmedName);
				currentPreset.setExpression(expression);
			}

			notifySaveListeners(result);
			JOptionPane.showMessageDialog(
					this,
					"Sequence saved successfully.",
					"Save Complete",
					JOptionPane.INFORMATION_MESSAGE
			);

		} catch (IllegalArgumentException validationError) {
			logger.debug("Sequence validation failed: {}", validationError.getMessage());
			JOptionPane.showMessageDialog(
					this,
					validationError.getMessage(),
					"Validation Error",
					JOptionPane.WARNING_MESSAGE
			);
		} catch (IOException ioException) {
			logger.error("Failed to persist sequence", ioException);
			JOptionPane.showMessageDialog(
					this,
					"Failed to save sequence: " + ioException.getMessage(),
					"I/O Error",
					JOptionPane.ERROR_MESSAGE
			);
		} catch (Exception unexpected) {
			logger.error("Unexpected error while saving sequence", unexpected);
			JOptionPane.showMessageDialog(
					this,
					"Unexpected error while saving sequence.",
					"Error",
					JOptionPane.ERROR_MESSAGE
			);
		}
	}

	public interface SaveListener {
		void onSequenceSaved(SequenceDetailService.SaveResult result);
	}
}
