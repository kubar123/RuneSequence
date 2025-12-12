package com.lansoftprogramming.runeSequence.core.sequence.model;

import com.lansoftprogramming.runeSequence.core.detection.DetectionResult;
import com.lansoftprogramming.runeSequence.infrastructure.config.AbilityConfig;
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
	public List<String> getDetectableTokens(AbilityConfig abilityConfig) {
		return getEffectiveAbilityConfigs(abilityConfig).stream()
				.map(EffectiveAbilityConfig::getAbilityKey)
				.toList();
	}

	public List<EffectiveAbilityConfig> getEffectiveAbilityConfigs(AbilityConfig abilityConfig) {
		List<EffectiveAbilityConfig> out = new ArrayList<>();
		for (Term t : terms) {
			for (Alternative alt : t.getAlternatives()) {
				collectEffectiveConfigs(alt, abilityConfig, out);
			}
		}
		return out;
	}

	private void collectEffectiveConfigs(Alternative alt,
	                                     AbilityConfig abilityCfg,
	                                     List<EffectiveAbilityConfig> out) {
		if (alt.isToken()) {
			String tokenName = alt.getToken();
			String abilityKey = baseAbilityKey(tokenName);
			AbilityConfig.AbilityData data = abilityCfg.getAbility(abilityKey);
			if (data != null) {
				AbilitySettingsOverrides overrides = alt.getAbilitySettingsOverrides();
				out.add(EffectiveAbilityConfig.from(abilityKey, data, overrides));
				logger.trace("Added token '{}'", abilityKey);
			} else {
				logger.debug("Token '{}' not found in AbilityConfig. Available abilities: {}", abilityKey, abilityCfg.getAbilities().keySet());
			}
		} else {
			// Handle subgroup - recursively process ALL steps, not just first
			for (Step step : alt.getSubgroup().getSteps()) {
				for (Term term : step.getTerms()) {
					for (Alternative alternative : term.getAlternatives()) {
						collectEffectiveConfigs(alternative, abilityCfg, out);
					}
				}
			}
		}
	}


	/**
	 * For overlay: flatten alternatives into DetectionResults using lastDetections
	 */
	public List<DetectionResult> flattenDetections(Map<String, DetectionResult> lastDetections) {

		logger.trace("Flattening detections for {} terms.", terms.size());
		List<DetectionResult> out = new ArrayList<>();

		for (Term t : terms) {
			// A Term with more than one alternative (separated by '/') represents an OR group.
			boolean termIsAlternative = t.getAlternatives().size() > 1;
			for (Alternative alt : t.getAlternatives()) {
				collectDetections(alt, lastDetections, out, termIsAlternative);
			}
		}


		logger.trace("Flatten detections returning {} results.", out.size());
		return out;
	}

	//Handle subgroups properly - process ALL steps, not just first
	private void collectDetections(Alternative alt, Map<String, DetectionResult> lastDetections, List<DetectionResult>
			out, boolean parentTermIsAlternative) {

		logger.trace("Processing alternative isToken={} parentTermIsAlternative={}", alt.isToken(), parentTermIsAlternative);

		if (alt.isToken()) {
			String tokenName = alt.getToken();
			String abilityKey = baseAbilityKey(tokenName);
			DetectionResult existing = lastDetections.get(abilityKey);
			DetectionResult r;
			if (existing == null) {
				// Not found - create a notFound with the correct isAlternative flag
				r = DetectionResult.notFound(abilityKey, parentTermIsAlternative);
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

			logger.trace("Added token result '{}' found={} isAlternative={}", tokenName, r.found, r.isAlternative);
		} else {
			// FIXED: Process ALL steps in the subgroup, not just the first

			logger.trace("Processing subgroup with {} steps.", alt.getSubgroup().getSteps().size());


			for (Step step : alt.getSubgroup().getSteps()) {
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

	private String baseAbilityKey(String tokenName) {
		return AbilityToken.baseAbilityKey(tokenName);
	}
}
