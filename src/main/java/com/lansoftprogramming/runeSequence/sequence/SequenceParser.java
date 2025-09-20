package com.lansoftprogramming.runeSequence.sequence;

import java.util.ArrayList;
import java.util.List;

/**
 * A recursive descent parser that transforms a sequence expression string into an Abstract Syntax Tree (AST).
 * The parser is implemented based on the following grammar:
 * <p>
 * Expression  := Step ('→' Step)*
 * Step        := Term ('+' Term)*
 * Term        := Alternative ('/' Alternative)*
 * Alternative := Ability | '(' Expression ')'
 * Ability     := A string representing an ability name.
 * <p>
 * This parser first tokenizes the input string and then builds the AST from the token stream.
 */
public class SequenceParser {

	private final List<Token> tokens;
	private int pos = 0;

	public SequenceParser(List<Token> tokens) {
		this.tokens = tokens;
	}

	/**
	 * Parses a raw expression string into a {@link SequenceDefinition}.
	 *
	 * @param input The expression string.
	 * @return The root of the AST.
	 */
	public static SequenceDefinition parse(String input) {

		Tokenizer tokenizer = new Tokenizer();
		List<Token> tokens = tokenizer.tokenize(input);
		return new SequenceParser(tokens).parseExpression();
	}

	private SequenceDefinition parseExpression() {
		List<Step> steps = new ArrayList<>();
		steps.add(parseStep());

		while (matchOperator("→")) {
			steps.add(parseStep());
		}

		return new SequenceDefinition(steps);
	}

	private Step parseStep() {
		List<Term> terms = new ArrayList<>();
		terms.add(parseTerm());

		while (matchOperator("+")) {
			terms.add(parseTerm());
		}

		return new Step(terms);
	}

	private Term parseTerm() {
		List<Alternative> alts = new ArrayList<>();
		alts.add(parseAlternative());

		while (matchOperator("/")) {
			alts.add(parseAlternative());
		}

		return new Term(alts);
	}

	private Alternative parseAlternative() {
		if (peek() instanceof Token.LeftParen) {
			consume(Token.LeftParen.class);
			SequenceDefinition inner = parseExpression();
			consume(Token.RightParen.class);
			return new Alternative(inner);
		} else {
			Token.Ability abilityToken = consume(Token.Ability.class);
			return new Alternative(abilityToken.name());
		}
	}

	// --------------------
	// Helpers
	// --------------------

	private Token peek() {
		if (eof()) {
			return null;
		}
		return tokens.get(pos);
	}

	private boolean matchOperator(String symbol) {
		Token current = peek();
		if (current instanceof Token.Operator op && op.symbol().equals(symbol)) {
			pos++;
			return true;
		}
		return false;
	}

	private <T extends Token> T consume(Class<T> type) {
		Token current = peek();
		if (type.isInstance(current)) {
			pos++;
			return type.cast(current);
		}
		throw new IllegalStateException("Expected token of type " + type.getSimpleName() + " but found " + (current != null ? current.getClass().getSimpleName() : "EOF") + " at position " + pos);
	}

	private boolean eof() {
		return pos >= tokens.size();
	}
}