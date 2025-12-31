package com.lansoftprogramming.runeSequence.core.sequence.parser;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.*;
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
	private static final String KEYWORD_DROP = "drop";
	private final Set<String> abilityNames;

	public TooltipMarkupParser() {
		this(null);
	}

	public TooltipMarkupParser(Set<String> abilityNames) {
		this.abilityNames = abilityNames != null ? Set.copyOf(abilityNames) : Set.of();
	}

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

			if (current == '(' && !ParserEscapes.isEscaped(expression, index)) {
				TooltipCandidate candidate = readTooltipCandidate(expression, index + 1);
				if (candidate != null) {
					String placeholder = PLACEHOLDER_PREFIX + tooltipTokens.size() + PLACEHOLDER_SUFFIX;
					boolean hasExtraOuterParens = false;

					int extraOpenIndex = index > 0 && expression.charAt(index - 1) == '(' && !ParserEscapes.isEscaped(expression, index - 1)
							? index - 1
							: -1;
					int extraCloseIndex = candidate.endIndex() + 1 < expression.length()
							&& expression.charAt(candidate.endIndex() + 1) == ')'
							&& !ParserEscapes.isEscaped(expression, candidate.endIndex() + 1)
							? candidate.endIndex() + 1
							: -1;

					if (extraOpenIndex == index - 1 && extraCloseIndex == candidate.endIndex() + 1) {
						hasExtraOuterParens = true;
						if (cleanedExpression.length() > 0
								&& cleanedExpression.charAt(cleanedExpression.length() - 1) == '(') {
							cleanedExpression.setLength(cleanedExpression.length() - 1);
						}
						if (placeholderExpression.length() > 0
								&& placeholderExpression.charAt(placeholderExpression.length() - 1) == '(') {
							placeholderExpression.setLength(placeholderExpression.length() - 1);
						}
					}

					int lookaheadIndex = hasExtraOuterParens ? (extraCloseIndex + 1) : (candidate.endIndex() + 1);
					char nextNonWhitespace = nextNonWhitespace(expression, lookaheadIndex);

					maybeInsertGap(cleanedExpression, nextNonWhitespace);
					appendPlaceholderWithSpacing(placeholderExpression, placeholder, nextNonWhitespace);

					tooltipTokens.add(new TooltipToken(placeholder, candidate.message()));
					index = hasExtraOuterParens ? (extraCloseIndex + 1) : (candidate.endIndex() + 1);
					continue;
				}
			}

			KeywordTooltip keywordTooltip = readKeywordTooltip(expression, index);
			if (keywordTooltip != null) {
				String placeholder = PLACEHOLDER_PREFIX + tooltipTokens.size() + PLACEHOLDER_SUFFIX;
				char nextNonWhitespace = nextNonWhitespace(expression, keywordTooltip.nextTokenStartIndex());
				maybeInsertGap(cleanedExpression, nextNonWhitespace);
				appendPlaceholderWithSpacing(placeholderExpression, placeholder, nextNonWhitespace);
				tooltipTokens.add(new TooltipToken(placeholder, keywordTooltip.message()));
				index = keywordTooltip.nextTokenStartIndex();
				continue;
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
		int openIndex = startIndex - 1;

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

			if (current == '(' && !ParserEscapes.isEscaped(expression, cursor)) {
				if (logger.isDebugEnabled()) {
					logger.debug("Tooltip parser: nested '(' at {} inside candidate starting at {}, treating as structural group",
							cursor, openIndex);
				}
				return null; // unescaped nested paren not allowed inside tooltip text
			}

			if (current == ')' && !ParserEscapes.isEscaped(expression, cursor)) {
				foundClosing = true;
				break;
			}

			messageBuilder.append(current);
			cursor++;
		}

		if (!foundClosing) {
			if (logger.isDebugEnabled()) {
				logger.debug("Tooltip parser: no closing ')' found for candidate starting at {}, treating as structural group",
						openIndex);
			}
			return null;
		}

		String message = messageBuilder.toString();
		if (message.isEmpty()) {
			if (logger.isDebugEnabled()) {
				logger.debug("Tooltip parser: empty tooltip content between parentheses at {}", openIndex);
			}
			return null;
		}

		if (TooltipGrammar.containsStructuralOperators(message)) {
			if (logger.isDebugEnabled()) {
				logger.debug("Tooltip parser: segment '({})' starting at {} contains structural operators, treating as grouping",
						message, openIndex);
			}
			return null;
		}

		if (looksLikeAbilitySubExpression(message)) {
			if (logger.isDebugEnabled()) {
				logger.debug("Tooltip parser: segment '({})' starting at {} looks like an ability-like sub-expression, treating as grouping",
						message, openIndex);
			}
			return null;
		}

		return new TooltipCandidate(message, cursor);
	}

	private KeywordTooltip readKeywordTooltip(String expression, int startIndex) {
		if (expression == null || startIndex < 0 || startIndex >= expression.length()) {
			return null;
		}

		String message = null;
		int keywordEnd = -1;

		if (matchesWord(expression, startIndex, KEYWORD_DROP)) {
			message = "Drop";
			keywordEnd = startIndex + KEYWORD_DROP.length();
		} else if (matchesTickKeyword(expression, startIndex)) {
			int cursor = startIndex;
			while (cursor < expression.length() && Character.isDigit(expression.charAt(cursor))) {
				cursor++;
			}
			if (cursor >= expression.length()) {
				return null;
			}
			message = expression.substring(startIndex, cursor + 1);
			keywordEnd = cursor + 1;
		} else if (matchesSingleCharKeyword(expression, startIndex, 's')) {
			message = "s";
			keywordEnd = startIndex + 1;
		} else if (matchesSingleCharKeyword(expression, startIndex, 'r')) {
			message = "r";
			keywordEnd = startIndex + 1;
		} else {
			return null;
		}

		char prev = startIndex > 0 ? expression.charAt(startIndex - 1) : 0;
		if (prev != 0 && !TooltipGrammar.isStructuralBoundary(prev)) {
			return null;
		}

		char after = keywordEnd < expression.length() ? expression.charAt(keywordEnd) : 0;
		if (after != 0 && !TooltipGrammar.isStructuralBoundary(after)) {
			return null;
		}

		int cursor = keywordEnd;
		boolean sawWhitespace = false;
		while (cursor < expression.length() && Character.isWhitespace(expression.charAt(cursor))) {
			sawWhitespace = true;
			cursor++;
		}
		if (!sawWhitespace) {
			return null;
		}

		char next = cursor < expression.length() ? expression.charAt(cursor) : 0;
		if (next == 0 || TooltipGrammar.isStructuralBoundary(next)) {
			return null;
		}

		return new KeywordTooltip(message, cursor);
	}

	private boolean matchesTickKeyword(String expression, int startIndex) {
		if (startIndex < 0 || startIndex >= expression.length()) {
			return false;
		}
		char first = expression.charAt(startIndex);
		if (!Character.isDigit(first)) {
			return false;
		}

		int cursor = startIndex;
		while (cursor < expression.length() && Character.isDigit(expression.charAt(cursor))) {
			cursor++;
		}
		if (cursor >= expression.length()) {
			return false;
		}
		char suffix = expression.charAt(cursor);
		if (Character.toLowerCase(suffix) != 't') {
			return false;
		}

		char after = cursor + 1 < expression.length() ? expression.charAt(cursor + 1) : 0;
		return after == 0 || TooltipGrammar.isStructuralBoundary(after);
	}

	private boolean matchesSingleCharKeyword(String expression, int startIndex, char keyword) {
		if (startIndex < 0 || startIndex >= expression.length()) {
			return false;
		}
		char current = expression.charAt(startIndex);
		if (Character.toLowerCase(current) != Character.toLowerCase(keyword)) {
			return false;
		}
		char after = startIndex + 1 < expression.length() ? expression.charAt(startIndex + 1) : 0;
		return after == 0 || TooltipGrammar.isStructuralBoundary(after);
	}

	private boolean matchesWord(String expression, int startIndex, String word) {
		if (word == null || word.isEmpty()) {
			return false;
		}
		int end = startIndex + word.length();
		if (startIndex < 0 || end > expression.length()) {
			return false;
		}

		for (int i = 0; i < word.length(); i++) {
			char a = Character.toLowerCase(expression.charAt(startIndex + i));
			char b = Character.toLowerCase(word.charAt(i));
			if (a != b) {
				return false;
			}
		}
		char after = end < expression.length() ? expression.charAt(end) : 0;
		return after == 0 || TooltipGrammar.isStructuralBoundary(after);
	}

	private boolean looksLikeAbilitySubExpression(String text) {
		if (abilityNames.isEmpty() || text == null) {
			return false;
		}
		String candidate = text.trim();
		if (candidate.isEmpty()) {
			return false;
		}
		return abilityNames.contains(candidate);
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

	private void maybeInsertGap(StringBuilder cleanedExpression, char nextNonWhitespace) {
		Character last = lastNonWhitespace(cleanedExpression);

		if (last == null || nextNonWhitespace == 0) {
			return;
		}

		if (!TooltipGrammar.isStructuralBoundary(last) && !TooltipGrammar.isStructuralBoundary(nextNonWhitespace)) {
			cleanedExpression.append(' ');
		}
	}

	private void appendPlaceholderWithSpacing(StringBuilder builder, String placeholder, char nextNonWhitespace) {
		Character last = lastNonWhitespace(builder);
		if (last != null && !TooltipGrammar.isStructuralBoundary(last)) {
			builder.append(' ');
		}

		builder.append(placeholder);

		if (nextNonWhitespace != 0 && !TooltipGrammar.isStructuralBoundary(nextNonWhitespace)) {
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

	private record KeywordTooltip(String message, int nextTokenStartIndex) {
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
