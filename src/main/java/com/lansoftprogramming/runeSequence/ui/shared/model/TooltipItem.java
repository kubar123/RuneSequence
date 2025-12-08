package com.lansoftprogramming.runeSequence.ui.shared.model;

import javax.swing.*;

/**
 * Palette/model item representing a tooltip message card in the sequence editor.
 * Extends {@link AbilityItem} so it can participate in the existing drag flow.
 */
public class TooltipItem extends AbilityItem {

	private final String message;

	public TooltipItem(String key, String displayName, String message, ImageIcon icon) {
		super(key, displayName, 0, "Tooltip", icon);
		this.message = message != null ? message : "";
	}

	public String getMessage() {
		return message;
	}
}

