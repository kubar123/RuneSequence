package com.lansoftprogramming.runeSequence.sequence;

import com.lansoftprogramming.runeSequence.config.AbilityConfig;
import com.lansoftprogramming.runeSequence.config.ConfigManager;
import com.lansoftprogramming.runeSequence.detection.DetectionResult;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

public class Step {
	private static final Logger logger = LoggerFactory.getLogger(Step.class);
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
			logger.debug("Checking token: '{}'", tokenName);

			if (abilityCfg.getAbility(tokenName) != null) {
				out.add(tokenName);
				logger.debug("Added token: '{}'", tokenName);
			} else {
				logger.warn("Token not found in AbilityConfig: '{}'", tokenName);
				logger.warn("Available abilities: {}", abilityCfg.getAbilities().keySet());
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
		logger.debug("=== DEBUG STEP ===");
		logger.debug("Terms count: {}", terms.size());

		for (int i = 0; i < terms.size(); i++) {
			Term term = terms.get(i);
			logger.debug("Term[{}] alternatives: {}", i, term.getAlternatives().size());

			for (int j = 0; j < term.getAlternatives().size(); j++) {
				Alternative alt = term.getAlternatives().get(j);
				logger.debug("  Alt[{}] isToken: {}", j, alt.isToken());
				if (alt.isToken()) {
					logger.debug("    Token: '{}'", alt.getToken());
					logger.debug("    Exists in config: {}", (abilityCfg.getAbility(alt.getToken()) != null));
				}
			}
		}

		List<String> detectableTokens = getDetectableTokens(null); // Pass your ConfigManager here
		logger.debug("Detectable tokens: {}", detectableTokens);
		logger.debug("=================");
	}

	/**
	 * For overlay: flatten alternatives into DetectionResults using lastDetections
	 */
	public List<DetectionResult> flattenDetections(Map<String, DetectionResult> lastDetections) {
		logger.debug("Step.flattenDetections: Processing {} terms", terms.size());
		List<DetectionResult> out = new ArrayList<>();

		for (Term t : terms) {
			for (Alternative alt : t.getAlternatives()) {
				collectDetections(alt, lastDetections, out);
			}
		}

		logger.debug("Step.flattenDetections: Returning {} results", out.size());
		return out;
	}

	// FIXED: Handle subgroups properly - process ALL steps, not just first
	private void collectDetections(Alternative alt, Map<String, DetectionResult> lastDetections, List<DetectionResult> out) {
		logger.debug("Step.collectDetections: Processing alternative, isToken={}", alt.isToken());

		if (alt.isToken()) {
			String tokenName = alt.getToken();
			DetectionResult r = lastDetections.getOrDefault(tokenName, DetectionResult.notFound(tokenName));
			out.add(r);
			logger.debug("  Added token result: {} found={}", tokenName, r.found);
		} else {
			// FIXED: Process ALL steps in the subgroup, not just the first
			logger.debug("  Processing subgroup with {} steps", alt.getSubgroup().getSteps().size());

			for (Step step : alt.getSubgroup().getSteps()) {
				logger.debug("    Processing subgroup step with {} terms", step.getTerms().size());
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