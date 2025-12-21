package com.lansoftprogramming.runeSequence.core.sequence.runtime.modification;

import java.util.Objects;
import java.util.function.BiPredicate;

/**
 * Predicate describing which abilities a modification targets.
 * <p>
 * Selectors must match internal identifiers (ability keys / instance ids), not display names.
 */
@FunctionalInterface
public interface AbilitySelector {

	boolean matches(AbilityRef ability, SequenceRuntimeContext context);

	default AbilitySelector and(AbilitySelector other) {
		Objects.requireNonNull(other, "other");
		return (ability, context) -> matches(ability, context) && other.matches(ability, context);
	}

	default AbilitySelector or(AbilitySelector other) {
		Objects.requireNonNull(other, "other");
		return (ability, context) -> matches(ability, context) || other.matches(ability, context);
	}

	default AbilitySelector negate() {
		return (ability, context) -> !matches(ability, context);
	}

	static AbilitySelector from(BiPredicate<AbilityRef, SequenceRuntimeContext> predicate) {
		Objects.requireNonNull(predicate, "predicate");
		return predicate::test;
	}
}

