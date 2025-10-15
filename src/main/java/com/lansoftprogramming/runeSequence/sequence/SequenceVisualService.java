package com.lansoftprogramming.runeSequence.sequence;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.List;

/**
 * Service for converting parsed sequence definitions into visual elements for display.
 * This service bridges the gap between the sequence model (AST) and the GUI representation.
 */
public class SequenceVisualService {
	private static final Logger logger = LoggerFactory.getLogger(SequenceVisualService.class);

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
			// Parse expression into AST
			SequenceDefinition definition = SequenceParser.parse(expression);

			// Convert AST to visual elements
			return convertDefinitionToElements(definition);
		} catch (Exception e) {
			logger.error("Failed to parse expression: {}", expression, e);
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
		List<Step> steps = definition.getSteps();

		for (int stepIndex = 0; stepIndex < steps.size(); stepIndex++) {
			Step step = steps.get(stepIndex);
			List<Term> terms = step.getTerms();

			for (int termIndex = 0; termIndex < terms.size(); termIndex++) {
				Term term = terms.get(termIndex);
				List<Alternative> alternatives = term.getAlternatives();

				for (int altIndex = 0; altIndex < alternatives.size(); altIndex++) {
					Alternative alt = alternatives.get(altIndex);

					// Process the alternative
					processAlternative(alt, elements);

					// Add separator between alternatives (slash for OR)
					if (altIndex < alternatives.size() - 1) {
						elements.add(SequenceElement.slash());
					}
				}

				// Add separator between terms (plus for AND)
				if (termIndex < terms.size() - 1) {
					elements.add(SequenceElement.plus());
				}
			}

			// Add separator between steps (arrow for sequence)
			if (stepIndex < steps.size() - 1) {
				elements.add(SequenceElement.arrow());
			}
		}

		logger.debug("Converted definition to {} visual elements", elements.size());
		return elements;
	}

	/**
	 * Processes a single alternative, which can be either a token (ability) or a subgroup.
	 *
	 * @param alt The alternative to process
	 * @param elements The list to add elements to
	 */
	private void processAlternative(Alternative alt, List<SequenceElement> elements) {
		if (alt.isToken()) {
			// Simple ability token
			elements.add(SequenceElement.ability(alt.getToken()));
		} else if (alt.isGroup()) {
			// Nested subgroup - recursively process
			List<SequenceElement> subElements = convertDefinitionToElements(alt.getSubgroup());
			elements.addAll(subElements);
		}
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
