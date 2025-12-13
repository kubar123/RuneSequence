package com.lansoftprogramming.runeSequence.core.sequence.parser;

import com.lansoftprogramming.runeSequence.core.sequence.model.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.*;
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

	private static final Logger logger = LoggerFactory.getLogger(SequenceParser.class);
	private static final Pattern ABILITY_WITH_LABEL = Pattern.compile("^(.*)\\[\\*(\\d+)]$");
	private static final Pattern SETTINGS_LINE = Pattern.compile("^#\\*(\\d+)\\s+(.+)$");

	private final List<Token> tokens;
	private final Map<String, AbilitySettingsOverrides> overridesByLabel;
	private int pos = 0;

	public SequenceParser(List<Token> tokens, Map<String, AbilitySettingsOverrides> overridesByLabel) {
		this.tokens = tokens;
		this.overridesByLabel = overridesByLabel != null ? overridesByLabel : Map.of();
	}

	/**
	 * Parses a raw expression string into a {@link SequenceDefinition}.
	 *
	 * @param input The expression string.
	 * @return The root of the AST.
	 */
	public static SequenceDefinition parse(String input) {

		InputParts parts = splitInput(input);
		String expression = parts.expression();
		Map<String, AbilitySettingsOverrides> overridesByLabel = parseSettingsLines(parts.settingsLines());

		Tokenizer tokenizer = new Tokenizer();
		try {
			List<Token> tokens = tokenizer.tokenize(expression);
			SequenceParser parser = new SequenceParser(tokens, overridesByLabel);
			SequenceDefinition definition = parser.parseExpression();
			parser.ensureFullyConsumed();
			return definition;
		} catch (RuntimeException primaryFailure) {
			String cleanedExpression = new TooltipMarkupParser().parse(expression).cleanedExpression();
			if (cleanedExpression == null || cleanedExpression.isBlank() || cleanedExpression.equals(expression)) {
				throw primaryFailure;
			}
			List<Token> tokens = tokenizer.tokenize(cleanedExpression);
			SequenceParser parser = new SequenceParser(tokens, overridesByLabel);
			SequenceDefinition definition = parser.parseExpression();
			parser.ensureFullyConsumed();
			return definition;
		}
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

			AbilitySettingsOverrides overrides = parsed.instanceLabel() != null
					? overridesByLabel.get(parsed.instanceLabel())
					: null;

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

	private static InputParts splitInput(String input) {
		if (input == null) {
			throw new IllegalStateException("Expression cannot be null.");
		}

		String[] rawLines = input.split("\\R");
		List<String> expressionLines = new ArrayList<>();
		List<String> settingsLines = new ArrayList<>();

		for (String rawLine : rawLines) {
			if (rawLine == null) {
				continue;
			}
			String trimmed = rawLine.trim();
			if (trimmed.isEmpty()) {
				continue;
			}
			if (trimmed.startsWith("#*")) {
				settingsLines.add(trimmed);
			} else {
				expressionLines.add(trimmed);
			}
		}

		if (expressionLines.isEmpty()) {
			throw new IllegalStateException("Expression cannot be blank.");
		}

		String expression = String.join("\n", expressionLines);
		return new InputParts(expression, settingsLines);
	}

	private static Map<String, AbilitySettingsOverrides> parseSettingsLines(List<String> settingsLines) {
		if (settingsLines == null || settingsLines.isEmpty()) {
			return Map.of();
		}

		Map<String, AbilitySettingsOverrides> mergedByLabel = new HashMap<>();

		for (String line : settingsLines) {
			if (line == null) {
				continue;
			}
			Matcher matcher = SETTINGS_LINE.matcher(line.trim());
			if (!matcher.matches()) {
				logger.warn("Ignoring malformed per-instance settings line: '{}'", line);
				continue;
			}

			String label = matcher.group(1);
			String payload = matcher.group(2).trim();
			if (payload.isEmpty()) {
				logger.warn("Ignoring per-instance settings line with no key/value pairs: '{}'", line);
				continue;
			}

			AbilitySettingsOverrides delta = parseSettingsPayload(payload, line);
			if (delta == null || delta.isEmpty()) {
				continue;
			}

			AbilitySettingsOverrides existing = mergedByLabel.get(label);
			mergedByLabel.put(label, mergeOverrides(existing, delta));
		}

		return Map.copyOf(mergedByLabel);
	}

	private static AbilitySettingsOverrides mergeOverrides(AbilitySettingsOverrides existing,
	                                                       AbilitySettingsOverrides delta) {
		if (existing == null) {
			return delta;
		}

		AbilitySettingsOverrides.Builder builder = AbilitySettingsOverrides.builder();

		String type = delta.getTypeOverride().orElse(existing.getTypeOverride().orElse(null));
		Integer level = delta.getLevelOverride().orElse(existing.getLevelOverride().orElse(null));
		Boolean triggersGcd = delta.getTriggersGcdOverride().orElse(existing.getTriggersGcdOverride().orElse(null));
		Short castDuration = delta.getCastDurationOverride().orElse(existing.getCastDurationOverride().orElse(null));
		Short cooldown = delta.getCooldownOverride().orElse(existing.getCooldownOverride().orElse(null));
		Double threshold = delta.getDetectionThresholdOverride().orElse(existing.getDetectionThresholdOverride().orElse(null));
		String mask = delta.getMaskOverride().orElse(existing.getMaskOverride().orElse(null));

		builder.type(type)
				.level(level)
				.triggersGcd(triggersGcd)
				.castDuration(castDuration)
				.cooldown(cooldown)
				.detectionThreshold(threshold)
				.mask(mask);

		return builder.build();
	}

	private static AbilitySettingsOverrides parseSettingsPayload(String payload, String sourceLine) {
		String[] parts = payload.split("\\s+");
		AbilitySettingsOverrides.Builder builder = AbilitySettingsOverrides.builder();
		boolean any = false;

		for (String part : parts) {
			if (part.isBlank()) {
				continue;
			}
			int equalsIndex = part.indexOf('=');
			if (equalsIndex <= 0 || equalsIndex == part.length() - 1) {
				logger.warn("Ignoring malformed key/value pair '{}' in settings line '{}'", part, sourceLine);
				continue;
			}

			String rawKey = part.substring(0, equalsIndex);
			String rawValue = part.substring(equalsIndex + 1);
			String key = rawKey.trim().toLowerCase(Locale.ROOT);
			String value = rawValue.trim();
			if (key.isEmpty() || value.isEmpty()) {
				logger.warn("Ignoring malformed key/value pair '{}' in settings line '{}'", part, sourceLine);
				continue;
			}

			switch (key) {
				case "triggers_gcd", "gcd_cooldown" -> {
					Boolean parsed = parseBooleanStrict(value);
					if (parsed == null) {
						logger.warn("Ignoring invalid boolean '{}' for key '{}' in settings line '{}'", value, rawKey, sourceLine);
						continue;
					}
					builder.triggersGcd(parsed);
					any = true;
				}
				case "cast_duration" -> {
					Short parsed = parseShortTiming(value);
					if (parsed == null) {
						logger.warn("Ignoring invalid cast_duration '{}' in settings line '{}'", value, sourceLine);
						continue;
					}
					builder.castDuration(parsed);
					any = true;
				}
				case "cooldown" -> {
					Short parsed = parseShortTiming(value);
					if (parsed == null) {
						logger.warn("Ignoring invalid cooldown '{}' in settings line '{}'", value, sourceLine);
						continue;
					}
					builder.cooldown(parsed);
					any = true;
				}
				case "detection_threshold" -> {
					Double parsed = parseThreshold(value);
					if (parsed == null) {
						logger.warn("Ignoring invalid detection_threshold '{}' in settings line '{}'", value, sourceLine);
						continue;
					}
					builder.detectionThreshold(parsed);
					any = true;
				}
				case "mask" -> {
					builder.mask(value);
					any = true;
				}
				case "type" -> {
					builder.type(value);
					any = true;
				}
				case "level" -> {
					Integer parsed = parseNonNegativeInt(value);
					if (parsed == null) {
						logger.warn("Ignoring invalid level '{}' in settings line '{}'", value, sourceLine);
						continue;
					}
					builder.level(parsed);
					any = true;
				}
				default -> logger.warn("Ignoring unknown settings key '{}' in settings line '{}'", rawKey, sourceLine);
			}
		}

		return any ? builder.build() : null;
	}

	private static Boolean parseBooleanStrict(String value) {
		if (value == null) {
			return null;
		}
		String normalized = value.trim().toLowerCase(Locale.ROOT);
		return switch (normalized) {
			case "true" -> Boolean.TRUE;
			case "false" -> Boolean.FALSE;
			default -> null;
		};
	}

	private static Integer parseNonNegativeInt(String value) {
		try {
			int parsed = Integer.parseInt(value);
			return Math.max(0, parsed);
		} catch (NumberFormatException e) {
			return null;
		}
	}

	private static Short parseShortTiming(String value) {
		try {
			int parsed = Integer.parseInt(value);
			if (parsed < 0 || parsed > Short.MAX_VALUE) {
				return null;
			}
			return (short) parsed;
		} catch (NumberFormatException e) {
			return null;
		}
	}

	private static Double parseThreshold(String value) {
		try {
			Double parsed = Double.parseDouble(value);
			return com.lansoftprogramming.runeSequence.core.sequence.model.AbilityValueSanitizers.sanitizeDetectionThreshold(parsed);
		} catch (NumberFormatException e) {
			return null;
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

	private record InputParts(String expression, List<String> settingsLines) {
	}

	private record ParsedAbility(String abilityName, String instanceLabel) {
	}
}