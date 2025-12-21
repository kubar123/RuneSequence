package com.lansoftprogramming.runeSequence.core.sequence.runtime.modification;

import java.util.Collection;
import java.util.HashSet;
import java.util.Objects;
import java.util.Set;

/**
 * Common {@link AbilitySelector} factories.
 */
public final class AbilitySelectors {

	private AbilitySelectors() {
	}

	public static AbilitySelector any() {
		return (ability, context) -> true;
	}

	public static AbilitySelector byKey(String abilityKey) {
		Objects.requireNonNull(abilityKey, "abilityKey");
		return (ability, context) -> ability != null && abilityKey.equals(ability.abilityKey());
	}

	public static AbilitySelector byKeys(Collection<String> abilityKeys) {
		Objects.requireNonNull(abilityKeys, "abilityKeys");
		Set<String> keys = new HashSet<>();
		for (String key : abilityKeys) {
			if (key != null && !key.isBlank()) {
				keys.add(key);
			}
		}
		Set<String> frozen = Set.copyOf(keys);
		return (ability, context) -> ability != null && frozen.contains(ability.abilityKey());
	}

	public static AbilitySelector byType(String type) {
		Objects.requireNonNull(type, "type");
		return (ability, context) -> {
			if (ability == null) {
				return false;
			}
			if (ability.abilityData() != null && ability.abilityData().getType() != null) {
				return type.equalsIgnoreCase(ability.abilityData().getType());
			}
			if (ability.effectiveConfig() != null && ability.effectiveConfig().getType().isPresent()) {
				return type.equalsIgnoreCase(ability.effectiveConfig().getType().orElse(""));
			}
			return false;
		};
	}
}

