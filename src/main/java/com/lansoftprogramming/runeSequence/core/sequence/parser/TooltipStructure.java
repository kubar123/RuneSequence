package com.lansoftprogramming.runeSequence.core.sequence.parser;

import com.lansoftprogramming.runeSequence.core.sequence.model.*;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/**
 * Builds a linear structural view of a {@link SequenceDefinition} for tooltip placement and visualization.
 * This keeps runtime schedule building and UI rendering aligned on the same traversal rules.
 */
public final class TooltipStructure {
	private TooltipStructure() {
	}

	public static List<StructuralElement> linearize(SequenceDefinition definition) {
		List<StructuralElement> out = new ArrayList<>();
		if (definition == null) {
			return out;
		}

		List<Step> steps = definition.getSteps();
		for (int stepIndex = 0; stepIndex < steps.size(); stepIndex++) {
			Step step = steps.get(stepIndex);
			linearizeStep(step, stepIndex, out);

			if (stepIndex < steps.size() - 1) {
				out.add(StructuralElement.operator(TooltipGrammar.ARROW, stepIndex));
			}
		}

		return out;
	}

	private static void linearizeStep(Step step, int stepIndex, List<StructuralElement> out) {
		List<Term> terms = step.getTerms();
		for (int termIndex = 0; termIndex < terms.size(); termIndex++) {
			Term term = terms.get(termIndex);
			linearizeTerm(term, stepIndex, out);

			if (termIndex < terms.size() - 1) {
				out.add(StructuralElement.operator(TooltipGrammar.AND, stepIndex));
			}
		}
	}

	private static void linearizeTerm(Term term, int stepIndex, List<StructuralElement> out) {
		List<Alternative> alternatives = term.getAlternatives();
		for (int altIndex = 0; altIndex < alternatives.size(); altIndex++) {
			Alternative alt = alternatives.get(altIndex);
			linearizeAlternative(alt, stepIndex, out);

			if (altIndex < alternatives.size() - 1) {
				out.add(StructuralElement.operator(TooltipGrammar.OR, stepIndex));
			}
		}
	}

	private static void linearizeAlternative(Alternative alt, int stepIndex, List<StructuralElement> out) {
		if (alt.isToken()) {
			List<String> modifiers = alt.getAbilityModifiers();
			if (modifiers != null && !modifiers.isEmpty()) {
				for (String modifier : modifiers) {
					if (modifier == null || modifier.isBlank()) {
						continue;
					}
					out.add(StructuralElement.ability(modifier, stepIndex));
					out.add(StructuralElement.operator(TooltipGrammar.AND, stepIndex));
				}
			}
			String token = AbilityToken.format(alt.getToken(), alt.getInstanceLabel());
			if (token != null) {
				out.add(StructuralElement.ability(token, stepIndex));
			}
			return;
		}

		if (alt.isGroup()) {
			SequenceDefinition subgroup = alt.getSubgroup();
			if (subgroup == null) {
				return;
			}
			List<Step> subSteps = subgroup.getSteps();
			for (int i = 0; i < subSteps.size(); i++) {
				Step subStep = subSteps.get(i);
				linearizeStep(subStep, stepIndex, out);
				if (i < subSteps.size() - 1) {
					out.add(StructuralElement.operator(TooltipGrammar.ARROW, stepIndex));
				}
			}
		}
	}

	public record StructuralElement(boolean ability, Character operatorSymbol, int stepIndex, String abilityName) {
		public static StructuralElement ability(String abilityName, int stepIndex) {
			Objects.requireNonNull(abilityName, "abilityName");
			return new StructuralElement(true, null, stepIndex, abilityName);
		}

		public static StructuralElement operator(char symbol, int stepIndex) {
			return new StructuralElement(false, symbol, stepIndex, null);
		}

		public boolean isAbility() {
			return ability;
		}

		public boolean isOperator() {
			return !ability && operatorSymbol != null;
		}

		public boolean isArrow() {
			return operatorSymbol != null && operatorSymbol == TooltipGrammar.ARROW;
		}
	}
}
