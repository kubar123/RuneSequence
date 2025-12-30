package com.lansoftprogramming.runeSequence.core.sequence.modifier;

import com.lansoftprogramming.runeSequence.core.sequence.model.*;

import java.util.*;

/**
 * Applies "modifier tokens" that annotate another ability in the same step.
 * <p>
 * Example: {@code gmaul + eofspec} is represented as a single {@code eofspec} token with a {@code gmaul} modifier.
 * The modifier does not become a detectable ability; it only affects effective configuration and UI rendering.
 */
public final class AbilityModifierEngine {

	private static final Map<String, ModifierDefinition> DEFINITIONS_BY_ALIAS;
	private static final Map<String, ModifierDefinition> DEFINITIONS_BY_CANONICAL;
	private static final List<ModifierDefinition> DEFINITIONS;

	static {
		List<ModifierDefinition> definitions = List.of(
				new ModifierDefinition(
						"gmaul",
						Set.of("gmaul"),
						Set.of("spec", "eofspec"),
						AbilitySettingsOverrides.builder().triggersGcd(false).build()
				),
				new ModifierDefinition(
						"armadylbattlestaff",
						Set.of("armadylbattlestaff", "armabattlestaff"),
						Set.of("spec", "eofspec"),
						AbilitySettingsOverrides.builder().castDuration((short) 5).build()
				)
		);

		Map<String, ModifierDefinition> byAlias = new HashMap<>();
		Map<String, ModifierDefinition> byCanonical = new HashMap<>();
		for (ModifierDefinition def : definitions) {
			if (def == null) {
				continue;
			}
			String canonical = normalizeKey(def.canonicalKey());
			if (canonical != null) {
				byCanonical.put(canonical, def);
			}
			for (String alias : def.aliases()) {
				String normalized = normalizeKey(alias);
				if (normalized != null) {
					byAlias.put(normalized, def);
				}
			}
		}

		DEFINITIONS = List.copyOf(definitions);
		DEFINITIONS_BY_ALIAS = Map.copyOf(byAlias);
		DEFINITIONS_BY_CANONICAL = Map.copyOf(byCanonical);
	}

	private AbilityModifierEngine() {
	}

	public static boolean isModifierToken(String token) {
		return resolve(token) != null;
	}

	/**
	 * Resolves a modifier definition for the given token (case-insensitive). Returns null when unknown.
	 */
	public static ModifierDefinition resolve(String token) {
		String normalized = normalizeKey(token);
		if (normalized == null) {
			return null;
		}
		return DEFINITIONS_BY_ALIAS.get(normalized);
	}

	public static String canonicalModifierKey(String token) {
		ModifierDefinition def = resolve(token);
		return def != null ? def.canonicalKey() : null;
	}

	public static List<ModifierDefinition> listDefinitions() {
		return DEFINITIONS;
	}

	public static List<ModifierDefinition> listDefinitionsForTarget(String abilityKey) {
		String normalizedTarget = normalizeKey(abilityKey);
		if (normalizedTarget == null) {
			return List.of();
		}

		List<ModifierDefinition> out = new ArrayList<>();
		for (ModifierDefinition def : DEFINITIONS) {
			if (def != null && def.targets().contains(normalizedTarget)) {
				out.add(def);
			}
		}
		out.sort(Comparator.comparing(ModifierDefinition::canonicalKey));
		return List.copyOf(out);
	}

	public static ModifierDefinition resolveCanonical(String canonicalKey) {
		String normalized = normalizeKey(canonicalKey);
		if (normalized == null) {
			return null;
		}
		return DEFINITIONS_BY_CANONICAL.get(normalized);
	}

	public static AbilitySettingsOverrides deriveOverrides(Alternative alternative) {
		if (alternative == null || !alternative.isToken()) {
			return null;
		}
		List<String> modifiers = alternative.getAbilityModifiers();
		if (modifiers == null || modifiers.isEmpty()) {
			return null;
		}

		String targetKey = normalizeKey(alternative.getToken());
		if (targetKey == null) {
			return null;
		}

		AbilitySettingsOverrides derived = null;
		for (String modifier : modifiers) {
			ModifierDefinition def = resolve(modifier);
			if (def == null) {
				continue;
			}
			if (!def.targets().contains(targetKey)) {
				continue;
			}
			derived = AbilitySettingsOverrides.merge(derived, def.overrides());
		}
		return derived;
	}

	public static AbilitySettingsOverrides effectiveOverrides(Alternative alternative) {
		if (alternative == null || !alternative.isToken()) {
			return alternative != null ? alternative.getAbilitySettingsOverrides() : null;
		}
		AbilitySettingsOverrides derived = deriveOverrides(alternative);
		return AbilitySettingsOverrides.merge(derived, alternative.getAbilitySettingsOverrides());
	}

	/**
	 * Rewrites the AST so "modifier + target" chains become a single target token carrying modifier metadata.
	 */
	public static SequenceDefinition apply(SequenceDefinition definition) {
		if (definition == null) {
			return null;
		}

		boolean changed = false;
		List<Step> steps = definition.getSteps();
		List<Step> newSteps = new ArrayList<>(steps.size());

		for (Step step : steps) {
			Step processed = apply(step);
			if (processed != step) {
				changed = true;
			}
			newSteps.add(processed);
		}

		return changed ? new SequenceDefinition(newSteps) : definition;
	}

	private static Step apply(Step step) {
		if (step == null) {
			return null;
		}

		boolean changed = false;
		List<Term> originalTerms = step.getTerms();
		List<Term> processedTerms = new ArrayList<>(originalTerms.size());

		for (Term term : originalTerms) {
			Term processed = apply(term);
			if (processed != term) {
				changed = true;
			}
			processedTerms.add(processed);
		}

		List<Term> mergedTerms = mergeModifierChains(processedTerms);
		if (mergedTerms != processedTerms) {
			changed = true;
		}

		return changed ? new Step(mergedTerms) : step;
	}

	private static Term apply(Term term) {
		if (term == null) {
			return null;
		}
		boolean changed = false;
		List<Alternative> alts = term.getAlternatives();
		List<Alternative> newAlts = new ArrayList<>(alts.size());
		for (Alternative alt : alts) {
			Alternative processed = apply(alt);
			if (processed != alt) {
				changed = true;
			}
			newAlts.add(processed);
		}
		return changed ? new Term(newAlts) : term;
	}

	private static Alternative apply(Alternative alt) {
		if (alt == null) {
			return null;
		}
		if (!alt.isGroup()) {
			return alt;
		}
		SequenceDefinition subgroup = alt.getSubgroup();
		if (subgroup == null) {
			return alt;
		}
		SequenceDefinition processed = apply(subgroup);
		if (processed == subgroup) {
			return alt;
		}
		return new Alternative(processed);
	}

	private static List<Term> mergeModifierChains(List<Term> terms) {
		if (terms == null || terms.size() < 2) {
			return terms;
		}

		boolean changed = false;
		List<Term> out = new ArrayList<>(terms.size());

		int i = 0;
		while (i < terms.size()) {
			ModifierChain chain = readModifierChain(terms, i);
			if (chain != null && chain.canCollapse()) {
				out.add(chain.toCollapsedTerm());
				i = chain.nextIndexAfterTarget();
				changed = true;
				continue;
			}

			out.add(terms.get(i));
			i++;
		}

		return changed ? List.copyOf(out) : terms;
	}

	private static ModifierChain readModifierChain(List<Term> terms, int startIndex) {
		if (terms == null || startIndex < 0 || startIndex >= terms.size()) {
			return null;
		}

		int cursor = startIndex;
		List<ModifierDefinition> modifiers = new ArrayList<>();

		while (cursor < terms.size()) {
			Alternative alt = singleTokenAlternative(terms.get(cursor));
			if (alt == null) {
				break;
			}

			ModifierDefinition def = resolve(alt.getToken());
			if (def == null) {
				break;
			}
			modifiers.add(def);
			cursor++;
		}

		if (modifiers.isEmpty()) {
			return null;
		}

		Alternative targetAlt = cursor < terms.size() ? singleTokenAlternative(terms.get(cursor)) : null;
		return new ModifierChain(startIndex, cursor, modifiers, targetAlt);
	}

	private static Alternative singleTokenAlternative(Term term) {
		if (term == null) {
			return null;
		}
		List<Alternative> alts = term.getAlternatives();
		if (alts == null || alts.size() != 1) {
			return null;
		}
		Alternative alt = alts.get(0);
		return alt != null && alt.isToken() ? alt : null;
	}

	private static String normalizeKey(String key) {
		if (key == null) {
			return null;
		}
		String trimmed = key.trim();
		if (trimmed.isEmpty()) {
			return null;
		}
		return trimmed.toLowerCase();
	}

	public record ModifierDefinition(String canonicalKey,
	                                 Set<String> aliases,
	                                 Set<String> targets,
	                                 AbilitySettingsOverrides overrides) {
		public ModifierDefinition {
			Objects.requireNonNull(canonicalKey, "canonicalKey");
			aliases = aliases != null ? Set.copyOf(aliases) : Set.of(canonicalKey);
			Set<String> normalizedTargets = new HashSet<>();
			if (targets != null) {
				for (String target : targets) {
					String normalized = normalizeKey(target);
					if (normalized != null) {
						normalizedTargets.add(normalized);
					}
				}
			}
			targets = normalizedTargets.isEmpty() ? Set.of() : Set.copyOf(normalizedTargets);
			overrides = overrides != null && !overrides.isEmpty() ? overrides : null;
		}
	}

	private static final class ModifierChain {
		private final int startIndex;
		private final int targetIndex;
		private final List<ModifierDefinition> modifiers;
		private final Alternative targetAlt;

		private ModifierChain(int startIndex,
		                      int targetIndex,
		                      List<ModifierDefinition> modifiers,
		                      Alternative targetAlt) {
			this.startIndex = startIndex;
			this.targetIndex = targetIndex;
			this.modifiers = modifiers != null ? List.copyOf(modifiers) : List.of();
			this.targetAlt = targetAlt;
		}

		boolean canCollapse() {
			if (targetAlt == null || modifiers.isEmpty()) {
				return false;
			}
			String targetKey = normalizeKey(targetAlt.getToken());
			if (targetKey == null) {
				return false;
			}
			for (ModifierDefinition def : modifiers) {
				if (def == null || !def.targets().contains(targetKey)) {
					return false;
				}
			}
			return true;
		}

		Term toCollapsedTerm() {
			List<String> mergedModifiers = new ArrayList<>(targetAlt.getAbilityModifiers());
			for (ModifierDefinition def : modifiers) {
				mergedModifiers.add(def.canonicalKey());
			}
			Alternative merged = targetAlt.withAbilityModifiers(mergedModifiers);
			return new Term(List.of(merged));
		}

		int nextIndexAfterTarget() {
			return targetIndex + 1;
		}
	}
}
