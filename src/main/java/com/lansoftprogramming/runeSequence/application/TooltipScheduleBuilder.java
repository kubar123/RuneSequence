package com.lansoftprogramming.runeSequence.application;

import com.lansoftprogramming.runeSequence.core.sequence.model.Alternative;
import com.lansoftprogramming.runeSequence.core.sequence.model.SequenceDefinition;
import com.lansoftprogramming.runeSequence.core.sequence.model.Step;
import com.lansoftprogramming.runeSequence.core.sequence.model.Term;
import com.lansoftprogramming.runeSequence.core.sequence.parser.SequenceParser;
import com.lansoftprogramming.runeSequence.core.sequence.parser.TooltipMarkupParser;
import com.lansoftprogramming.runeSequence.core.sequence.runtime.SequenceTooltip;
import com.lansoftprogramming.runeSequence.core.sequence.runtime.TooltipSchedule;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.*;

/**
 * Builds a {@link TooltipSchedule} for a given rotation expression.
 * <p>
 * This builder reuses the tooltip parsing rules from {@link TooltipMarkupParser}
 * and the core {@link SequenceParser} so that UI and runtime share the same
 * semantics. Tooltips are attached at the step level and never affect detection
 * or timing.
 */
public class TooltipScheduleBuilder {

	private static final Logger logger = LoggerFactory.getLogger(TooltipScheduleBuilder.class);

	private final TooltipMarkupParser tooltipMarkupParser;

	public TooltipScheduleBuilder() {
		this(new TooltipMarkupParser());
	}

	public TooltipScheduleBuilder(Set<String> abilityNames) {
		this(new TooltipMarkupParser(abilityNames));
	}

	public TooltipScheduleBuilder(TooltipMarkupParser tooltipMarkupParser) {
		this.tooltipMarkupParser = tooltipMarkupParser;
	}

	/**
	 * Build a {@link SequenceDefinition} and its corresponding {@link TooltipSchedule}
	 * from a raw rotation expression.
	 *
	 * @param expression raw rotation expression, potentially containing tooltip markers
	 * @return build result holding the parsed definition and tooltip schedule
	 */
	public BuildResult build(String expression) {
		if (expression == null || expression.isBlank()) {
			return new BuildResult(null, TooltipSchedule.empty());
		}

		try {
			TooltipMarkupParser.ParseResult parseResult = tooltipMarkupParser.parse(expression);
			String cleanedExpression = parseResult.cleanedExpression();
			if (cleanedExpression == null || cleanedExpression.isBlank()) {
				// Expression consisted entirely of tooltip text; treat as no-op for runtime
				return new BuildResult(null, TooltipSchedule.empty());
			}

			SequenceDefinition definition = SequenceParser.parse(cleanedExpression);
			TooltipSchedule schedule = buildSchedule(definition, parseResult.tooltipPlacements());
			return new BuildResult(definition, schedule);
		} catch (Exception e) {
			logger.warn("Failed to build tooltip schedule for expression '{}'. Falling back to raw parse without tooltips.",
					expression, e);
			try {
				SequenceDefinition definition = SequenceParser.parse(expression);
				return new BuildResult(definition, TooltipSchedule.empty());
			} catch (Exception parseError) {
				logger.error("Failed to parse rotation expression '{}'. Sequence will be unavailable.", expression, parseError);
				return new BuildResult(null, TooltipSchedule.empty());
			}
		}
	}

	private TooltipSchedule buildSchedule(SequenceDefinition definition,
	                                      List<TooltipMarkupParser.TooltipPlacement> placements) {
		if (definition == null || placements == null || placements.isEmpty()) {
			return TooltipSchedule.empty();
		}

		List<StructuralElement> structure = linearizeStructure(definition);
		if (structure.isEmpty()) {
			return TooltipSchedule.empty();
		}

		Map<Integer, List<SequenceTooltip>> byStep = new HashMap<>();
		int maxIndex = structure.size();

		for (TooltipMarkupParser.TooltipPlacement placement : placements) {
			if (placement == null) {
				if (logger.isDebugEnabled()) {
					logger.debug("Skipping null tooltip placement during schedule build");
				}
				continue;
			}
			int insertionIndex = placement.insertionIndex();
			if (insertionIndex < 0 || insertionIndex > maxIndex) {
				if (logger.isDebugEnabled()) {
					logger.debug("Skipping tooltip placement with out-of-bounds insertionIndex {} (max={})",
							insertionIndex, maxIndex);
				}
				continue;
			}

			int stepIndex = resolveStepIndexForInsertion(structure, insertionIndex);
			if (stepIndex < 0) {
				if (logger.isDebugEnabled()) {
					logger.debug("Skipping tooltip placement at structural index {} with no resolvable step",
							insertionIndex);
				}
				continue;
			}

			String message = placement.message();
			if (message == null || message.isBlank()) {
				if (logger.isDebugEnabled()) {
					logger.debug("Skipping tooltip placement at structural index {} with blank message", insertionIndex);
				}
				continue;
			}

			SequenceTooltip tooltip = new SequenceTooltip(stepIndex, null, message);
			byStep.computeIfAbsent(stepIndex, k -> new ArrayList<>()).add(tooltip);
		}

		if (byStep.isEmpty()) {
			return TooltipSchedule.empty();
		}

		if (logger.isDebugEnabled()) {
			logger.debug("Built TooltipSchedule with tooltips on {} step(s)", byStep.size());
		}

		return new TooltipSchedule(byStep);
	}

	private List<StructuralElement> linearizeStructure(SequenceDefinition definition) {
		List<StructuralElement> out = new ArrayList<>();
		if (definition == null) {
			return out;
		}

		List<Step> steps = definition.getSteps();
		for (int stepIndex = 0; stepIndex < steps.size(); stepIndex++) {
			Step step = steps.get(stepIndex);
			linearizeStep(step, stepIndex, out);

			// Top-level NEXT arrow between steps
			if (stepIndex < steps.size() - 1) {
				out.add(StructuralElement.operator("→", stepIndex));
			}
		}

		return out;
	}

	private void linearizeStep(Step step, int stepIndex, List<StructuralElement> out) {
		List<Term> terms = step.getTerms();
		for (int termIndex = 0; termIndex < terms.size(); termIndex++) {
			Term term = terms.get(termIndex);
			linearizeTerm(term, stepIndex, out);

			// AND separator between terms
			if (termIndex < terms.size() - 1) {
				out.add(StructuralElement.operator("+", stepIndex));
			}
		}
	}

	private void linearizeTerm(Term term, int stepIndex, List<StructuralElement> out) {
		List<Alternative> alternatives = term.getAlternatives();
		for (int altIndex = 0; altIndex < alternatives.size(); altIndex++) {
			Alternative alt = alternatives.get(altIndex);
			linearizeAlternative(alt, stepIndex, out);

			// OR separator between alternatives
			if (altIndex < alternatives.size() - 1) {
				out.add(StructuralElement.operator("/", stepIndex));
			}
		}
	}

	private void linearizeAlternative(Alternative alt, int stepIndex, List<StructuralElement> out) {
		if (alt.isToken()) {
			out.add(StructuralElement.ability(stepIndex));
		} else if (alt.isGroup()) {
			SequenceDefinition subgroup = alt.getSubgroup();
			if (subgroup == null) {
				return;
			}
			List<Step> subSteps = subgroup.getSteps();
			for (int i = 0; i < subSteps.size(); i++) {
				Step subStep = subSteps.get(i);
				linearizeStep(subStep, stepIndex, out);
				// Nested NEXT arrows inside the group
				if (i < subSteps.size() - 1) {
					out.add(StructuralElement.operator("→", stepIndex));
				}
			}
		}
	}

	private int resolveStepIndexForInsertion(List<StructuralElement> structure, int insertionIndex) {
		int size = structure.size();
		if (size == 0) {
			return -1;
		}

		StructuralElement left = insertionIndex > 0 && insertionIndex - 1 < size
				? structure.get(insertionIndex - 1)
				: null;
		StructuralElement right = insertionIndex < size ? structure.get(insertionIndex) : null;

		// Arrow adjacency rules:
		// ability (msg) → ... -> left step
		if (right != null && right.isArrow() && left != null && left.isAbility()) {
			return left.stepIndex;
		}
		// ... → (msg) ability -> right step
		if (left != null && left.isArrow() && right != null && right.isAbility()) {
			return right.stepIndex;
		}

		// General rule: prefer the nearest ability neighbour
		if (left != null && left.isAbility()) {
			return left.stepIndex;
		}
		if (right != null && right.isAbility()) {
			return right.stepIndex;
		}

		// Fallback: attach to the left element's step, or right if left is absent
		if (left != null) {
			return left.stepIndex;
		}
		if (right != null) {
			return right.stepIndex;
		}

		return -1;
	}

	public record BuildResult(SequenceDefinition definition, TooltipSchedule schedule) {
	}

	private static final class StructuralElement {
		private final boolean ability;
		private final String operatorSymbol;
		private final int stepIndex;

		private StructuralElement(boolean ability, String operatorSymbol, int stepIndex) {
			this.ability = ability;
			this.operatorSymbol = operatorSymbol;
			this.stepIndex = stepIndex;
		}

		static StructuralElement ability(int stepIndex) {
			return new StructuralElement(true, null, stepIndex);
		}

		static StructuralElement operator(String symbol, int stepIndex) {
			return new StructuralElement(false, symbol, stepIndex);
		}

		boolean isAbility() {
			return ability;
		}

		boolean isArrow() {
			return "→".equals(operatorSymbol);
		}
	}
}