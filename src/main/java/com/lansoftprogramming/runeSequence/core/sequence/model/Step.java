package com.lansoftprogramming.runeSequence.core.sequence.model;

import com.lansoftprogramming.runeSequence.infrastructure.config.AbilityConfig;
import com.lansoftprogramming.runeSequence.core.detection.DetectionResult;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

public class Step {

	private final List<Term> terms;

	public Step(List<Term> terms) {
		this.terms = List.copyOf(terms);
	}

	public List<Term> getTerms() {
		return terms;
	}

	/**
	 * Flatten all detectable tokens in this step for detection engine
	 */
	public List<String> getDetectableTokens(AbilityConfig abilityConfig) {
		List<String> out = new ArrayList<>();
		for (Term t : terms) {
			for (Alternative alt : t.getAlternatives()) {
				collectDetectable(alt, abilityConfig, out);
			}
		}
		return out;
	}

	private void collectDetectable(Alternative alt, AbilityConfig abilityCfg, List<String> out) {
		if (alt.isToken()) {
			String tokenName = alt.getToken();
			System.out.println("Checking token: '" + tokenName + "'");

			if (abilityCfg.getAbility(tokenName) != null) {
				out.add(tokenName);
				System.out.println("Added token: '" + tokenName + "'");
			} else {
				System.out.println("Token not found in AbilityConfig: '" + tokenName + "'");
				System.out.println("Available abilities: " + abilityCfg.getAbilities().keySet());
			}
		} else {
			// Handle subgroup - recursively process ALL steps, not just first
			for (Step step : alt.getSubgroup().getSteps()) {
				for (Term term : step.getTerms()) {
					for (Alternative alternative : term.getAlternatives()) {
						collectDetectable(alternative, abilityCfg, out);
					}
				}
			}
		}
	}

	public void debugStep(AbilityConfig abilityCfg) {
		System.out.println("=== DEBUG STEP ===");
		System.out.println("Terms count: " + terms.size());

		for (int i = 0; i < terms.size(); i++) {
			Term term = terms.get(i);
			System.out.println("Term[" + i + "] alternatives: " + term.getAlternatives().size());

			for (int j = 0; j < term.getAlternatives().size(); j++) {
				Alternative alt = term.getAlternatives().get(j);
				System.out.println("  Alt[" + j + "] isToken: " + alt.isToken());
				if (alt.isToken()) {
					System.out.println("    Token: '" + alt.getToken() + "'");
					System.out.println("    Exists in config: " + (abilityCfg.getAbility(alt.getToken()) != null));
				}
			}
		}

		// TODO: The config is no longer available here, this method may need to be moved
		// or the config passed in from a higher level for debugging.
		// List<String> detectableTokens = getDetectableTokens(abilityCfg);
		// System.out.println("Detectable tokens: " + detectableTokens);
		System.out.println("=================");
	}

	/**
	 * For overlay: flatten alternatives into DetectionResults using lastDetections
	 */
	public List<DetectionResult> flattenDetections(Map<String, DetectionResult> lastDetections) {

		System.out.println("Step.flattenDetections: Processing " + terms.size() + " terms");
		List<DetectionResult> out = new ArrayList<>();

		for (Term t : terms) {
			// A Term with more than one alternative (separated by '/') represents an OR group.
			boolean termIsAlternative = t.getAlternatives().size() > 1;
			for (Alternative alt : t.getAlternatives()) {
				collectDetections(alt, lastDetections, out, termIsAlternative);
			}
		}


		System.out.println("Step.flattenDetections: Returning " + out.size() + " results");
		return out;
	}

	// FIXED: Handle subgroups properly - process ALL steps, not just first
	private void collectDetections(Alternative alt, Map<String, DetectionResult> lastDetections, List<DetectionResult> out, boolean parentTermIsAlternative) {

		System.out.println("Step.collectDetections: Processing alternative, isToken=" + alt.isToken() + ", parentTermIsAlternative=" + parentTermIsAlternative);

		if (alt.isToken()) {
			String tokenName = alt.getToken();
			DetectionResult existing = lastDetections.get(tokenName);
			DetectionResult r;
			if (existing == null) {
				// Not found - create a notFound with the correct isAlternative flag
				r = DetectionResult.notFound(tokenName, parentTermIsAlternative);
			} else {
				// Use the existing result's isAlternative if it was set during detection,
				// otherwise use the parentTermIsAlternative from sequence structure
				boolean useAlternative = existing.isAlternative || parentTermIsAlternative;
				if (existing.isAlternative == useAlternative) {
					r = existing;
				} else {
					// Recreate a DetectionResult preserving data but with updated isAlternative
					if (existing.found) {
						r = DetectionResult.found(existing.templateName,
								existing.location,
								existing.confidence,
								existing.boundingBox,
								useAlternative);
					} else {
						r = DetectionResult.notFound(existing.templateName, useAlternative);
					}
				}
			}
			out.add(r);

			System.out.println("  Added token result: " + tokenName + " found=" + r.found + " isAlternative=" + r.isAlternative);
		} else {
			// FIXED: Process ALL steps in the subgroup, not just the first

			System.out.println("  Processing subgroup with " + alt.getSubgroup().getSteps().size() + " steps");


			for (Step step : alt.getSubgroup().getSteps()) {

				System.out.println("    Processing subgroup step with " + step.getTerms().size() + " terms");

				for (Term term : step.getTerms()) {

					boolean termIsAlternative = term.getAlternatives().size() > 1;
					for (Alternative alternative : term.getAlternatives()) {

						collectDetections(alternative, lastDetections, out, termIsAlternative);

					}

				}

			}
		}
	}

	@Override
	public String toString() {
		return String.join(" + ", terms.stream().map(Object::toString).toList());
	}
}