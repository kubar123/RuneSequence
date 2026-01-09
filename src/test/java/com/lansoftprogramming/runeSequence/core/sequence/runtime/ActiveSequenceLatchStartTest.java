package com.lansoftprogramming.runeSequence.core.sequence.runtime;

import com.lansoftprogramming.runeSequence.core.sequence.model.Alternative;
import com.lansoftprogramming.runeSequence.core.sequence.model.SequenceDefinition;
import com.lansoftprogramming.runeSequence.core.sequence.model.Step;
import com.lansoftprogramming.runeSequence.core.sequence.model.Term;
import com.lansoftprogramming.runeSequence.infrastructure.config.AbilityConfig;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Field;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;

class ActiveSequenceLatchStartTest {

	@Test
	void shouldAdvanceImmediatelyOnLatchForInstantAbility() throws Exception {
		AbilityConfig abilityConfig = new AbilityConfig();
		AbilityConfig.AbilityData a = new AbilityConfig.AbilityData();
		a.setTriggersGcd(true);
		a.setCastDuration((short) 0);
		a.setCooldown((short) 0);
		abilityConfig.putAbility("A", a);

		AbilityConfig.AbilityData b = new AbilityConfig.AbilityData();
		b.setTriggersGcd(false);
		b.setCastDuration((short) 0);
		b.setCooldown((short) 0);
		abilityConfig.putAbility("B", b);

		SequenceDefinition definition = new SequenceDefinition(List.of(
				new Step(List.of(new Term(List.of(new Alternative("A"))))),
				new Step(List.of(new Term(List.of(new Alternative("B")))))
		));

		ActiveSequence seq = new ActiveSequence(definition, abilityConfig);

		long now = System.currentTimeMillis();
		seq.onLatchStart(now);

		assertEquals(1, seq.getCurrentStepIndex());
		assertFalse(seq.isComplete());

		// Force the latch-delay window to complete.
		setPrivateLongField(seq.stepTimer, "stepStartTimeMs", System.currentTimeMillis() - seq.stepTimer.getStepDurationMs() - 5);

		seq.processDetections(List.of());

		// Still on step 1, but now using step 1's own timing (B has 0 duration).
		assertEquals(1, seq.getCurrentStepIndex());
		assertEquals(0L, seq.stepTimer.getStepDurationMs());
		assertFalse(seq.isComplete());
	}

	@Test
	void shouldNotAdvanceOnLatchForCastAbility() {
		AbilityConfig abilityConfig = new AbilityConfig();
		AbilityConfig.AbilityData a = new AbilityConfig.AbilityData();
		a.setTriggersGcd(true);
		a.setCastDuration((short) 4);
		a.setCooldown((short) 0);
		abilityConfig.putAbility("A", a);

		AbilityConfig.AbilityData b = new AbilityConfig.AbilityData();
		b.setTriggersGcd(true);
		b.setCastDuration((short) 0);
		b.setCooldown((short) 0);
		abilityConfig.putAbility("B", b);

		SequenceDefinition definition = new SequenceDefinition(List.of(
				new Step(List.of(new Term(List.of(new Alternative("A"))))),
				new Step(List.of(new Term(List.of(new Alternative("B")))))
		));

		ActiveSequence seq = new ActiveSequence(definition, abilityConfig);

		seq.onLatchStart(System.currentTimeMillis());

		assertEquals(0, seq.getCurrentStepIndex());
		assertEquals(4 * StepTimer.TICK_MS, seq.stepTimer.getStepDurationMs());
	}

	private static void setPrivateLongField(Object target, String fieldName, long value) throws Exception {
		Field field = target.getClass().getDeclaredField(fieldName);
		field.setAccessible(true);
		field.setLong(target, value);
	}
}

