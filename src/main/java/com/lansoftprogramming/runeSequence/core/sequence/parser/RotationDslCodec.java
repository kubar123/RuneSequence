package com.lansoftprogramming.runeSequence.core.sequence.parser;

import com.lansoftprogramming.runeSequence.core.sequence.model.AbilitySettingsOverrides;
import com.lansoftprogramming.runeSequence.core.sequence.model.AbilityToken;
import com.lansoftprogramming.runeSequence.core.sequence.model.SequenceDefinition;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Helper for importing and exporting the rotation DSL while keeping per-instance settings lines
 * (`#*N key=value ...`), per-ability settings lines (`#@abilityKey key=value ...`), and label suffixes (`[*N]`)
 * consistent.
 */
public final class RotationDslCodec {
	private static final Logger logger = LoggerFactory.getLogger(RotationDslCodec.class);
	private static final Pattern SETTINGS_LINE = Pattern.compile("^#\\*(\\d+)\\s+(.+)$");
	private static final Pattern PER_ABILITY_SETTINGS_LINE = Pattern.compile("^#@([^\\s]+)\\s+(.+)$");

	private RotationDslCodec() {
	}

	public record ParsedRotation(String expression,
	                            Map<String, AbilitySettingsOverrides> perInstanceOverrides,
	                            Map<String, AbilitySettingsOverrides> perAbilityOverrides) {
	}

	/**
	 * Splits an import payload into its expression and merged per-instance overrides map.
	 * Duplicate labels are merged with later key/value pairs overriding earlier ones.
	 * Malformed settings lines are ignored with warnings.
	 */
	public static ParsedRotation parse(String input) {
		if (input == null) {
			throw new IllegalStateException("Expression cannot be null.");
		}

		String normalizedInput = ExpressionSanitizer.stripSurroundingQuotes(ExpressionSanitizer.removeInvisibles(input));
		String[] rawLines = normalizedInput.split("\\R");
		List<String> expressionLines = new ArrayList<>();
		List<String> perInstanceSettingsLines = new ArrayList<>();
		List<String> perAbilitySettingsLines = new ArrayList<>();

		for (String rawLine : rawLines) {
			if (rawLine == null) {
				continue;
			}
			String trimmed = ExpressionSanitizer.removeInvisibles(rawLine).trim();
			if (trimmed.isEmpty()) {
				continue;
			}
			if (trimmed.startsWith("#*")) {
				perInstanceSettingsLines.add(trimmed);
			} else if (trimmed.startsWith("#@")) {
				perAbilitySettingsLines.add(trimmed);
			} else {
				expressionLines.add(trimmed);
			}
		}

		if (expressionLines.isEmpty()) {
			throw new IllegalStateException("Expression cannot be blank.");
		}

		String expression = String.join("\n", expressionLines);
		Map<String, AbilitySettingsOverrides> overridesByLabel = parseSettingsLines(perInstanceSettingsLines);
		Map<String, AbilitySettingsOverrides> overridesByAbility = parsePerAbilitySettingsLines(perAbilitySettingsLines);
		return new ParsedRotation(expression, overridesByLabel, overridesByAbility);
	}

	/**
	 * Exports a label-free expression with no `#*` lines, suitable for external tools.
	 */
	public static String exportSimple(String input) {
		ParsedRotation parsed = parse(input);
		return renderExpressionWithoutLabels(parsed.expression());
	}

	/**
	 * Exports a deep rotation string: expression (with labels) plus `#*N` settings lines derived from
	 * the provided per-instance overrides map and optional `#@abilityKey` settings lines derived from
	 * the provided per-ability overrides map. Only labels/ability keys present in the expression are emitted.
	 */
	public static String exportDeep(String input, Map<String, AbilitySettingsOverrides> perInstanceOverrides) {
		return exportDeep(input, perInstanceOverrides, null);
	}

	/**
	 * Exports a deep rotation string: expression (with labels) plus `#@abilityKey` and `#*N` settings lines derived
	 * from the provided overrides maps. Only abilities/labels present in the expression are emitted.
	 */
	public static String exportDeep(String input,
	                               Map<String, AbilitySettingsOverrides> perInstanceOverrides,
	                               Map<String, AbilitySettingsOverrides> perAbilityOverrides) {
		ParsedRotation parsed = parse(input);
		String expression = parsed.expression().trim();
		boolean hasPerInstance = perInstanceOverrides != null && !perInstanceOverrides.isEmpty();
		boolean hasPerAbility = perAbilityOverrides != null && !perAbilityOverrides.isEmpty();
		if (!hasPerInstance && !hasPerAbility) {
			return expression;
		}

		Set<String> labelsInExpression = collectLabelsInExpression(expression);
		Set<String> abilitiesInExpression = collectAbilityKeysInExpression(expression);

		Map<String, AbilitySettingsOverrides> filteredPerAbility = new LinkedHashMap<>();
		if (hasPerAbility && !abilitiesInExpression.isEmpty()) {
			for (Map.Entry<String, AbilitySettingsOverrides> entry : perAbilityOverrides.entrySet()) {
				String abilityKey = entry.getKey();
				AbilitySettingsOverrides overrides = entry.getValue();
				if (abilityKey == null || abilityKey.isBlank() || overrides == null || overrides.isEmpty()) {
					continue;
				}
				if (!abilitiesInExpression.contains(abilityKey)) {
					logger.warn("Ignoring per-ability override for '{}' with no matching token in expression.", abilityKey);
					continue;
				}
				filteredPerAbility.put(abilityKey, overrides);
			}
		}

		Map<String, AbilitySettingsOverrides> filteredPerInstance = new LinkedHashMap<>();
		if (hasPerInstance && !labelsInExpression.isEmpty()) {
			for (Map.Entry<String, AbilitySettingsOverrides> entry : perInstanceOverrides.entrySet()) {
				String label = entry.getKey();
				AbilitySettingsOverrides overrides = entry.getValue();
				if (label == null || label.isBlank() || overrides == null || overrides.isEmpty()) {
					continue;
				}
				if (!labelsInExpression.contains(label)) {
					logger.warn("Ignoring per-instance override for label '{}' with no matching label in expression.", label);
					continue;
				}
				filteredPerInstance.put(label, overrides);
			}
		}

		if (filteredPerAbility.isEmpty() && filteredPerInstance.isEmpty()) {
			return expression;
		}

		StringBuilder out = new StringBuilder(expression);

		if (!filteredPerAbility.isEmpty()) {
			List<String> abilityKeys = new ArrayList<>(filteredPerAbility.keySet());
			abilityKeys.sort(String::compareTo);
			for (String abilityKey : abilityKeys) {
				if (containsWhitespace(abilityKey)) {
					logger.warn("Skipping per-ability DSL export for key '{}' containing whitespace.", abilityKey);
					continue;
				}
				String payload = serializeOverridesPayload(filteredPerAbility.get(abilityKey));
				if (payload == null || payload.isBlank()) {
					continue;
				}
				out.append("\n#@").append(abilityKey).append(' ').append(payload);
			}
		}

		if (!filteredPerInstance.isEmpty()) {
			List<String> labels = new ArrayList<>(filteredPerInstance.keySet());
			labels.sort(RotationDslCodec::compareLabelsNumericFirst);
			for (String label : labels) {
				String payload = serializeOverridesPayload(filteredPerInstance.get(label));
				if (payload == null || payload.isBlank()) {
					continue;
				}
				out.append("\n#*").append(label).append(' ').append(payload);
			}
		}

		return out.toString();
	}

	/**
	 * Collects any `[*N]` instance labels found in the expression (ignores tooltip annotations).
	 */
	public static Set<String> collectLabelsInExpression(String expression) {
		if (expression == null || expression.isBlank()) {
			return Set.of();
		}

		String cleaned;
		try {
			cleaned = new TooltipMarkupParser().parse(expression).cleanedExpression();
		} catch (Exception e) {
			cleaned = expression;
		}

		Set<String> labels = new HashSet<>();
		try {
			Tokenizer tokenizer = new Tokenizer();
			List<Token> tokens = tokenizer.tokenize(cleaned);
			for (Token token : tokens) {
				if (token instanceof Token.Ability abilityToken) {
					AbilityToken parsed = AbilityToken.parse(abilityToken.name());
					parsed.getInstanceLabel().ifPresent(labels::add);
				}
			}
			return Set.copyOf(labels);
		} catch (Exception ignored) {
			Matcher matcher = Pattern.compile("\\[\\*(\\d+)]").matcher(cleaned);
			while (matcher.find()) {
				labels.add(matcher.group(1));
			}
			return Set.copyOf(labels);
		}
	}

	/**
	 * Collects any base ability keys found in the expression (ignores tooltip annotations and instance labels).
	 */
	public static Set<String> collectAbilityKeysInExpression(String expression) {
		if (expression == null || expression.isBlank()) {
			return Set.of();
		}

		String cleaned;
		try {
			cleaned = new TooltipMarkupParser().parse(expression).cleanedExpression();
		} catch (Exception e) {
			cleaned = expression;
		}

		Set<String> abilities = new HashSet<>();
		try {
			Tokenizer tokenizer = new Tokenizer();
			List<Token> tokens = tokenizer.tokenize(cleaned);
			for (Token token : tokens) {
				if (token instanceof Token.Ability abilityToken) {
					AbilityToken parsed = AbilityToken.parse(abilityToken.name());
					String key = parsed.getAbilityKey();
					if (key != null && !key.isBlank()) {
						abilities.add(key);
					}
				}
			}
			return Set.copyOf(abilities);
		} catch (Exception ignored) {
			return Set.of();
		}
	}

	private static String renderExpressionWithoutLabels(String expression) {
		if (expression == null) {
			return "";
		}

		TooltipMarkupParser tooltipParser = new TooltipMarkupParser();
		TooltipMarkupParser.ParseResult tooltipResult;
		try {
			tooltipResult = tooltipParser.parse(expression);
		} catch (Exception e) {
			return stripLabelsFallback(expression);
		}

		String cleanedExpression = tooltipResult.cleanedExpression();
		if (cleanedExpression == null || cleanedExpression.isBlank()) {
			return "";
		}

		try {
			SequenceDefinition definition = SequenceParser.parse(cleanedExpression);
			List<String> baseElements = new ArrayList<>();
			for (TooltipStructure.StructuralElement element : TooltipStructure.linearize(definition)) {
				if (element.isAbility()) {
					AbilityToken parsed = AbilityToken.parse(element.abilityName());
					baseElements.add(AbilityToken.format(parsed.getAbilityKey(), null));
				} else if (element.isOperator() && element.operatorSymbol() != null) {
					baseElements.add(String.valueOf(element.operatorSymbol()));
				}
			}

			List<String> withTooltips = tooltipParser.insertTooltips(
					baseElements,
					tooltipResult.tooltipPlacements(),
					message -> "(" + TooltipGrammar.escapeTooltipText(message) + ")"
			);
			return String.join("", withTooltips);
		} catch (Exception e) {
			return stripLabelsFallback(expression);
		}
	}

	private static String stripLabelsFallback(String expression) {
		return expression != null ? expression.replaceAll("\\[\\*\\d+]", "") : "";
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

	private static Map<String, AbilitySettingsOverrides> parsePerAbilitySettingsLines(List<String> settingsLines) {
		if (settingsLines == null || settingsLines.isEmpty()) {
			return Map.of();
		}

		Map<String, AbilitySettingsOverrides> mergedByAbility = new HashMap<>();

		for (String line : settingsLines) {
			if (line == null) {
				continue;
			}
			Matcher matcher = PER_ABILITY_SETTINGS_LINE.matcher(line.trim());
			if (!matcher.matches()) {
				logger.warn("Ignoring malformed per-ability settings line: '{}'", line);
				continue;
			}

			String abilityKey = matcher.group(1);
			String payload = matcher.group(2).trim();
			if (payload.isEmpty()) {
				logger.warn("Ignoring per-ability settings line with no key/value pairs: '{}'", line);
				continue;
			}

			AbilitySettingsOverrides delta = parseSettingsPayload(payload, line);
			if (delta == null || delta.isEmpty()) {
				continue;
			}

			AbilitySettingsOverrides existing = mergedByAbility.get(abilityKey);
			mergedByAbility.put(abilityKey, mergeOverrides(existing, delta));
		}

		return Map.copyOf(mergedByAbility);
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

	private static String serializeOverridesPayload(AbilitySettingsOverrides overrides) {
		if (overrides == null || overrides.isEmpty()) {
			return null;
		}

		List<String> parts = new ArrayList<>();
		overrides.getTypeOverride().ifPresent(value -> addStringField(parts, "type", value));
		overrides.getLevelOverride().ifPresent(value -> parts.add("level=" + value));
		overrides.getTriggersGcdOverride().ifPresent(value -> parts.add("triggers_gcd=" + value.toString().toLowerCase(Locale.ROOT)));
		overrides.getCastDurationOverride().ifPresent(value -> parts.add("cast_duration=" + value));
		overrides.getCooldownOverride().ifPresent(value -> parts.add("cooldown=" + value));
		overrides.getDetectionThresholdOverride().ifPresent(value -> parts.add("detection_threshold=" + value));
		overrides.getMaskOverride().ifPresent(value -> addStringField(parts, "mask", value));

		if (parts.isEmpty()) {
			return null;
		}
		return String.join(" ", parts);
	}

	private static void addStringField(List<String> parts, String key, String value) {
		if (value == null || value.isBlank()) {
			return;
		}
		if (containsWhitespace(value)) {
			logger.warn("Skipping DSL export for key '{}' with whitespace value '{}'", key, value);
			return;
		}
		parts.add(key + "=" + value);
	}

	private static boolean containsWhitespace(String value) {
		for (int i = 0; i < value.length(); i++) {
			if (Character.isWhitespace(value.charAt(i))) {
				return true;
			}
		}
		return false;
	}

	private static int compareLabelsNumericFirst(String a, String b) {
		Integer ia = parseIntOrNull(a);
		Integer ib = parseIntOrNull(b);
		if (ia != null && ib != null) {
			return Integer.compare(ia, ib);
		}
		if (ia != null) {
			return -1;
		}
		if (ib != null) {
			return 1;
		}
		return a.compareTo(b);
	}

	private static Integer parseIntOrNull(String value) {
		if (value == null || value.isBlank()) {
			return null;
		}
		try {
			return Integer.parseInt(value.trim());
		} catch (NumberFormatException e) {
			return null;
		}
	}
}
