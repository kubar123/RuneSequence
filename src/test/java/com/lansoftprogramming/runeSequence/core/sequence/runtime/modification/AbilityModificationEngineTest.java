package com.lansoftprogramming.runeSequence.core.sequence.runtime.modification;

import com.lansoftprogramming.runeSequence.core.sequence.runtime.SequenceTooltip;
import com.lansoftprogramming.runeSequence.core.sequence.runtime.timing.AbilityTimingProfile;
import com.lansoftprogramming.runeSequence.core.sequence.runtime.timing.StepTimingView;
import com.lansoftprogramming.runeSequence.infrastructure.config.AbilityConfig;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

public class AbilityModificationEngineTest {

	@Test
	void shouldApplyOverrideWhileWindowOpenAndRemoveAfterExpiry() {
		AbilityConfig abilityConfig = new AbilityConfig();
		abilityConfig.putAbility("assault", new AbilityConfig.AbilityData());

		AbilityModificationRule rule = new AbilityModificationRule() {
			@Override
			public String id() {
				return "test-rule";
			}

			@Override
			public void onEvent(SequenceRuntimeContext context, SequenceEvent event, ModifierRuntime runtime) {
				if (event instanceof SequenceEvent.AbilityDetected detected && "trigger".equals(detected.abilityKey())) {
					runtime.openWindow("w", 1000);
					runtime.putOverride(new AbilityOverrideSpec(
							"o",
							AbilitySelectors.byKey("assault"),
							new AbilityOverridePatch(null, (short) 0, null, null),
							"w",
							null,
							null,
							false,
							0
					));
				}
			}
		};

		AbilityModificationEngine engine = new AbilityModificationEngine(List.of(rule));
		StepTimingView timing = timingStub();

		engine.onEvent(contextAt(abilityConfig, timing, 1000), new SequenceEvent.AbilityDetected("trigger", "trigger#0", StepPosition.CURRENT_STEP));

		AbilityTimingProfile base = new AbilityTimingProfile("assault", true, (short) 3, (short) 0, null);
		AbilityTimingProfile overridden = engine.applyTimingOverrides(contextAt(abilityConfig, timing, 1500), base);
		assertEquals(0, overridden.castDurationTicks());

		AbilityTimingProfile expired = engine.applyTimingOverrides(contextAt(abilityConfig, timing, 2501), base);
		assertEquals(3, expired.castDurationTicks());
	}

	@Test
	void shouldConsumeOverrideOnMatch() {
		AbilityConfig abilityConfig = new AbilityConfig();
		abilityConfig.putAbility("assault", new AbilityConfig.AbilityData());

		AbilityModificationRule rule = new AbilityModificationRule() {
			@Override
			public String id() {
				return "test-rule";
			}

			@Override
			public void onEvent(SequenceRuntimeContext context, SequenceEvent event, ModifierRuntime runtime) {
				if (event instanceof SequenceEvent.AbilityDetected detected && "trigger".equals(detected.abilityKey())) {
					runtime.openWindow("w", 1000);
					runtime.putOverride(new AbilityOverrideSpec(
							"o",
							AbilitySelectors.byKey("assault"),
							new AbilityOverridePatch(null, (short) 0, null, null),
							"w",
							null,
							1,
							true,
							0
					));
				}
			}
		};

		AbilityModificationEngine engine = new AbilityModificationEngine(List.of(rule));
		StepTimingView timing = timingStub();

		engine.onEvent(contextAt(abilityConfig, timing, 1000), new SequenceEvent.AbilityDetected("trigger", "trigger#0", StepPosition.CURRENT_STEP));

		AbilityTimingProfile base = new AbilityTimingProfile("assault", true, (short) 3, (short) 0, null);
		assertEquals(0, engine.applyTimingOverrides(contextAt(abilityConfig, timing, 1100), base).castDurationTicks());

		engine.onEvent(contextAt(abilityConfig, timing, 1200), new SequenceEvent.AbilityDetected("assault", "assault#0", StepPosition.CURRENT_STEP));
		assertEquals(3, engine.applyTimingOverrides(contextAt(abilityConfig, timing, 1300), base).castDurationTicks());
	}

	@Test
	void shouldAnchorRuntimeTooltipsToCurrentStep() {
		AbilityConfig abilityConfig = new AbilityConfig();
		AbilityModificationRule rule = new AbilityModificationRule() {
			@Override
			public String id() {
				return "test-rule";
			}

			@Override
			public void onEvent(SequenceRuntimeContext context, SequenceEvent event, ModifierRuntime runtime) {
				if (event instanceof SequenceEvent.SequenceInitialized) {
					runtime.putTooltip("t", new SequenceTooltip(0, null, "Hello"), null, null);
				}
			}
		};

		AbilityModificationEngine engine = new AbilityModificationEngine(List.of(rule));
		StepTimingView timing = timingStub();

		engine.onEvent(contextAt(abilityConfig, timing, 1000), new SequenceEvent.SequenceInitialized());
		List<SequenceTooltip> tooltips = engine.getRuntimeTooltips(new SequenceRuntimeContext(
				abilityConfig,
				7,
				List.of(),
				List.of(),
				timing,
				1100
		));

		assertEquals(1, tooltips.size());
		assertEquals(7, tooltips.getFirst().stepIndex());
		assertEquals("Hello", tooltips.getFirst().message());
	}

	private static SequenceRuntimeContext contextAt(AbilityConfig abilityConfig, StepTimingView timing, long nowMs) {
		return new SequenceRuntimeContext(
				abilityConfig,
				0,
				List.of("trigger"),
				List.of("assault"),
				timing,
				nowMs
		);
	}

	private static StepTimingView timingStub() {
		return new StepTimingView() {
			@Override
			public long getStepStartTimeMs() {
				return 0;
			}

			@Override
			public long getStepDurationMs() {
				return 0;
			}

			@Override
			public long getEffectiveElapsedMs(long nowMs) {
				return 0;
			}

			@Override
			public boolean isPaused() {
				return false;
			}
		};
	}
}

