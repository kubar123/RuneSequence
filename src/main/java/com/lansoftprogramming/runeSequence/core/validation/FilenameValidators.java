package com.lansoftprogramming.runeSequence.core.validation;

public final class FilenameValidators {
	private FilenameValidators() {
	}

	public static boolean containsPathSeparator(String value) {
		if (value == null) {
			return false;
		}
		return value.contains("/") || value.contains("\\") || value.contains(":");
	}
}

