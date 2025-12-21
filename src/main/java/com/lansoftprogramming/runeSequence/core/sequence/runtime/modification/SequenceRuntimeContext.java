package com.lansoftprogramming.runeSequence.core.sequence.runtime.modification;

import com.lansoftprogramming.runeSequence.core.sequence.runtime.timing.StepTimingView;
import com.lansoftprogramming.runeSequence.infrastructure.config.AbilityConfig;

import java.util.List;
import java.util.Objects;
import java.util.Optional;

/**
 * Immutable snapshot of runtime state provided to modifier rules.
 */
public record SequenceRuntimeContext(AbilityConfig abilityConfig,
                                     int currentStepIndex,
                                     List<String> currentStepAbilityKeys,
                                     List<String> nextStepAbilityKeys,
                                     StepTimingView stepTiming,
                                     long nowMs) {

	public SequenceRuntimeContext {
		Objects.requireNonNull(abilityConfig, "abilityConfig");
		currentStepAbilityKeys = currentStepAbilityKeys != null ? List.copyOf(currentStepAbilityKeys) : List.of();
		nextStepAbilityKeys = nextStepAbilityKeys != null ? List.copyOf(nextStepAbilityKeys) : List.of();
	}

	public Optional<AbilityConfig.AbilityData> getAbilityData(String abilityKey) {
		if (abilityKey == null) {
			return Optional.empty();
		}
		return Optional.ofNullable(abilityConfig.getAbility(abilityKey));
	}
}

