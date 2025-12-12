package com.lansoftprogramming.runeSequence.ui.presetManager.service;

import com.lansoftprogramming.runeSequence.core.sequence.model.AbilitySettingsOverrides;
import com.lansoftprogramming.runeSequence.core.sequence.model.SequenceDefinition;
import com.lansoftprogramming.runeSequence.core.sequence.parser.SequenceParser;
import com.lansoftprogramming.runeSequence.core.sequence.parser.TooltipGrammar;
import com.lansoftprogramming.runeSequence.core.sequence.parser.TooltipMarkupParser;
import com.lansoftprogramming.runeSequence.core.sequence.parser.TooltipStructure;
import com.lansoftprogramming.runeSequence.ui.presetManager.model.SequenceElement;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Service for converting parsed sequence definitions into visual elements for display.
 * This service bridges the gap between the sequence model (AST) and the GUI representation.
 */
public class SequenceVisualService {
	private static final Logger logger = LoggerFactory.getLogger(SequenceVisualService.class);
	private final TooltipMarkupParser tooltipMarkupParser;

	public SequenceVisualService() {
		this(new TooltipMarkupParser());
	}

	public SequenceVisualService(Set<String> abilityNames) {
		this(new TooltipMarkupParser(abilityNames));
	}

	public SequenceVisualService(TooltipMarkupParser tooltipMarkupParser) {
		this.tooltipMarkupParser = tooltipMarkupParser;
	}

	/**
	 * Converts a sequence expression string into a list of visual elements.
	 * This handles the full parsing pipeline: expression -> AST -> visual elements.
	 *
	 * @param expression The sequence expression string (e.g., "ability1 -> ability2 + ability3")
	 * @return List of SequenceElement objects representing the visual structure
	 */
	public List<SequenceElement> parseToVisualElements(String expression) {
		return parseToVisualElements(expression, null);
	}

	/**
	 * Converts a sequence expression string into a list of visual elements and attaches any known
	 * per-instance overrides keyed by instance label.
	 *
	 * @param expression The sequence expression string (e.g., "ability1 -> ability2 + ability3")
	 * @param overridesByLabel Map of instanceLabel -> AbilitySettingsOverrides (may be null)
	 * @return List of SequenceElement objects representing the visual structure
	 */
	public List<SequenceElement> parseToVisualElements(String expression,
	                                                   Map<String, AbilitySettingsOverrides> overridesByLabel) {
		if (expression == null || expression.trim().isEmpty()) {
			logger.debug("Empty expression provided");
			return new ArrayList<>();
		}

		try {
			TooltipMarkupParser.ParseResult parseResult = tooltipMarkupParser.parse(expression);
			String cleanedExpression = parseResult.cleanedExpression();

			if (cleanedExpression == null || cleanedExpression.trim().isEmpty()) {
				return tooltipMarkupParser.insertTooltips(
						new ArrayList<>(),
						parseResult.tooltipPlacements(),
						SequenceElement::tooltip
				);
			}

			// Parse expression into AST without tooltip annotations
			SequenceDefinition definition = SequenceParser.parse(cleanedExpression);

			// Convert AST to visual elements
			List<SequenceElement> baseElements = convertDefinitionToElements(definition, overridesByLabel);
			return tooltipMarkupParser.insertTooltips(
					baseElements,
					parseResult.tooltipPlacements(),
					SequenceElement::tooltip
			);
		} catch (Exception e) {
			logger.error("Failed to parse expression with tooltip handling: {}", expression, e);
			try {
				SequenceDefinition fallbackDefinition = SequenceParser.parse(expression);
				return convertDefinitionToElements(fallbackDefinition, overridesByLabel);
			} catch (Exception fallback) {
				logger.error("Fallback parse without tooltip stripping also failed for expression: {}", expression, fallback);
			}
			return new ArrayList<>();
		}
	}

	/**
	 * Converts a SequenceDefinition (AST) into a flat list of visual elements.
	 * This traverses the AST and creates appropriate visual elements for abilities and separators.
	 *
	 * @param definition The parsed sequence definition
	 * @return List of visual elements with abilities and separators
	 */
	private List<SequenceElement> convertDefinitionToElements(SequenceDefinition definition) {
		return convertDefinitionToElements(definition, null);
	}

	private List<SequenceElement> convertDefinitionToElements(SequenceDefinition definition,
	                                                          Map<String, AbilitySettingsOverrides> overridesByLabel) {
		List<SequenceElement> elements = new ArrayList<>();
		List<TooltipStructure.StructuralElement> structure = TooltipStructure.linearize(definition);

		for (TooltipStructure.StructuralElement element : structure) {
			if (element.isAbility()) {
				AbilityTokenParts parts = parseAbilityToken(element.abilityName());
				AbilitySettingsOverrides overrides = parts.instanceLabel() != null && overridesByLabel != null
						? overridesByLabel.get(parts.instanceLabel())
						: null;
				elements.add(SequenceElement.ability(parts.abilityKey(), parts.instanceLabel(), overrides));
			} else if (element.isOperator()) {
				char symbol = element.operatorSymbol();
				if (symbol == TooltipGrammar.ARROW) {
					elements.add(SequenceElement.arrow());
				} else if (symbol == TooltipGrammar.AND) {
					elements.add(SequenceElement.plus());
				} else if (symbol == TooltipGrammar.OR) {
					elements.add(SequenceElement.slash());
				}
			}
		}

		logger.debug("Converted definition to {} visual elements", elements.size());
		return elements;
	}

	/**
	 * Extracts just the ability keys from a sequence expression.
	 * This is useful when you only need the ability names without separators.
	 *
	 * @param expression The sequence expression string
	 * @return List of ability keys in order
	 */
	public List<String> extractAbilityKeys(String expression) {
		List<SequenceElement> elements = parseToVisualElements(expression, null);
		return elements.stream()
				.filter(SequenceElement::isAbility)
				.map(SequenceElement::getAbilityKey)
				.filter(java.util.Objects::nonNull)
				.toList();
	}

	private AbilityTokenParts parseAbilityToken(String token) {
		if (token == null || token.isBlank()) {
			return new AbilityTokenParts(token, null);
		}
		Matcher matcher = INSTANCE_LABEL_PATTERN.matcher(token);
		if (matcher.matches()) {
			return new AbilityTokenParts(matcher.group(1), matcher.group(2));
		}
		return new AbilityTokenParts(token, null);
	}

	private record AbilityTokenParts(String abilityKey, String instanceLabel) {
	}

	private static final Pattern INSTANCE_LABEL_PATTERN = Pattern.compile("(.+?)\\[\\*(\\d+)\\]$");
}
