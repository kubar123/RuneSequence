package com.lansoftprogramming.runeSequence.core.sequence.runtime.modification;

import com.lansoftprogramming.runeSequence.core.sequence.runtime.timing.AbilityTimingProfile;
import com.lansoftprogramming.runeSequence.core.sequence.runtime.timing.StepTimingView;
import com.lansoftprogramming.runeSequence.infrastructure.config.AbilityConfig;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class GreaterBargeAlwaysRuleTest {

	@Test
		void shouldWaitEightTicksThenHoldGbargeUntilUsedAndApplyOneShotCastOverrideAfterwards() {
			AbilityConfig abilityConfig = new AbilityConfig();
			abilityConfig.putAbility("gbarge", new AbilityConfig.AbilityData());
			abilityConfig.putAbility("assault", new AbilityConfig.AbilityData());

			MutableTimingView timing = new MutableTimingView();
			timing.stepDurationMs = 1800;

			AbilityModificationEngine engine = new AbilityModificationEngine(List.of(new GreaterBargeAlwaysRule()));

			long t0 = 1000;
		SequenceRuntimeContext ctxStart = new SequenceRuntimeContext(
				abilityConfig,
				0,
				List.of("fury"),
				List.of("gbarge"),
				timing,
				t0
		);

			engine.onEvent(ctxStart, new SequenceEvent.StepStarted(0));
			List<TimingDirective> startDirectives = engine.drainTimingDirectives();
			assertTrue(startDirectives.stream().anyMatch(d -> d instanceof TimingDirective.PauseStepTimer));
			applyTiming(timing, startDirectives);

			long t5 = t0 + 5 * 600L;
			timing.effectiveElapsedMs = 5 * 600L;
			SequenceRuntimeContext ctxMid = new SequenceRuntimeContext(
					abilityConfig,
				0,
				List.of("fury"),
				List.of("gbarge"),
				timing,
					t5
			);
			engine.onEvent(ctxMid, new SequenceEvent.Heartbeat());
			List<TimingDirective> midDirectives = engine.drainTimingDirectives();
			assertTrue(midDirectives.isEmpty());

			// If gbarge is "used" before the 8 tick wait completes, ignore it.
			engine.onEvent(
					new SequenceRuntimeContext(abilityConfig, 0, List.of("fury"), List.of("gbarge"), timing, t5 + 1),
					new SequenceEvent.AbilityUsed("gbarge", "gbarge#0", StepPosition.NEXT_STEP)
			);
			assertTrue(engine.drainTimingDirectives().isEmpty());

			long t8 = t0 + 8 * 600L;
			timing.effectiveElapsedMs = 8 * 600L;
			SequenceRuntimeContext ctxAfter = new SequenceRuntimeContext(
					abilityConfig,
					0,
					List.of("fury"),
					List.of("gbarge"),
					timing,
					t8
			);
			engine.onEvent(ctxAfter, new SequenceEvent.Heartbeat());
			List<TimingDirective> afterDirectives = engine.drainTimingDirectives();
			assertTrue(afterDirectives.stream().anyMatch(d -> d instanceof TimingDirective.ResumeStepTimer));
			assertTrue(afterDirectives.stream().anyMatch(d -> d instanceof TimingDirective.ForceStepSatisfiedAt));
			applyTiming(timing, afterDirectives);

			long tGbargeStep = t0 + 9 * 600L;
			timing.effectiveElapsedMs = 600L;
			SequenceRuntimeContext ctxGbarge = new SequenceRuntimeContext(
					abilityConfig,
					0,
					List.of("gbarge"),
					List.of("assault"),
					timing,
					tGbargeStep
			);
			engine.onEvent(ctxGbarge, new SequenceEvent.Heartbeat());
			List<TimingDirective> holdDirectives = engine.drainTimingDirectives();
			assertTrue(holdDirectives.stream().anyMatch(d -> d instanceof TimingDirective.PauseStepTimer));
			assertFalse(holdDirectives.stream().anyMatch(d -> d instanceof TimingDirective.ForceStepSatisfiedAt));
			applyTiming(timing, holdDirectives);

			engine.onEvent(ctxGbarge, new SequenceEvent.AbilityUsed("gbarge", "gbarge#0", StepPosition.CURRENT_STEP));
			List<TimingDirective> gbargeDirectives = engine.drainTimingDirectives();
			assertTrue(gbargeDirectives.stream().anyMatch(d -> d instanceof TimingDirective.ResumeStepTimer));
			assertTrue(gbargeDirectives.stream().anyMatch(d -> d instanceof TimingDirective.ForceStepSatisfiedAt));
			applyTiming(timing, gbargeDirectives);

		AbilityTimingProfile baseAssault = new AbilityTimingProfile("assault", true, (short) 8, (short) 0, null);
		AbilityTimingProfile overridden = engine.applyTimingOverrides(
				new SequenceRuntimeContext(abilityConfig, 1, List.of("gbarge"), List.of("assault"), timing, tGbargeStep + 1),
				baseAssault
		);
		assertEquals(0, overridden.castDurationTicks());

		engine.onEvent(
				new SequenceRuntimeContext(abilityConfig, 2, List.of("assault"), List.of(), timing, tGbargeStep + 2),
				new SequenceEvent.AbilityUsed("assault", "assault#0", StepPosition.CURRENT_STEP)
		);
		AbilityTimingProfile consumed = engine.applyTimingOverrides(
				new SequenceRuntimeContext(abilityConfig, 2, List.of("assault"), List.of(), timing, tGbargeStep + 3),
				baseAssault
		);
			assertEquals(8, consumed.castDurationTicks());
		}

		private static void applyTiming(MutableTimingView timing, List<TimingDirective> directives) {
			for (TimingDirective directive : directives) {
				if (directive instanceof TimingDirective.PauseStepTimer) {
					timing.paused = true;
				} else if (directive instanceof TimingDirective.ResumeStepTimer) {
					timing.paused = false;
				}
			}
		}

		private static final class MutableTimingView implements StepTimingView {
			long effectiveElapsedMs;
			long stepDurationMs;
			boolean paused;

		@Override
		public long getStepStartTimeMs() {
			return 0;
		}

		@Override
		public long getStepDurationMs() {
			return stepDurationMs;
		}

		@Override
		public long getEffectiveElapsedMs(long nowMs) {
			return effectiveElapsedMs;
		}

			@Override
			public boolean isPaused() {
				return paused;
			}
		}
	}
