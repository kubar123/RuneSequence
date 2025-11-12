package com.lansoftprogramming.runeSequence.core.sequence.parser;

import com.lansoftprogramming.runeSequence.core.sequence.model.Alternative;
import com.lansoftprogramming.runeSequence.core.sequence.model.SequenceDefinition;
import com.lansoftprogramming.runeSequence.core.sequence.model.Step;
import com.lansoftprogramming.runeSequence.core.sequence.model.Term;

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
		SequenceParser parser = new SequenceParser(tokens);
		SequenceDefinition definition = parser.parseExpression();
		parser.ensureFullyConsumed();
		return definition;
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

		validateTermOrdering(terms);
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
			validateAbilityName(abilityToken.name());
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

	private void ensureFullyConsumed() {
		if (!eof()) {
			Token unexpected = peek();
			throw new IllegalStateException("Unexpected token " + (unexpected != null ? unexpected.getClass().getSimpleName() : "EOF") + " at position " + pos);
		}
	}

	private void validateTermOrdering(List<Term> terms) {
		if (terms.size() < 3) {
			return;
		}

		for (int i = 1; i < terms.size() - 1; i++) {
			if (terms.get(i).getAlternatives().size() > 1) {
				throw new IllegalStateException("Cannot nest alternatives between '+' operators without parentheses near term index " + i);
			}
		}
	}

	private void validateAbilityName(String name) {
		if (name != null && name.indexOf(' ') >= 0 && name.matches("[A-Za-z0-9 ]+")) {
			throw new IllegalStateException("Missing operator between abilities near '" + name + "'");
		}
	}
}
