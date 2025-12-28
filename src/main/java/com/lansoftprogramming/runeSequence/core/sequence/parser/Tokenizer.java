package com.lansoftprogramming.runeSequence.core.sequence.parser;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Pattern;

public class Tokenizer {
	private static final Logger logger = LoggerFactory.getLogger(Tokenizer.class);
	private static final char TOOLTIP_MARKER = '\uE000';
	private static final Pattern SPEC_SUFFIX = Pattern.compile("(?i)\\s+spec\\b");
	private static final Pattern EOFSPEC_SUFFIX = Pattern.compile("(?i)\\s+eofspec\\b");
	private static final Pattern ROAR_ODE_PAIR = Pattern.compile("(?i)\\b(roarofawakening)\\s+(odetodeceit)\\b");

	public List<Token> tokenize(String expression) {

		logger.debug("Tokenizer: Input expression: '{}'", expression);

		// Handle "spec" as a special suffix, converting "[ability] spec" to "[ability] + spec"
		String processedExpression = normalizeSpecialSuffixes(expression);
		processedExpression = normalizeGroupedItems(processedExpression);
		// Treat tooltip markup "(message)" as non-structural and ignore it for operator decisions.
		// We replace it with a marker that forces an ability boundary so "A (tip) B" does not become
		// a single merged ability token "A B".
		processedExpression = replaceTooltipMarkupWithMarker(processedExpression);

		// Treat line breaks between two abilities as an implicit arrow.
		// This allows multi-line clipboard payloads to be parsed as a single expression:
		// "a → b\nc → d" becomes "a → b→c → d".
		String newlineNormalizedExpression = normalizeLineBreaks(processedExpression);
		logger.debug("Tokenizer: Expression after newline normalization: '{}'", newlineNormalizedExpression);

		// Stage 1: Add padding around operators and parentheses
		// Support both Unicode → and ASCII -> by normalizing to the canonical arrow
		String paddedExpression = newlineNormalizedExpression
				.replace("->", "→")
				.replaceAll("([→+/()])", " $1 ");

		logger.debug("Tokenizer: Padded expression: '{}'", paddedExpression);

		// Stage 2: Split by whitespace and merge ability words
		String[] parts = paddedExpression.trim().split("\\s+");
		logger.debug("Tokenizer: Split into {} parts:", parts.length);

		for (int i = 0; i < parts.length; i++) {
			logger.debug("  Part[{}]: '{}'", i, parts[i]);
		}

		List<Token> tokens = new ArrayList<>();
		StringBuilder currentAbility = new StringBuilder();

		for (String part : parts) {
			if (isTooltipMarker(part)) {
				if (currentAbility.length() > 0) {
					String abilityName = currentAbility.toString().trim();
					logger.debug("Tokenizer: Adding ability token: '{}'", abilityName);
					tokens.add(new Token.Ability(abilityName));
					currentAbility.setLength(0);
				}
				continue;
			}
			if (isOperatorOrParen(part)) {
				// Add pending ability first
				if (currentAbility.length() > 0) {
					String abilityName = currentAbility.toString().trim();

					logger.debug("Tokenizer: Adding ability token: '{}'", abilityName);
					tokens.add(new Token.Ability(abilityName));
					currentAbility.setLength(0);
				}
				// Add operator/paren

				logger.debug("Tokenizer: Adding operator/paren: '{}'", part);
				addOperatorOrParenToken(part, tokens);
			} else {
				// Append to current ability
				if (currentAbility.length() > 0) {
					currentAbility.append(" ");
				}
				currentAbility.append(part);

				logger.debug("Tokenizer: Building ability: '{}'", currentAbility);
			}
		}

		// Add final ability
		if (currentAbility.length() > 0) {
			String finalAbility = currentAbility.toString().trim();

			logger.debug("Tokenizer: Adding final ability: '{}'", finalAbility);
			tokens.add(new Token.Ability(finalAbility));
		}


		logger.debug("Tokenizer: Final token count: {}", tokens.size());

		for (int i = 0; i < tokens.size(); i++) {

			logger.debug("  Token[{}]: {}", i, tokens.get(i));

		}

		return tokens;
	}

	private String normalizeSpecialSuffixes(String expression) {
		if (expression == null || expression.isEmpty()) {
			return expression;
		}
		String normalized = SPEC_SUFFIX.matcher(expression).replaceAll(" + spec");
		return EOFSPEC_SUFFIX.matcher(normalized).replaceAll(" + eofspec");
	}

	private String normalizeGroupedItems(String expression) {
		if (expression == null || expression.isEmpty()) {
			return expression;
		}
		return ROAR_ODE_PAIR.matcher(expression).replaceAll("$1 + $2");
	}

	private boolean isTooltipMarker(String part) {
		return part != null && part.length() == 1 && part.charAt(0) == TOOLTIP_MARKER;
	}

	private String replaceTooltipMarkupWithMarker(String input) {
		if (input == null || input.isEmpty()) {
			return input;
		}

		StringBuilder out = new StringBuilder(input.length());
		int index = 0;

		while (index < input.length()) {
			char current = input.charAt(index);

			if (current == '(' && !isEscaped(input, index)) {
				int candidateEnd = findTooltipCandidateEnd(input, index + 1);
				if (candidateEnd != -1) {
					char prevNonWhitespace = prevNonWhitespace(input, index - 1);
					char nextNonWhitespace = nextNonWhitespace(input, candidateEnd + 1);
					boolean leftAttached = prevNonWhitespace != 0
							&& !Character.isWhitespace(prevNonWhitespace)
							&& !isOperatorOrParenChar(prevNonWhitespace)
							&& prevNonWhitespace != TOOLTIP_MARKER;
					boolean rightAttached = nextNonWhitespace != 0
							&& !Character.isWhitespace(nextNonWhitespace)
							&& !isOperatorOrParenChar(nextNonWhitespace)
							&& nextNonWhitespace != TOOLTIP_MARKER;

					// Only treat "(...)" as tooltip markup when it is attached to an ability-like token
					// on either side; otherwise it is likely structural grouping "(expression)".
					if (!leftAttached && !rightAttached) {
						out.append(current);
						index++;
						continue;
					}
					out.append(' ').append(TOOLTIP_MARKER).append(' ');
					index = candidateEnd + 1;
					continue;
				}
			}

			out.append(current);
			index++;
		}

		return out.toString();
	}

	private char prevNonWhitespace(String expression, int startIndex) {
		for (int i = Math.min(startIndex, expression.length() - 1); i >= 0; i--) {
			char c = expression.charAt(i);
			if (!Character.isWhitespace(c)) {
				return c;
			}
		}
		return 0;
	}

	private char nextNonWhitespace(String expression, int startIndex) {
		for (int i = Math.max(0, startIndex); i < expression.length(); i++) {
			char c = expression.charAt(i);
			if (!Character.isWhitespace(c)) {
				return c;
			}
		}
		return 0;
	}

	private int findTooltipCandidateEnd(String expression, int startIndex) {
		int cursor = startIndex;
		boolean foundClosing = false;
		boolean hasContent = false;

		while (cursor < expression.length()) {
			char current = expression.charAt(cursor);

			if (current == '\\' && cursor + 1 < expression.length()) {
				char next = expression.charAt(cursor + 1);
				if (next == '(' || next == ')') {
					hasContent = true;
					cursor += 2;
					continue;
				}
			}

			if (current == '(' && !isEscaped(expression, cursor)) {
				return -1; // nested paren => structural grouping, not a tooltip
			}

			if (current == ')' && !isEscaped(expression, cursor)) {
				foundClosing = true;
				break;
			}

			if (!Character.isWhitespace(current)) {
				hasContent = true;
			}
			if (current == '→' || current == '+' || current == '/') {
				return -1; // tooltip text cannot contain structural operators
			}

			cursor++;
		}

		if (!foundClosing || !hasContent) {
			return -1;
		}

		return cursor;
	}

	private boolean isEscaped(String expression, int index) {
		int backslashCount = 0;
		int cursor = index - 1;
		while (cursor >= 0 && expression.charAt(cursor) == '\\') {
			backslashCount++;
			cursor--;
		}
		return backslashCount % 2 != 0;
	}


	private String normalizeLineBreaks(String input) {
		if (input == null || input.isEmpty()) {
			return input;
		}

		String[] rawLines = input.split("\\R");
		List<String> logicalLines = new ArrayList<>();

		for (String raw : rawLines) {
			String cleaned = cleanLine(raw);
			if (!cleaned.isEmpty()) {
				logicalLines.add(cleaned);
			}
		}

		if (logicalLines.isEmpty()) {
			return "";
		}

		StringBuilder out = new StringBuilder(input.length());
		// Start with the first non-empty line as-is
		out.append(logicalLines.get(0));

		for (int i = 1; i < logicalLines.size(); i++) {
			String prev = logicalLines.get(i - 1);
			String current = logicalLines.get(i);

			char last = prev.charAt(prev.length() - 1);
			char first = current.charAt(0);

			// If a line boundary sits next to an operator/paren, treat it as spacing only.
			// Otherwise, treat it as an implicit arrow between abilities.
			if (isOperatorOrParenChar(last) || isOperatorOrParenChar(first)) {
				out.append(' ');
			} else {
				out.append('→');
			}

			out.append(current);
		}

		return out.toString();
	}

	private String cleanLine(String line) {
		if (line == null || line.isEmpty()) {
			return "";
		}

		String sanitized = ExpressionSanitizer.removeInvisibles(line);
		StringBuilder sb = new StringBuilder(sanitized.length());
		for (int i = 0; i < sanitized.length(); i++) {
			char c = sanitized.charAt(i);
			// Strip line-break characters defensively (split() should already have removed them)
			if (c == '\n' || c == '\r') {
				continue;
			}
			sb.append(c);
		}

		return sb.toString().trim();
	}

	private boolean isOperatorOrParenChar(char c) {
		return c == '→' || c == '+' || c == '/' || c == '(' || c == ')';
	}


	private boolean isOperatorOrParen(String part) {
		boolean isOp = part.equals("→") || part.equals("+") ||
				part.equals("/") || part.equals("(") || part.equals(")");

		logger.debug("Tokenizer.isOperatorOrParen('{}'): {}", part, isOp);
		return isOp;
	}

	private void addOperatorOrParenToken(String part, List<Token> tokens) {
		switch (part) {
			case "→", "+", "/" -> tokens.add(new Token.Operator(part));
			case "(" -> tokens.add(new Token.LeftParen());
			case ")" -> tokens.add(new Token.RightParen());

			default -> logger.error("Tokenizer: Unknown operator/paren: '{}'", part);
		}
	}
}
