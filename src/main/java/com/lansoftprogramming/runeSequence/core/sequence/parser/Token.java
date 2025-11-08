package com.lansoftprogramming.runeSequence.core.sequence.parser;

import java.util.Objects;

/**
 * Represents a token in the ability sequence expression.
 * This is a sealed interface, which means all implementing classes must be declared in this file.
 */
public sealed interface Token {

	/**
	 * Represents an ability name.
	 *
	 * @param name The name of the ability, which can include spaces.
	 */
	record Ability(String name) implements Token {
		public Ability {
			Objects.requireNonNull(name, "Ability name cannot be null.");
			if (name.isBlank()) {
				throw new IllegalArgumentException("Ability name cannot be blank.");
			}
		}
	}

	/**
	 * Represents an operator token.
	 *
	 * @param symbol The operator symbol (e.g., "→", "+", "/").
	 */
	record Operator(String symbol) implements Token {
		public Operator {
			Objects.requireNonNull(symbol, "Operator symbol cannot be null.");
			if (!symbol.equals("→") && !symbol.equals("+") && !symbol.equals("/")) {
				throw new IllegalArgumentException("Invalid operator symbol: " + symbol);
			}
		}
	}

	/**
	 * Represents a left parenthesis '('.
	 */
	record LeftParen() implements Token {
	}

	/**
	 * Represents a right parenthesis ')'.
	 */
	record RightParen() implements Token {
	}
}