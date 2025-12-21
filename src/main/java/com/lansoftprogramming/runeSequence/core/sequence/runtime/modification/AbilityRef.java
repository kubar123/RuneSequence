package com.lansoftprogramming.runeSequence.core.sequence.runtime.modification;

import com.lansoftprogramming.runeSequence.core.sequence.model.EffectiveAbilityConfig;
import com.lansoftprogramming.runeSequence.infrastructure.config.AbilityConfig;

/**
 * Resolved runtime identity for an ability, including optional per-instance metadata.
 */
public record AbilityRef(String abilityKey,
                         String instanceId,
                         AbilityConfig.AbilityData abilityData,
                         EffectiveAbilityConfig effectiveConfig) {
}

