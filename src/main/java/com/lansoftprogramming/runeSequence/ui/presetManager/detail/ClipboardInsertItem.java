package com.lansoftprogramming.runeSequence.ui.presetManager.detail;

import com.lansoftprogramming.runeSequence.ui.presetManager.model.SequenceElement;
import com.lansoftprogramming.runeSequence.ui.shared.model.AbilityItem;

import javax.swing.*;
import java.util.ArrayList;
import java.util.List;

/**
 * Lightweight payload used when dragging a copied rotation from the clipboard.
 * It behaves like an AbilityItem for the drag controller but carries the parsed
 * sequence elements so the presenter can insert the full rotation at drop time.
 */
class ClipboardInsertItem extends AbilityItem {
	private final List<SequenceElement> elements;
	private final String expression;

	public ClipboardInsertItem(String key,
							   String displayName,
							   ImageIcon icon,
							   List<SequenceElement> elements,
							   String expression) {
		super(key, displayName, 0, "Rotation", icon);
		this.elements = new ArrayList<>(elements);
		this.expression = expression;
	}

	public List<SequenceElement> getElements() {
		return new ArrayList<>(elements);
	}

	public String getExpression() {
		return expression;
	}

	/**
	 * @return true when the clipboard payload contains exactly one ability and no separators.
	 */
	public boolean isSingleAbility() {
		return elements.size() == 1 && elements.get(0).isAbility();
	}

	/**
	 * @return true when the clipboard payload spans multiple sequential steps (arrows present).
	 */
	public boolean isMultiStep() {
		return elements.stream().anyMatch(e -> e.getType() == SequenceElement.Type.ARROW);
	}
}
