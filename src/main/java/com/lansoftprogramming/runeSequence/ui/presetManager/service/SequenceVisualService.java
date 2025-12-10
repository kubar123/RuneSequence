package com.lansoftprogramming.runeSequence.ui.presetManager.service;

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
import java.util.Set;

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
			List<SequenceElement> baseElements = convertDefinitionToElements(definition);
			return tooltipMarkupParser.insertTooltips(
					baseElements,
					parseResult.tooltipPlacements(),
					SequenceElement::tooltip
			);
		} catch (Exception e) {
			logger.error("Failed to parse expression with tooltip handling: {}", expression, e);
			try {
				SequenceDefinition fallbackDefinition = SequenceParser.parse(expression);
				return convertDefinitionToElements(fallbackDefinition);
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
		List<SequenceElement> elements = new ArrayList<>();
		List<TooltipStructure.StructuralElement> structure = TooltipStructure.linearize(definition);

		for (TooltipStructure.StructuralElement element : structure) {
			if (element.isAbility()) {
				elements.add(SequenceElement.ability(element.abilityName()));
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
		List<SequenceElement> elements = parseToVisualElements(expression);
		return elements.stream()
				.filter(SequenceElement::isAbility)
				.map(SequenceElement::getValue)
				.toList();
	}
}
