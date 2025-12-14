package com.lansoftprogramming.runeSequence.ui.shared.model;

import javax.swing.*;
import java.awt.image.BufferedImage;
import java.util.Objects;

/**
 * Represents an ability item with its display metadata.
 * This is a value object that encapsulates all information needed to display an ability in the UI.
 */
public class AbilityItem {
	private static final ImageIcon PLACEHOLDER_ICON = new ImageIcon(
			new BufferedImage(1, 1, BufferedImage.TYPE_INT_ARGB)
	);

	private final String key;
	private final String displayName;
	private final int level;
	private final String type;
	private final ImageIcon icon;

	public AbilityItem(String key, String displayName, int level, String type, ImageIcon icon) {
		this.key = Objects.requireNonNull(key, "Ability key cannot be null");
		this.displayName = Objects.requireNonNull(displayName, "Display name cannot be null");
		this.level = level;
		this.type = type;
		this.icon = icon != null ? icon : PLACEHOLDER_ICON;
	}

	public String getKey() {
		return key;
	}

	public String getDisplayName() {
		return displayName;
	}

	public int getLevel() {
		return level;
	}

	public String getType() {
		return type;
	}

	public ImageIcon getIcon() {
		return icon;
	}

	@Override
	public boolean equals(Object o) {
		if (this == o) return true;
		if (o == null || getClass() != o.getClass()) return false;
		AbilityItem that = (AbilityItem) o;
		return key.equals(that.key);
	}

	@Override
	public int hashCode() {
		return Objects.hash(key);
	}

	@Override
	public String toString() {
		return String.format("AbilityItem{key='%s', displayName='%s', level=%d, type='%s'}",
				key, displayName, level, type);
	}
}
