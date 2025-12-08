package com.lansoftprogramming.runeSequence.core.sequence.parser;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Parses tooltip annotations from a raw expression string without altering the core sequence structure.
 * <p>
 * Tooltip segments use the syntax {@code (message)} adjacent to abilities. They are stripped before
 * handing the expression to the core {@link SequenceParser}, then re-mapped to structural indices so
 * callers can re-inject tooltip elements in visual models or runtime schedules.
 */
public class TooltipMarkupParser {
	private static final Logger logger = LoggerFactory.getLogger(TooltipMarkupParser.class);
	private static final String PLACEHOLDER_PREFIX = "__tooltip";
	private static final String PLACEHOLDER_SUFFIX = "__";

	/**
	 * Extracts tooltip annotations from the given expression.
	 *
	 * @param expression raw expression that may contain tooltip segments
	 * @return result containing the expression with tooltips removed and placement instructions
	 */
	public ParseResult parse(String expression) {
		if (expression == null || expression.isBlank()) {
			return new ParseResult("", List.of());
		}

		StringBuilder cleanedExpression = new StringBuilder(expression.length());
		StringBuilder placeholderExpression = new StringBuilder(expression.length());
		List<TooltipToken> tooltipTokens = new ArrayList<>();

		int index = 0;
		while (index < expression.length()) {
			char current = expression.charAt(index);

			if (current == '(' && !isEscaped(expression, index)) {
				TooltipCandidate candidate = readTooltipCandidate(expression, index + 1);
				if (candidate != null) {
					String placeholder = PLACEHOLDER_PREFIX + tooltipTokens.size() + PLACEHOLDER_SUFFIX;
					char nextNonWhitespace = nextNonWhitespace(expression, candidate.endIndex() + 1);

					maybeInsertGap(cleanedExpression, nextNonWhitespace);
					appendPlaceholderWithSpacing(placeholderExpression, placeholder, nextNonWhitespace);

					tooltipTokens.add(new TooltipToken(placeholder, candidate.message()));
					index = candidate.endIndex() + 1;
					continue;
				}
			}

			cleanedExpression.append(current);
			placeholderExpression.append(current);
			index++;
		}

		List<TooltipPlacement> placements = mapPlacements(placeholderExpression.toString(), tooltipTokens);
		return new ParseResult(cleanedExpression.toString(), placements);
	}

	/**
	 * Inserts tooltip elements into the provided base element list according to the computed placements.
	 */
	public <T> List<T> insertTooltips(List<T> baseElements, List<TooltipPlacement> placements, TooltipFactory<T> factory) {
		if (placements == null || placements.isEmpty()) {
			return baseElements;
		}
		if (baseElements == null) {
			baseElements = List.of();
		}

		List<T> result = new ArrayList<>(baseElements);
		int offset = 0;

		for (TooltipPlacement placement : placements) {
			if (placement == null) {
				continue;
			}
			int insertionIndex = Math.max(0, Math.min(placement.insertionIndex(), result.size()));
			result.add(insertionIndex + offset, factory.createTooltipElement(placement.message()));
			offset++;
		}

		return result;
	}

	private TooltipCandidate readTooltipCandidate(String expression, int startIndex) {
		StringBuilder messageBuilder = new StringBuilder();
		int cursor = startIndex;
		boolean foundClosing = false;

		while (cursor < expression.length()) {
			char current = expression.charAt(cursor);

			if (current == '\\' && cursor + 1 < expression.length()) {
				char next = expression.charAt(cursor + 1);
				if (next == '(' || next == ')') {
					messageBuilder.append(next);
					cursor += 2;
					continue;
				}
			}

			if (current == '(') {
				return null; // unescaped nested paren not allowed inside tooltip text
			}

			if (current == ')' && !isEscaped(expression, cursor)) {
				foundClosing = true;
				break;
			}

			messageBuilder.append(current);
			cursor++;
		}

		if (!foundClosing) {
			return null;
		}

		String message = messageBuilder.toString();
		if (message.isEmpty()) {
			return null;
		}

		if (containsStructuralOperator(message)) {
			return null;
		}

		return new TooltipCandidate(message, cursor);
	}

	private boolean containsStructuralOperator(String text) {
		for (int i = 0; i < text.length(); i++) {
			char c = text.charAt(i);
			if (c == '→' || c == '+' || c == '/') {
				return true;
			}
		}
		return false;
	}

	private boolean isEscaped(String expression, int index) {
		int backslashCount = 0;
		int cursor = index - 1;
		while (cursor >= 0 && expression.charAt(cursor) == '\\') {
			backslashCount++;
			cursor--;
		}
		return backslashCount % 2 != 0;
	}

	private char nextNonWhitespace(String expression, int startIndex) {
		for (int i = startIndex; i < expression.length(); i++) {
			char c = expression.charAt(i);
			if (!Character.isWhitespace(c)) {
				return c;
			}
		}
		return 0;
	}

	private Character lastNonWhitespace(StringBuilder builder) {
		for (int i = builder.length() - 1; i >= 0; i--) {
			char c = builder.charAt(i);
			if (!Character.isWhitespace(c)) {
				return c;
			}
		}
		return null;
	}

	private boolean isBoundary(char c) {
		return Character.isWhitespace(c) || c == '→' || c == '+' || c == '/' || c == '(' || c == ')';
	}

	private void maybeInsertGap(StringBuilder cleanedExpression, char nextNonWhitespace) {
		Character last = lastNonWhitespace(cleanedExpression);

		if (last == null || nextNonWhitespace == 0) {
			return;
		}

		if (!isBoundary(last) && !isBoundary(nextNonWhitespace)) {
			cleanedExpression.append(' ');
		}
	}

	private void appendPlaceholderWithSpacing(StringBuilder builder, String placeholder, char nextNonWhitespace) {
		Character last = lastNonWhitespace(builder);
		if (last != null && !isBoundary(last)) {
			builder.append(' ');
		}

		builder.append(placeholder);

		if (nextNonWhitespace != 0 && !isBoundary(nextNonWhitespace)) {
			builder.append(' ');
		}
	}

	private List<TooltipPlacement> mapPlacements(String placeholderExpression, List<TooltipToken> tooltipTokens) {
		if (tooltipTokens.isEmpty()) {
			return List.of();
		}

		Pattern placeholderPattern = Pattern.compile(Pattern.quote(PLACEHOLDER_PREFIX) + "\\d+" + Pattern.quote(PLACEHOLDER_SUFFIX));
		Map<String, String> messageByPlaceholder = new HashMap<>();
		for (TooltipToken token : tooltipTokens) {
			messageByPlaceholder.put(token.placeholder(), token.message());
		}

		List<TooltipPlacement> placements = new ArrayList<>();
		Tokenizer tokenizer = new Tokenizer();
		List<Token> tokens = tokenizer.tokenize(placeholderExpression);

		int structuralIndex = 0;
		for (Token token : tokens) {
			if (token instanceof Token.Ability ability) {
				String abilityName = ability.name();
				Matcher matcher = placeholderPattern.matcher(abilityName);
				int cursor = 0;
				boolean countedAbility = false;

				while (matcher.find()) {
					String before = abilityName.substring(cursor, matcher.start()).trim();
					if (!before.isEmpty() && !countedAbility) {
						structuralIndex++; // count the actual ability content once
						countedAbility = true;
					}

					String placeholderMessage = messageByPlaceholder.get(matcher.group());
					if (placeholderMessage != null) {
						placements.add(new TooltipPlacement(structuralIndex, placeholderMessage));
					}
					cursor = matcher.end();
				}

				String remaining = abilityName.substring(cursor).trim();
				if (!remaining.isEmpty() && !countedAbility) {
					structuralIndex++; // ability text after the final placeholder
					countedAbility = true;
				} else if (!countedAbility && !abilityName.isEmpty()) {
					// ability token without placeholders
					structuralIndex++;
				}
			} else if (token instanceof Token.Operator) {
				structuralIndex++;
			}
		}

		if (!placements.isEmpty() && logger.isDebugEnabled()) {
			logger.debug("Mapped {} tooltip placements", placements.size());
		}

		return placements;
	}

	private record TooltipCandidate(String message, int endIndex) {
	}

	private record TooltipToken(String placeholder, String message) {
	}

	public record TooltipPlacement(int insertionIndex, String message) {
	}

	public record ParseResult(String cleanedExpression, List<TooltipPlacement> tooltipPlacements) {
	}

	@FunctionalInterface
	public interface TooltipFactory<T> {
		T createTooltipElement(String message);
	}
}

