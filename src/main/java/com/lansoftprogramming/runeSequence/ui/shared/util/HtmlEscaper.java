package com.lansoftprogramming.runeSequence.ui.shared.util;

import java.util.Objects;

public final class HtmlEscaper {
	private HtmlEscaper() {
	}

	public static String escape(String text) {
		Objects.requireNonNull(text, "text");
		if (text.isEmpty()) {
			return text;
		}

		StringBuilder builder = new StringBuilder(text.length());
		for (int i = 0; i < text.length(); i++) {
			char c = text.charAt(i);
			switch (c) {
				case '<' -> builder.append("&lt;");
				case '>' -> builder.append("&gt;");
				case '&' -> builder.append("&amp;");
				case '"' -> builder.append("&quot;");
				case '\'' -> builder.append("&#39;");
				default -> builder.append(c);
			}
		}
		return builder.toString();
	}
}
