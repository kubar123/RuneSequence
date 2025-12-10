package com.lansoftprogramming.runeSequence.core.sequence.parser;

import java.util.Set;

/**
 * Shared tooltip grammar utilities to keep operator symbols and validation rules consistent.
 */
public final class TooltipGrammar {
	public static final char ARROW = '→';
	public static final char AND = '+';
	public static final char OR = '/';
	public static final char LEFT_PAREN = '(';
	public static final char RIGHT_PAREN = ')';

	private static final Set<Character> STRUCTURAL_OPERATORS = Set.of(ARROW, AND, OR);

	private TooltipGrammar() {
	}

	public static boolean isStructuralOperator(char c) {
		return STRUCTURAL_OPERATORS.contains(c);
	}

	public static boolean isStructuralBoundary(char c) {
		return Character.isWhitespace(c) || isStructuralOperator(c) || c == LEFT_PAREN || c == RIGHT_PAREN;
	}

	public static boolean containsStructuralOperators(String text) {
		if (text == null || text.isEmpty()) {
			return false;
		}
		for (int i = 0; i < text.length(); i++) {
			if (isStructuralOperator(text.charAt(i))) {
				return true;
			}
		}
		return false;
	}

	public static boolean isValidTooltipMessage(String message) {
		if (message == null) {
			return false;
		}
		return !containsStructuralOperators(message);
	}

	public static String escapeTooltipText(String message) {
		if (message == null || message.isEmpty()) {
			return "";
		}
		return message
				.replace("(", "\\(")
				.replace(")", "\\)");
	}
}
