package com.lansoftprogramming.runeSequence.core.sequence.parser;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.List;

public class Tokenizer {
	private static final Logger logger = LoggerFactory.getLogger(Tokenizer.class);

	public List<Token> tokenize(String expression) {

		logger.debug("Tokenizer: Input expression: '{}'", expression);

		// Handle "spec" as a special suffix, converting "[ability] spec" to "[ability] + spec"
		String processedExpression = expression.replaceAll("(?i)\\s+spec\\b", " + spec");

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

		StringBuilder sb = new StringBuilder(line.length());
		for (int i = 0; i < line.length(); i++) {
			char c = line.charAt(i);

			// Strip line-break characters defensively (split() should already have removed them)
			if (c == '\n' || c == '\r') {
				continue;
			}

			// Drop zero-width / exotic whitespace that often sneaks in from rich text
			if (c == '\u200B' || c == '\u00A0' || c == '\u202F') {
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
