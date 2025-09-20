package com.lansoftprogramming.runeSequence.sequence;

import com.lansoftprogramming.runeSequence.config.AbilityConfig;
import com.lansoftprogramming.runeSequence.config.ConfigManager;
import com.lansoftprogramming.runeSequence.detection.DetectionResult;

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
	public List<String> getDetectableTokens(ConfigManager configManager) {
		List<String> out = new ArrayList<>();
		AbilityConfig abilityCfg = configManager.getAbilities();

		for (Term t : terms) {
			for (Alternative alt : t.getAlternatives()) {
				collectDetectable(alt, abilityCfg, out);
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

		List<String> detectableTokens = getDetectableTokens(null); // Pass your ConfigManager here
		System.out.println("Detectable tokens: " + detectableTokens);
		System.out.println("=================");
	}

	/**
	 * For overlay: flatten alternatives into DetectionResults using lastDetections
	 */
	public List<DetectionResult> flattenDetections(Map<String, DetectionResult> lastDetections) {

		System.out.println("Step.flattenDetections: Processing " + terms.size() + " terms");
		List<DetectionResult> out = new ArrayList<>();

		for (Term t : terms) {
			for (Alternative alt : t.getAlternatives()) {
				collectDetections(alt, lastDetections, out);
			}
		}


		System.out.println("Step.flattenDetections: Returning " + out.size() + " results");
		return out;
	}

	// FIXED: Handle subgroups properly - process ALL steps, not just first
	private void collectDetections(Alternative alt, Map<String, DetectionResult> lastDetections, List<DetectionResult> out) {

		System.out.println("Step.collectDetections: Processing alternative, isToken=" + alt.isToken());

		if (alt.isToken()) {
			String tokenName = alt.getToken();
			DetectionResult r = lastDetections.getOrDefault(tokenName, DetectionResult.notFound(tokenName));
			out.add(r);

			System.out.println("  Added token result: " + tokenName + " found=" + r.found);
		} else {
			// FIXED: Process ALL steps in the subgroup, not just the first

			System.out.println("  Processing subgroup with " + alt.getSubgroup().getSteps().size() + " steps");


			for (Step step : alt.getSubgroup().getSteps()) {
				
				System.out.println("    Processing subgroup step with " + step.getTerms().size() + " terms");

				for (Term term : step.getTerms()) {

					for (Alternative alternative : term.getAlternatives()) {

						collectDetections(alternative, lastDetections, out);

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