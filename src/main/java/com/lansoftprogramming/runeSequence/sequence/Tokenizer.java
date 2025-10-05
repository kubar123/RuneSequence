package com.lansoftprogramming.runeSequence.sequence;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.List;

public class Tokenizer {
	private static final Logger logger = LoggerFactory.getLogger(Tokenizer.class);

	public List<Token> tokenize(String expression) {

		logger.debug("Tokenizer: Input expression: '{}'", expression);
		// Print char codes for debugging Unicode issues
		for (int i = 0; i < expression.length(); i++) {
			char c = expression.charAt(i);
			logger.debug("  Char[{}]: '{}' (code={})", i, c, (int) c);
		}

		// Stage 1: Add padding around operators and parentheses
		// Support both Unicode → and ASCII ->
		String paddedExpression = expression
				.replaceAll("([→+/()])", " $1 ")
				.replaceAll("(->)", " $1 ");

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

//	private List<Token> postProcess(List<Token> tokens) {
//		if (tokens.isEmpty()) {
//			return tokens;
//		}
//
//		List<Token> finalTokens = new ArrayList<>();
//
//		for (int i = 0; i < tokens.size(); i++) {
//			Token current = tokens.get(i);
//
//			// Only merge if we have the exact pattern: Ability LeftParen Ability RightParen
//			if (current instanceof Token.Ability ability1 &&
//					i + 3 < tokens.size() &&
//					tokens.get(i + 1) instanceof Token.LeftParen &&
//					tokens.get(i + 2) instanceof Token.Ability ability2 &&
//					tokens.get(i + 3) instanceof Token.RightParen) {
//
//				String firstName = ability1.name().toLowerCase();
//				String secondName = ability2.name().toLowerCase();
//
//				// Only merge if first ability is short and second looks like a modifier
//				if (firstName.length() <= 4 &&
//						(secondName.contains("tick") || secondName.contains("cast") ||
//								secondName.matches("\\d+.*"))) {
//					String mergedName = ability1.name() + " (" + ability2.name() + ")";
//					finalTokens.add(new Token.Ability(mergedName));
//					i += 3; // Skip next 3 tokens
//				} else {
//					// Don't merge, add current token
//					finalTokens.add(current);
//				}
//			} else {
//				finalTokens.add(current);
//			}
//		}
//
//		return finalTokens;
//	}

	// FIXED: Support both Unicode → and ASCII ->
	private boolean isOperatorOrParen(String part) {
		boolean isOp = part.equals("→") || part.equals("->") || part.equals("+") ||
				part.equals("/") || part.equals("(") || part.equals(")");

		logger.debug("Tokenizer.isOperatorOrParen('{}'): {}", part, isOp);
		return isOp;
	}

	// FIXED: Support both Unicode → and ASCII ->
	private void addOperatorOrParenToken(String part, List<Token> tokens) {
		switch (part) {
			case "→", "->", "+", "/" -> tokens.add(new Token.Operator(part));
			case "(" -> tokens.add(new Token.LeftParen());
			case ")" -> tokens.add(new Token.RightParen());

			default -> logger.error("Tokenizer: Unknown operator/paren: '{}'", part);
		}
	}
}