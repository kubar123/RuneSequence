package com.lansoftprogramming.runeSequence.core.sequence.runtime.modification;

import com.lansoftprogramming.runeSequence.core.sequence.runtime.SequenceTooltip;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;

/**
 * Runtime rule for the "Always GBarge" rotation setting.
 * <p>
 * Behaviour:
 * <ul>
 *     <li>If {@code gbarge} is the next expected step, pause step progression and show "Stop attacking boss" for 8 ticks.</li>
 *     <li>After 8 ticks, show "BARGE NOW!!", advance the step to {@code gbarge}, and keep the step timer paused indefinitely.</li>
 *     <li>When {@code gbarge} is visually detected as "used", resume timing, advance immediately, and open a short post window.</li>
 *     <li>During the post window, apply a one-shot castDuration=0 override to the first affected channeled ability.</li>
 * </ul>
 */
public final class GreaterBargeAlwaysRule implements AbilityModificationRule {

	public static final String RULE_ID = "always-gbarge";

	private static final Logger logger = LoggerFactory.getLogger(GreaterBargeAlwaysRule.class);

	private static final long TICK_MS = 600L;

	private static final String ABILITY_GBARGE = "gbarge";

	private static final int PRE_WAIT_TICKS = 8;
	// TickCounter entries are auto-removed as soon as they expire; keep one extra "sentinel" tick so we can observe the transition.
	private static final int PRE_WAIT_COUNTER_TICKS = PRE_WAIT_TICKS + 1;
	private static final int PRE_WAIT_COMPLETE_REMAINING_TICKS = 1;

	// Greater Barge's relevant follow-up window (ticks) for applying the one-shot cast-duration override.
	// This can be tuned if desired, but is intentionally independent of the pre-wait counter.
	private static final int POST_WINDOW_TICKS = 8;

	private static final String COUNTER_PRE = "pre_gbarge_ticks";
	private static final String TOOLTIP_STOP_ATTACKING = "pre_gbarge_stop_attacking_tooltip";
	private static final String TOOLTIP_BARGE_NOW = "pre_gbarge_barge_now_tooltip";

	private static final String WINDOW_POST = "post_gbarge_window";
	private static final String COUNTER_POST = "post_gbarge_ticks";
	private static final String OVERRIDE_POST = "post_gbarge_cast0";

	private static final List<String> AFFECTED_ABILITY_KEYS = List.of(
			"assault",
			"flurry",
			"gflurry",
			"destroy",
			"frenzy"
	);

	@Override
	public String id() {
		return RULE_ID;
	}

	@Override
	public void onEvent(SequenceRuntimeContext ctx, SequenceEvent event, ModifierRuntime rt) {
		if (ctx == null || event == null || rt == null) {
			return;
		}

		boolean gbargeIsCurrent = ctx.currentStepAbilityKeys().contains(ABILITY_GBARGE);
		boolean gbargeIsNext = ctx.nextStepAbilityKeys().contains(ABILITY_GBARGE);

		if (event instanceof SequenceEvent.StepStarted || event instanceof SequenceEvent.StepAdvanced) {
			if (!gbargeIsCurrent && !gbargeIsNext) {
				stopPreWait(rt, "step_changed_and_gbarge_not_adjacent");
				ensureStepTimerResumed(ctx, rt, "step_changed_and_gbarge_not_adjacent");
			} else if (gbargeIsNext && !gbargeIsCurrent && rt.getTickCounter(COUNTER_PRE).isEmpty()) {
				startPreWait(ctx, rt);
			} else if (gbargeIsCurrent) {
				ensureStepTimerPaused(ctx, rt, "gbarge_step_hold");
			}
		}

		if (event instanceof SequenceEvent.AbilityUsed used) {
			handleAbilityUsed(ctx, used, rt);
		} else if (event instanceof SequenceEvent.Heartbeat) {
			handleHeartbeat(ctx, rt);
		}
	}

	private void handleAbilityUsed(SequenceRuntimeContext ctx, SequenceEvent.AbilityUsed used, ModifierRuntime rt) {
		if (used.abilityKey() == null) {
			return;
		}

		boolean gbargeIsCurrent = ctx.currentStepAbilityKeys().contains(ABILITY_GBARGE);
		boolean gbargeIsNext = ctx.nextStepAbilityKeys().contains(ABILITY_GBARGE);

		if (ABILITY_GBARGE.equals(used.abilityKey())) {
			int remainingPreTicks = rt.getTickCounter(COUNTER_PRE)
					.map(counter -> counter.remainingTicks(ctx.nowMs()))
					.orElse(0);
			if (remainingPreTicks > PRE_WAIT_COMPLETE_REMAINING_TICKS) {
				// Per spec: do not accept GBarge use until the 8-tick wait completes.
				logger.info("AlwaysGBarge: gbarge used detected early but ignored (remainingPreTicks={})", remainingPreTicks);
				return;
			}

			logger.info("AlwaysGBarge: gbarge used detected (position={}, gbargeIsCurrent={}, gbargeIsNext={}) -> starting post window",
					used.position(), gbargeIsCurrent, gbargeIsNext);
			stopPreWait(rt, "gbarge_used_detected");

			// Per spec: upon using GBarge, tick forward immediately to the follow-up step.
			ensureStepTimerResumed(ctx, rt, "gbarge_used_detected");
			emitForceStepSatisfied(ctx, rt, "gbarge_used_detected");

			startPostWindow(ctx, rt);
		}
	}

	private void handleHeartbeat(SequenceRuntimeContext ctx, ModifierRuntime rt) {
		boolean gbargeIsCurrent = ctx.currentStepAbilityKeys().contains(ABILITY_GBARGE);
		boolean gbargeIsNext = ctx.nextStepAbilityKeys().contains(ABILITY_GBARGE);

		if (!gbargeIsCurrent && !gbargeIsNext) {
			stopPreWait(rt, "heartbeat_gbarge_not_adjacent");
			ensureStepTimerResumed(ctx, rt, "heartbeat_gbarge_not_adjacent");
			return;
		}

		long nowMs = ctx.nowMs();

		if (gbargeIsNext && !gbargeIsCurrent) {
			ensureStepTimerPaused(ctx, rt, "pre_wait_active");
			if (rt.getTickCounter(COUNTER_PRE).isEmpty()) {
				startPreWait(ctx, rt);
			}

			int remainingTicks = rt.getTickCounter(COUNTER_PRE)
					.map(counter -> counter.remainingTicks(nowMs))
					.orElse(0);
			if (remainingTicks > PRE_WAIT_COMPLETE_REMAINING_TICKS) {
				rt.putTooltip(TOOLTIP_STOP_ATTACKING, new SequenceTooltip(ctx.currentStepIndex(), null, "Stop attacking boss"), null, null);
				rt.removeTooltip(TOOLTIP_BARGE_NOW);
				return;
			}

			rt.removeTooltip(TOOLTIP_STOP_ATTACKING);
			rt.removeTooltip(TOOLTIP_BARGE_NOW);
			rt.stopTickCounter(COUNTER_PRE);

			// Countdown complete: advance to the GBarge step immediately.
			ensureStepTimerResumed(ctx, rt, "pre_wait_complete");
			emitForceStepSatisfied(ctx, rt, "pre_wait_complete");
			return;
		}

		if (gbargeIsCurrent) {
			rt.removeTooltip(TOOLTIP_STOP_ATTACKING);
			rt.putTooltip(TOOLTIP_BARGE_NOW, new SequenceTooltip(ctx.currentStepIndex(), null, "BARGE NOW!!"), null, null);
			ensureStepTimerPaused(ctx, rt, "await_gbarge_used");
		}
	}

	private void startPreWait(SequenceRuntimeContext ctx, ModifierRuntime rt) {
		logger.info("AlwaysGBarge: starting pre-wait ({} ticks) because next is gbarge", PRE_WAIT_TICKS);
		rt.startTickCounter(COUNTER_PRE, PRE_WAIT_COUNTER_TICKS, TICK_MS);
		rt.removeTooltip(TOOLTIP_BARGE_NOW);
		rt.putTooltip(TOOLTIP_STOP_ATTACKING, new SequenceTooltip(ctx.currentStepIndex(), null, "Stop attacking boss"), null, null);
		ensureStepTimerPaused(ctx, rt, "start_pre_wait");
	}

	private void stopPreWait(ModifierRuntime rt, String reason) {
		boolean wasActive = rt.getTickCounter(COUNTER_PRE).isPresent();
		rt.stopTickCounter(COUNTER_PRE);
		rt.removeTooltip(TOOLTIP_STOP_ATTACKING);
		rt.removeTooltip(TOOLTIP_BARGE_NOW);
		if (wasActive) {
			logger.info("AlwaysGBarge: pre-wait stopped (reason={})", reason);
		}
	}

	private void startPostWindow(SequenceRuntimeContext ctx, ModifierRuntime rt) {
		logger.info("AlwaysGBarge: opening post window ({} ticks) for one-shot cast override", POST_WINDOW_TICKS);
		rt.openWindow(WINDOW_POST, (long) POST_WINDOW_TICKS * TICK_MS);
		rt.startTickCounter(COUNTER_POST, POST_WINDOW_TICKS, TICK_MS);

		rt.putOverride(new AbilityOverrideSpec(
				OVERRIDE_POST,
				AbilitySelectors.byKeys(AFFECTED_ABILITY_KEYS),
				new AbilityOverridePatch(null, (short) 0, null, null),
				WINDOW_POST,
				null,
				1,
				true,
					0
			));
	}

	private void ensureStepTimerPaused(SequenceRuntimeContext ctx, ModifierRuntime rt, String reason) {
		if (ctx.stepTiming().isPaused()) {
			return;
		}
		logger.info("AlwaysGBarge: TimingDirective PauseStepTimer (reason={})", reason);
		rt.emitTimingDirective(new TimingDirective.PauseStepTimer());
	}

	private void ensureStepTimerResumed(SequenceRuntimeContext ctx, ModifierRuntime rt, String reason) {
		if (!ctx.stepTiming().isPaused()) {
			return;
		}
		logger.info("AlwaysGBarge: TimingDirective ResumeStepTimer (reason={})", reason);
		rt.emitTimingDirective(new TimingDirective.ResumeStepTimer());
	}

	private void emitForceStepSatisfied(SequenceRuntimeContext ctx, ModifierRuntime rt, String reason) {
		logger.info("AlwaysGBarge: TimingDirective ForceStepSatisfiedAt (reason={})", reason);
		rt.emitTimingDirective(new TimingDirective.ForceStepSatisfiedAt(ctx.nowMs()));
	}
}
