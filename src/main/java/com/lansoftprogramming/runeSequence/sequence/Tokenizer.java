package com.lansoftprogramming.runeSequence.sequence;

import java.util.ArrayList;
import java.util.List;

public class Tokenizer {

	public List<Token> tokenize(String expression) {

		System.out.println("Tokenizer: Input expression: '" + expression + "'");
		// Print char codes for debugging Unicode issues

		for (int i = 0; i < expression.length(); i++) {

			char c = expression.charAt(i);

			System.out.println("  Char[" + i + "]: '" + c + "' (code=" + (int) c + ")");

		}

		// Stage 1: Add padding around operators and parentheses
		// Support both Unicode → and ASCII ->
		String paddedExpression = expression
				.replaceAll("([→+/()])", " $1 ")
				.replaceAll("(->)", " $1 ");


		System.out.println("Tokenizer: Padded expression: '" + paddedExpression + "'");

		// Stage 2: Split by whitespace and merge ability words
		String[] parts = paddedExpression.trim().split("\\s+");

		System.out.println("Tokenizer: Split into " + parts.length + " parts:");

		for (int i = 0; i < parts.length; i++) {

			System.out.println("  Part[" + i + "]: '" + parts[i] + "'");

		}

		List<Token> tokens = new ArrayList<>();
		StringBuilder currentAbility = new StringBuilder();

		for (String part : parts) {
			if (isOperatorOrParen(part)) {
				// Add pending ability first
				if (currentAbility.length() > 0) {
					String abilityName = currentAbility.toString().trim();

					System.out.println("Tokenizer: Adding ability token: '" + abilityName + "'");
					tokens.add(new Token.Ability(abilityName));
					currentAbility.setLength(0);
				}
				// Add operator/paren

				System.out.println("Tokenizer: Adding operator/paren: '" + part + "'");
				addOperatorOrParenToken(part, tokens);
			} else {
				// Append to current ability
				if (currentAbility.length() > 0) {
					currentAbility.append(" ");
				}
				currentAbility.append(part);

				System.out.println("Tokenizer: Building ability: '" + currentAbility + "'");
			}
		}

		// Add final ability
		if (currentAbility.length() > 0) {
			String finalAbility = currentAbility.toString().trim();

			System.out.println("Tokenizer: Adding final ability: '" + finalAbility + "'");
			tokens.add(new Token.Ability(finalAbility));
		}


		System.out.println("Tokenizer: Final token count: " + tokens.size());

		for (int i = 0; i < tokens.size(); i++) {

			System.out.println("  Token[" + i + "]: " + tokens.get(i));

		}

		return tokens;
	}

	private List<Token> postProcess(List<Token> tokens) {
		if (tokens.isEmpty()) {
			return tokens;
		}

		List<Token> finalTokens = new ArrayList<>();

		for (int i = 0; i < tokens.size(); i++) {
			Token current = tokens.get(i);

			// Only merge if we have the exact pattern: Ability LeftParen Ability RightParen
			if (current instanceof Token.Ability ability1 &&
					i + 3 < tokens.size() &&
					tokens.get(i + 1) instanceof Token.LeftParen &&
					tokens.get(i + 2) instanceof Token.Ability ability2 &&
					tokens.get(i + 3) instanceof Token.RightParen) {

				String firstName = ability1.name().toLowerCase();
				String secondName = ability2.name().toLowerCase();

				// Only merge if first ability is short and second looks like a modifier
				if (firstName.length() <= 4 &&
						(secondName.contains("tick") || secondName.contains("cast") ||
								secondName.matches("\\d+.*"))) {
					String mergedName = ability1.name() + " (" + ability2.name() + ")";
					finalTokens.add(new Token.Ability(mergedName));
					i += 3; // Skip next 3 tokens
				} else {
					// Don't merge, add current token
					finalTokens.add(current);
				}
			} else {
				finalTokens.add(current);
			}
		}

		return finalTokens;
	}

	// FIXED: Support both Unicode → and ASCII ->
	private boolean isOperatorOrParen(String part) {
		boolean isOp = part.equals("→") || part.equals("->") || part.equals("+") ||
				part.equals("/") || part.equals("(") || part.equals(")");

		System.out.println("Tokenizer.isOperatorOrParen('" + part + "'): " + isOp);
		return isOp;
	}

	// FIXED: Support both Unicode → and ASCII ->
	private void addOperatorOrParenToken(String part, List<Token> tokens) {
		switch (part) {
			case "→", "->", "+", "/" -> tokens.add(new Token.Operator(part));
			case "(" -> tokens.add(new Token.LeftParen());
			case ")" -> tokens.add(new Token.RightParen());

			default -> System.err.println("Tokenizer: Unknown operator/paren: '" + part + "'");
		}
	}
}