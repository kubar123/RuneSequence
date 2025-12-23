package com.lansoftprogramming.runeSequence.ui.theme;

import java.beans.PropertyChangeListener;
import java.beans.PropertyChangeSupport;
import java.util.Objects;

public final class ThemeManager {

	public static final String PROPERTY_THEME = "theme";

	private static final ThemeManager INSTANCE = new ThemeManager(new MetalTheme());

	private final PropertyChangeSupport changeSupport = new PropertyChangeSupport(this);
	private Theme theme;

	private ThemeManager(Theme initialTheme) {
		this.theme = Objects.requireNonNull(initialTheme, "initialTheme");
	}

	public static Theme getTheme() {
		return INSTANCE.theme;
	}

	public static void setTheme(Theme newTheme) {
		INSTANCE.setThemeInternal(newTheme);
	}

	public static void addThemeChangeListener(PropertyChangeListener listener) {
		INSTANCE.changeSupport.addPropertyChangeListener(PROPERTY_THEME, listener);
	}

	public static void removeThemeChangeListener(PropertyChangeListener listener) {
		INSTANCE.changeSupport.removePropertyChangeListener(PROPERTY_THEME, listener);
	}

	private void setThemeInternal(Theme newTheme) {
		Objects.requireNonNull(newTheme, "newTheme");
		Theme oldTheme = this.theme;
		if (oldTheme == newTheme) {
			return;
		}
		this.theme = newTheme;
		changeSupport.firePropertyChange(PROPERTY_THEME, oldTheme, newTheme);
	}
}

