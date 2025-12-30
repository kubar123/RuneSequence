package com.lansoftprogramming.runeSequence.ui.presetManager.service;

import com.lansoftprogramming.runeSequence.core.sequence.model.*;
import com.lansoftprogramming.runeSequence.core.sequence.modifier.AbilityModifierEngine;
import com.lansoftprogramming.runeSequence.core.sequence.parser.SequenceParser;
import com.lansoftprogramming.runeSequence.core.sequence.parser.TooltipGrammar;
import com.lansoftprogramming.runeSequence.core.sequence.parser.TooltipMarkupParser;
import com.lansoftprogramming.runeSequence.core.sequence.parser.TooltipStructure;
import com.lansoftprogramming.runeSequence.ui.presetManager.model.SequenceElement;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.*;

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
			SequenceDefinition definition = SequenceParser.parse(cleanedExpression, overridesByLabel, null);
			Map<String, AbilitySettingsOverrides> effectiveOverridesByLabel = collectOverridesByLabel(definition);

			// Convert AST to visual elements
			List<SequenceElement> baseElements = convertDefinitionToElements(definition, effectiveOverridesByLabel);
			List<SequenceElement> withTooltips = tooltipMarkupParser.insertTooltips(
					baseElements,
					parseResult.tooltipPlacements(),
					SequenceElement::tooltip
			);
			return collapseAbilityModifiers(withTooltips);
		} catch (Exception e) {
			logger.error("Failed to parse expression with tooltip handling: {}", expression, e);
			try {
				SequenceDefinition fallbackDefinition = SequenceParser.parse(expression, overridesByLabel, null);
				List<SequenceElement> fallbackElements = convertDefinitionToElements(fallbackDefinition, collectOverridesByLabel(fallbackDefinition));
				return collapseAbilityModifiers(fallbackElements);
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
				AbilityToken abilityToken = AbilityToken.parse(element.abilityName());
				String instanceLabel = abilityToken.getInstanceLabel().orElse(null);
				AbilitySettingsOverrides overrides = instanceLabel != null && overridesByLabel != null
						? overridesByLabel.get(instanceLabel)
						: null;
				elements.add(SequenceElement.ability(abilityToken.getAbilityKey(), instanceLabel, overrides));
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

	private Map<String, AbilitySettingsOverrides> collectOverridesByLabel(SequenceDefinition definition) {
		if (definition == null) {
			return Map.of();
		}

		Map<String, AbilitySettingsOverrides> collected = new LinkedHashMap<>();
		for (Step step : definition.getSteps()) {
			if (step == null) {
				continue;
			}
			for (Term term : step.getTerms()) {
				if (term == null) {
					continue;
				}
				for (Alternative alt : term.getAlternatives()) {
					collectOverridesFromAlternative(alt, collected);
				}
			}
		}

		return collected.isEmpty() ? Map.of() : Map.copyOf(collected);
	}

	private void collectOverridesFromAlternative(Alternative alt, Map<String, AbilitySettingsOverrides> out) {
		if (alt == null) {
			return;
		}
		if (alt.isToken()) {
			String label = alt.getInstanceLabel();
			AbilitySettingsOverrides overrides = alt.getAbilitySettingsOverrides();
			if (label != null && !label.isBlank() && overrides != null && !overrides.isEmpty()) {
				out.put(label, overrides);
			}
			return;
		}
		if (!alt.isGroup() || alt.getSubgroup() == null) {
			return;
		}
		for (Step step : alt.getSubgroup().getSteps()) {
			if (step == null) {
				continue;
			}
			for (Term term : step.getTerms()) {
				if (term == null) {
					continue;
				}
				for (Alternative nested : term.getAlternatives()) {
					collectOverridesFromAlternative(nested, out);
				}
			}
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
		List<SequenceElement> elements = parseToVisualElements(expression, null);
		return elements.stream()
				.filter(SequenceElement::isAbility)
				.map(SequenceElement::getAbilityKey)
				.filter(java.util.Objects::nonNull)
				.toList();
	}

	private List<SequenceElement> collapseAbilityModifiers(List<SequenceElement> elements) {
		if (elements == null || elements.isEmpty()) {
			return elements != null ? elements : List.of();
		}

		List<SequenceElement> out = new ArrayList<>(elements.size());
		int index = 0;
		while (index < elements.size()) {
			CollapseResult collapsed = tryCollapseModifierChain(elements, index);
			if (collapsed != null) {
				out.add(collapsed.element());
				index = collapsed.nextIndex();
				continue;
			}
			out.add(elements.get(index));
			index++;
		}

		return List.copyOf(out);
	}

	private CollapseResult tryCollapseModifierChain(List<SequenceElement> elements, int startIndex) {
		if (elements == null || startIndex < 0 || startIndex >= elements.size()) {
			return null;
		}

		SequenceElement first = elements.get(startIndex);
		if (first == null || !first.isAbility()) {
			return null;
		}
		if (AbilityModifierEngine.resolve(first.getAbilityKey()) == null) {
			return null;
		}

		List<String> modifiers = new ArrayList<>();
		int cursor = startIndex;

		while (true) {
			if (cursor >= elements.size()) {
				return null;
			}

			SequenceElement modifierElement = elements.get(cursor);
			if (modifierElement == null || !modifierElement.isAbility()) {
				return null;
			}
			AbilityModifierEngine.ModifierDefinition definition = AbilityModifierEngine.resolve(modifierElement.getAbilityKey());
			if (definition == null) {
				return null;
			}
			modifiers.add(definition.canonicalKey());

			int plusIndex = cursor + 1;
			int nextAbilityIndex = cursor + 2;
			if (nextAbilityIndex >= elements.size()) {
				return null;
			}

			SequenceElement plus = elements.get(plusIndex);
			SequenceElement next = elements.get(nextAbilityIndex);

			if (plus == null || !plus.isPlus() || next == null || !next.isAbility()) {
				return null;
			}

			if (AbilityModifierEngine.resolve(next.getAbilityKey()) != null) {
				cursor = nextAbilityIndex;
				continue;
			}

			String targetKey = next.getResolvedAbilityKey();
			if (targetKey == null || targetKey.isBlank()) {
				return null;
			}
			String normalizedTarget = targetKey.trim().toLowerCase();
			for (String modifierKey : modifiers) {
				AbilityModifierEngine.ModifierDefinition modDef = AbilityModifierEngine.resolve(modifierKey);
				if (modDef == null || !modDef.targets().contains(normalizedTarget)) {
					return null;
				}
			}

			List<String> combined = new ArrayList<>(next.getAbilityModifiers());
			combined.addAll(modifiers);

			return new CollapseResult(next.withAbilityModifiers(combined), nextAbilityIndex + 1);
		}
	}

	private record CollapseResult(SequenceElement element, int nextIndex) {
	}
}
