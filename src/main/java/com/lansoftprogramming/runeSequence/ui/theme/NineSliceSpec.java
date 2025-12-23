package com.lansoftprogramming.runeSequence.ui.theme;

import java.awt.*;

public record NineSliceSpec(int left, int right, int top, int bottom) {

	public NineSliceSpec {
		if (left < 0 || right < 0 || top < 0 || bottom < 0) {
			throw new IllegalArgumentException("Nine-slice insets must be non-negative.");
		}
	}

	public Insets toInsets() {
		return new Insets(top, left, bottom, right);
	}

	public Insets toInsetsCopy() {
		Insets insets = toInsets();
		return new Insets(insets.top, insets.left, insets.bottom, insets.right);
	}

	@Override
	public String toString() {
		return "NineSliceSpec{left=" + left + ", right=" + right + ", top=" + top + ", bottom=" + bottom + "}";
	}
}