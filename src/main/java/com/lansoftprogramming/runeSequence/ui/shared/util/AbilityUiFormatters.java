package com.lansoftprogramming.runeSequence.ui.shared.util;

import com.lansoftprogramming.runeSequence.ui.shared.model.AbilityItem;

public final class AbilityUiFormatters {
	private AbilityUiFormatters() {
	}

	public static String abilityTooltipHtml(AbilityItem item) {
		return String.format("<html><b>%s</b><br/>Type: %s<br/>Level: %d</html>",
				item.getDisplayName(),
				item.getType(),
				item.getLevel());
	}

	public static String formatCardDisplayName(String displayName) {
		if (displayName == null) {
			return "";
		}
		String trimmed = displayName.trim();
		if (trimmed.equals("Greater")) {
			return "G.";
		}
		if (trimmed.startsWith("Greater ")) {
			return "G. " + trimmed.substring("Greater ".length());
		}
		return displayName;
	}

	public static String truncate(String text, int maxLength) {
		if (text.length() <= maxLength) {
			return text;
		}
		return text.substring(0, maxLength - 3) + "...";
	}
}
