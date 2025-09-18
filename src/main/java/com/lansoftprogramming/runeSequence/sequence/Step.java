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
			if (abilityCfg.getAbility(alt.getToken()) != null) {
				out.add(alt.getToken());
			}
		} else {
			// group
			/*##*/
			for (Term t : alt.getSubgroup().getSteps().get(0).getTerms()) { // Fixed: getGroup() -> getSubgroup()
				for (Alternative a : t.getAlternatives()) {
					collectDetectable(a, abilityCfg, out);
				}
			}
		}
	}

	/**
	 * For overlay: flatten alternatives into DetectionResults using lastDetections
	 */
	public List<DetectionResult> flattenDetections(Map<String, DetectionResult> lastDetections) {
		List<DetectionResult> out = new ArrayList<>();
		for (Term t : terms) {
			for (Alternative alt : t.getAlternatives()) {
				collectDetections(alt, lastDetections, out);
			}
		}
		return out;
	}

	private void collectDetections(Alternative alt, Map<String, DetectionResult> lastDetections, List<DetectionResult> out) {
		if (alt.isToken()) {
			DetectionResult r = lastDetections.getOrDefault(alt.getToken(), DetectionResult.notFound(alt.getToken()));
			out.add(r);
		} else {
			// group
			/*##*/
			for (Term t : alt.getSubgroup().getSteps().get(0).getTerms()) { // Fixed: getGroup() -> getSubgroup()
				for (Alternative a : t.getAlternatives()) {
					collectDetections(a, lastDetections, out);
				}
			}
		}
	}
}