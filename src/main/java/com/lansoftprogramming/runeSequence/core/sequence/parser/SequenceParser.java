package com.lansoftprogramming.runeSequence.core.sequence.parser;

import com.lansoftprogramming.runeSequence.core.sequence.model.*;
import com.lansoftprogramming.runeSequence.core.sequence.modifier.AbilityModifierEngine;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

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

	private static final Pattern ABILITY_WITH_LABEL = Pattern.compile("^(.*)\\[\\*(\\d+)]$");

	private final List<Token> tokens;
	private final Map<String, AbilitySettingsOverrides> overridesByLabel;
	private final Map<String, AbilitySettingsOverrides> overridesByAbility;
	private int pos = 0;

	public SequenceParser(List<Token> tokens, Map<String, AbilitySettingsOverrides> overridesByLabel) {
		this(tokens, overridesByLabel, null);
	}

	public SequenceParser(List<Token> tokens,
	                      Map<String, AbilitySettingsOverrides> overridesByLabel,
	                      Map<String, AbilitySettingsOverrides> overridesByAbility) {
		this.tokens = tokens;
		this.overridesByLabel = overridesByLabel != null ? overridesByLabel : Map.of();
		this.overridesByAbility = overridesByAbility != null ? overridesByAbility : Map.of();
	}

	/**
	 * Parses a raw expression string into a {@link SequenceDefinition}.
	 *
	 * @param input The expression string.
	 * @return The root of the AST.
	 */
	public static SequenceDefinition parse(String input) {
		return parse(input, null, null);
	}

	/**
	 * Parses a raw expression string into a {@link SequenceDefinition}, applying any per-instance and/or per-ability
	 * settings overrides supplied by callers (e.g., preset ability properties).
	 * <p>
	 * If the input contains inline {@code #*N key=value} settings lines, those are parsed and merged with the provided
	 * per-instance map, with the provided map taking precedence for overlapping fields.
	 *
	 * @param input              expression string (may contain tooltip markup and/or inline {@code #*} settings lines)
	 * @param perInstanceOverrides optional label -> overrides map (may be null)
	 * @param perAbilityOverrides  optional abilityKey -> overrides map (may be null)
	 * @return the parsed AST
	 */
	public static SequenceDefinition parse(String input,
	                                      Map<String, AbilitySettingsOverrides> perInstanceOverrides,
	                                      Map<String, AbilitySettingsOverrides> perAbilityOverrides) {
		RotationDslCodec.ParsedRotation parts = RotationDslCodec.parse(input);
		String expression = parts.expression();
		Map<String, AbilitySettingsOverrides> overridesByLabel = mergeOverridesMaps(parts.perInstanceOverrides(), perInstanceOverrides);

		Tokenizer tokenizer = new Tokenizer();
		List<Token> tokens = tokenizer.tokenize(expression);
		SequenceParser parser = new SequenceParser(tokens, overridesByLabel, perAbilityOverrides);
		SequenceDefinition definition = parser.parseExpression();
		parser.ensureFullyConsumed();
		return AbilityModifierEngine.apply(definition);
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
			ParsedAbility parsed = parseAbilityToken(abilityToken.name());
			validateAbilityName(parsed.abilityName());

			AbilitySettingsOverrides perAbility = overridesByAbility.get(parsed.abilityName());
			AbilitySettingsOverrides perInstance = parsed.instanceLabel() != null
					? overridesByLabel.get(parsed.instanceLabel())
					: null;
			AbilitySettingsOverrides overrides = mergeOverrides(perAbility, perInstance);

			return new Alternative(parsed.abilityName(), parsed.instanceLabel(), overrides);
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

	private static ParsedAbility parseAbilityToken(String rawToken) {
		if (rawToken == null) {
			throw new IllegalStateException("Ability token cannot be null.");
		}

		Matcher matcher = ABILITY_WITH_LABEL.matcher(rawToken);
		if (matcher.matches()) {
			String abilityName = matcher.group(1);
			if (!abilityName.equals(abilityName.trim())) {
				throw new IllegalStateException("Instance label suffix must be attached with no spaces near '" + rawToken + "'");
			}
			String label = matcher.group(2);
			return new ParsedAbility(abilityName, label);
		}

		int labelStart = rawToken.lastIndexOf("[*");
		if (labelStart >= 0 && rawToken.endsWith("]")) {
			throw new IllegalStateException("Invalid instance label suffix near '" + rawToken + "'");
		}

		return new ParsedAbility(rawToken, null);
	}

	private record ParsedAbility(String abilityName, String instanceLabel) {
	}

	private static Map<String, AbilitySettingsOverrides> mergeOverridesMaps(Map<String, AbilitySettingsOverrides> base,
	                                                                       Map<String, AbilitySettingsOverrides> delta) {
		if ((base == null || base.isEmpty()) && (delta == null || delta.isEmpty())) {
			return Map.of();
		}

		Map<String, AbilitySettingsOverrides> merged = new java.util.LinkedHashMap<>();
		if (base != null && !base.isEmpty()) {
			for (Map.Entry<String, AbilitySettingsOverrides> entry : base.entrySet()) {
				String label = entry.getKey();
				AbilitySettingsOverrides overrides = entry.getValue();
				if (label == null || label.isBlank() || overrides == null || overrides.isEmpty()) {
					continue;
				}
				merged.put(label, overrides);
			}
		}

		if (delta != null && !delta.isEmpty()) {
			for (Map.Entry<String, AbilitySettingsOverrides> entry : delta.entrySet()) {
				String label = entry.getKey();
				AbilitySettingsOverrides overrides = entry.getValue();
				if (label == null || label.isBlank() || overrides == null || overrides.isEmpty()) {
					continue;
				}
				AbilitySettingsOverrides existing = merged.get(label);
				merged.put(label, mergeOverrides(existing, overrides));
			}
		}

		return merged.isEmpty() ? Map.of() : Map.copyOf(merged);
	}

	private static AbilitySettingsOverrides mergeOverrides(AbilitySettingsOverrides base,
	                                                      AbilitySettingsOverrides delta) {
		return AbilitySettingsOverrides.merge(base, delta);
	}
}