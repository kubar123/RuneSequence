package com.lansoftprogramming.runeSequence.core.sequence.parser;

final class ParserEscapes {
	private ParserEscapes() {
	}

	static boolean isEscaped(String expression, int index) {
		if (expression == null || index <= 0 || index >= expression.length()) {
			return false;
		}

		int backslashCount = 0;
		int cursor = index - 1;
		while (cursor >= 0 && expression.charAt(cursor) == '\\') {
			backslashCount++;
			cursor--;
		}
		return backslashCount % 2 != 0;
	}
}
