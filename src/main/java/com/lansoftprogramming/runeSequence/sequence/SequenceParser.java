package com.lansoftprogramming.runeSequence.sequence;

import java.util.ArrayList;
import java.util.List;

/**
 * Recursive descent parser for ability sequence expressions.
 * Grammar:
 * Expression := Step ('→' Step)*
 * Step       := Term ('+' Term)*
 * Term       := Alternative ('/' Alternative)*
 * Alternative:= Token | '(' Expression ')'
 */
public class SequenceParser {

	private final String input;
	private int pos = 0;

	public SequenceParser(String input) {
		this.input = input;
	}

	public static SequenceDefinition parse(String input) {
		return new SequenceParser(input).parseExpression();
	}

	private SequenceDefinition parseExpression() {
		List<Step> steps = new ArrayList<>();
		steps.add(parseStep());

		skipWhitespace();
		while (peek("→")) {
			consume("→");
			steps.add(parseStep());
			skipWhitespace();
		}

		return new SequenceDefinition(steps);
	}

	private Step parseStep() {
		List<Term> terms = new ArrayList<>();
		terms.add(parseTerm());

		skipWhitespace();
		while (peek("+")) {
			consume("+");
			terms.add(parseTerm());
			skipWhitespace();
		}

		return new Step(terms);
	}

	private Term parseTerm() {
		List<Alternative> alts = new ArrayList<>();
		alts.add(parseAlternative());

		skipWhitespace();
		while (peek("/")) {
			consume("/");
			alts.add(parseAlternative());
			skipWhitespace();
		}

		return new Term(alts);
	}

	private Alternative parseAlternative() {
		skipWhitespace();
		if (peek("(")) {
			consume("(");
			SequenceDefinition inner = parseExpression();
			consume(")");
			return new Alternative(new Step(inner.getSteps())); // flatten inner as a group
		}
		return new Alternative(parseToken());
	}

	private String parseToken() {
		skipWhitespace();
		StringBuilder sb = new StringBuilder();
		while (!eof() && !isDelimiter(peekChar())) {
			sb.append(input.charAt(pos++));
		}
		return sb.toString().trim();
	}

	// --------------------
	// Helpers
	// --------------------
	private boolean peek(String s) {
		skipWhitespace();
		return input.startsWith(s, pos);
	}

	private void consume(String s) {
		skipWhitespace();
		if (!input.startsWith(s, pos)) {
			throw new IllegalStateException("Expected '" + s + "' at pos " + pos);
		}
		pos += s.length();
	}

	private void skipWhitespace() {
		while (!eof() && Character.isWhitespace(input.charAt(pos))) pos++;
	}

	private boolean eof() {
		return pos >= input.length();
	}

	private char peekChar() {
		return input.charAt(pos);
	}

	private boolean isDelimiter(char c) {
		return Character.isWhitespace(c) || c == '+' || c == '/' || c == '→' || c == ')' || c == '(';
	}
}
