package com.lansoftprogramming.runeSequence.core.sequence.runtime.modification;

import java.util.Objects;

/**
 * Specification for a runtime ability override.
 */
public record AbilityOverrideSpec(String id,
                                  AbilitySelector selector,
                                  AbilityOverridePatch patch,
                                  String activeWhileWindowId,
                                  Long expiresAtMs,
                                  Integer usesRemaining,
                                  boolean consumeOnMatch,
                                  int priority) {

	public AbilityOverrideSpec {
		Objects.requireNonNull(id, "id");
		Objects.requireNonNull(selector, "selector");
		Objects.requireNonNull(patch, "patch");
	}
}

