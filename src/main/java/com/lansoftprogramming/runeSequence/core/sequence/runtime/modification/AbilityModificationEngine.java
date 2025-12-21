package com.lansoftprogramming.runeSequence.core.sequence.runtime.modification;

import com.lansoftprogramming.runeSequence.core.sequence.runtime.SequenceTooltip;
import com.lansoftprogramming.runeSequence.core.sequence.runtime.timing.AbilityTimingProfile;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.*;

/**
 * Generic runtime engine for conditional, temporary ability modifications.
 * <p>
 * Responsibilities:
 * <ul>
 *     <li>Maintain timing windows and tick counters across steps.</li>
 *     <li>Apply temporary patches to ability timing properties based on selectors.</li>
 *     <li>Emit timing directives (restart, duration changes, force-satisfy) without hard-coding any ability.</li>
 *     <li>Provide dynamic tooltips reusing {@link SequenceTooltip}.</li>
 * </ul>
 */
public class AbilityModificationEngine {

	private static final Logger logger = LoggerFactory.getLogger(AbilityModificationEngine.class);

	private final List<AbilityModificationRule> rules;

	private final Map<String, TimingWindow> windowsById = new HashMap<>();
	private final Map<String, TickCounter> countersById = new HashMap<>();
	private final LinkedHashMap<String, OverrideEntry> overridesById = new LinkedHashMap<>();
	private final LinkedHashMap<String, TooltipEntry> tooltipsById = new LinkedHashMap<>();
	private final List<TimingDirective> pendingTimingDirectives = new ArrayList<>();

	public AbilityModificationEngine(List<AbilityModificationRule> rules) {
		if (rules == null || rules.isEmpty()) {
			this.rules = List.of();
			return;
		}
		List<AbilityModificationRule> validated = new ArrayList<>(rules.size());
		for (AbilityModificationRule rule : rules) {
			if (rule == null) {
				continue;
			}
			rule.validate();
			validated.add(rule);
		}
		this.rules = List.copyOf(validated);
	}

	public static AbilityModificationEngine empty() {
		return new AbilityModificationEngine(List.of());
	}

	public void reset() {
		windowsById.clear();
		countersById.clear();
		overridesById.clear();
		tooltipsById.clear();
		pendingTimingDirectives.clear();
	}

	public void onEvent(SequenceRuntimeContext context, SequenceEvent event) {
		if (context == null || event == null) {
			return;
		}

		cleanup(context.nowMs());

		if (event instanceof SequenceEvent.SequencePaused) {
			pauseAll(context.nowMs());
		} else if (event instanceof SequenceEvent.SequenceResumed) {
			resumeAll(context.nowMs());
		}

		if (rules.isEmpty()) {
			return;
		}

		for (AbilityModificationRule rule : rules) {
			try {
				rule.onEvent(context, event, new ScopedRuntime(rule.id(), context));
			} catch (Exception e) {
				logger.error("Modifier rule '{}' failed on event {}", rule.id(), event.getClass().getSimpleName(), e);
			}
		}

		if (event instanceof SequenceEvent.AbilityDetected detected) {
			consumeOverridesOnMatch(context, detected.abilityKey(), detected.instanceId());
		} else if (event instanceof SequenceEvent.AbilityUsed used) {
			consumeOverridesOnMatch(context, used.abilityKey(), used.instanceId());
		}

		cleanup(context.nowMs());
	}

	public AbilityTimingProfile applyTimingOverrides(SequenceRuntimeContext context, AbilityTimingProfile baseProfile) {
		if (context == null || baseProfile == null) {
			return baseProfile;
		}

		cleanup(context.nowMs());

		AbilityRef ref = new AbilityRef(
				baseProfile.abilityKey(),
				null,
				context.getAbilityData(baseProfile.abilityKey()).orElse(null),
				null
		);

		AbilityTimingProfile next = baseProfile;
		for (OverrideEntry entry : activeOverridesInOrder(context)) {
			if (!entry.selector.matches(ref, context)) {
				continue;
			}
			next = entry.patch.applyTo(next);
		}
		return next;
	}

	public List<SequenceTooltip> getRuntimeTooltips(SequenceRuntimeContext context) {
		if (context == null) {
			return List.of();
		}
		cleanup(context.nowMs());

		List<SequenceTooltip> out = new ArrayList<>();
		for (TooltipEntry entry : tooltipsById.values()) {
			if (!entry.isActive(context.nowMs(), windowsById)) {
				continue;
			}
			SequenceTooltip tooltip = entry.tooltip;
			out.add(new SequenceTooltip(context.currentStepIndex(), tooltip.abilityInstanceId(), tooltip.message()));
		}
		return out.isEmpty() ? List.of() : List.copyOf(out);
	}

	public List<TimingDirective> drainTimingDirectives() {
		if (pendingTimingDirectives.isEmpty()) {
			return List.of();
		}
		List<TimingDirective> drained = List.copyOf(pendingTimingDirectives);
		pendingTimingDirectives.clear();
		return drained;
	}

	private void pauseAll(long nowMs) {
		for (TimingWindow window : windowsById.values()) {
			window.pause(nowMs);
		}
		for (TickCounter counter : countersById.values()) {
			counter.pause(nowMs);
		}
	}

	private void resumeAll(long nowMs) {
		for (TimingWindow window : windowsById.values()) {
			window.resume(nowMs);
		}
		for (TickCounter counter : countersById.values()) {
			counter.resume(nowMs);
		}
	}

	private void cleanup(long nowMs) {
		if (!windowsById.isEmpty()) {
			Iterator<Map.Entry<String, TimingWindow>> it = windowsById.entrySet().iterator();
			while (it.hasNext()) {
				Map.Entry<String, TimingWindow> entry = it.next();
				TimingWindow window = entry.getValue();
				if (window == null || window.isExpired(nowMs)) {
					it.remove();
				}
			}
		}

		if (!countersById.isEmpty()) {
			Iterator<Map.Entry<String, TickCounter>> it = countersById.entrySet().iterator();
			while (it.hasNext()) {
				Map.Entry<String, TickCounter> entry = it.next();
				TickCounter counter = entry.getValue();
				if (counter == null || counter.isExpired(nowMs)) {
					it.remove();
				}
			}
		}

		if (!overridesById.isEmpty()) {
			Iterator<Map.Entry<String, OverrideEntry>> it = overridesById.entrySet().iterator();
			while (it.hasNext()) {
				Map.Entry<String, OverrideEntry> entry = it.next();
				OverrideEntry override = entry.getValue();
				if (override == null || !override.isActive(nowMs, windowsById)) {
					it.remove();
				}
			}
		}

		if (!tooltipsById.isEmpty()) {
			Iterator<Map.Entry<String, TooltipEntry>> it = tooltipsById.entrySet().iterator();
			while (it.hasNext()) {
				Map.Entry<String, TooltipEntry> entry = it.next();
				TooltipEntry tooltip = entry.getValue();
				if (tooltip == null || !tooltip.isActive(nowMs, windowsById)) {
					it.remove();
				}
			}
		}
	}

	private List<OverrideEntry> activeOverridesInOrder(SequenceRuntimeContext context) {
		long nowMs = context.nowMs();
		if (overridesById.isEmpty()) {
			return List.of();
		}
		List<OverrideEntry> active = new ArrayList<>();
		for (OverrideEntry entry : overridesById.values()) {
			if (entry.isActive(nowMs, windowsById)) {
				active.add(entry);
			}
		}
		active.sort(Comparator.comparingInt((OverrideEntry o) -> o.priority).thenComparingLong(o -> o.createdAtNanos));
		return active;
	}

	private void consumeOverridesOnMatch(SequenceRuntimeContext context, String abilityKey, String instanceId) {
		if (abilityKey == null) {
			return;
		}

		AbilityRef ref = new AbilityRef(
				abilityKey,
				instanceId,
				context.getAbilityData(abilityKey).orElse(null),
				null
		);

		Iterator<Map.Entry<String, OverrideEntry>> it = overridesById.entrySet().iterator();
		while (it.hasNext()) {
			Map.Entry<String, OverrideEntry> entry = it.next();
			OverrideEntry override = entry.getValue();
			if (override == null || !override.consumeOnMatch || override.usesRemaining == null) {
				continue;
			}
			if (!override.isActive(context.nowMs(), windowsById)) {
				continue;
			}
			if (!override.selector.matches(ref, context)) {
				continue;
			}
			int remaining = override.usesRemaining.intValue() - 1;
			if (remaining <= 0) {
				it.remove();
			} else {
				override.usesRemaining = remaining;
			}
		}
	}

	private String qualify(String ruleId, String localId) {
		String safeRule = ruleId != null ? ruleId.trim() : "";
		String safeLocal = localId != null ? localId.trim() : "";
		return safeRule + ":" + safeLocal;
	}

	private final class ScopedRuntime implements ModifierRuntime {
		private final String ruleId;
		private final SequenceRuntimeContext context;

		private ScopedRuntime(String ruleId, SequenceRuntimeContext context) {
			this.ruleId = ruleId != null ? ruleId : "";
			this.context = context;
		}

		@Override
		public TimingWindow openWindow(String windowId, long durationMs) {
			String qualifiedId = qualify(ruleId, windowId);
			TimingWindow window = windowsById.computeIfAbsent(qualifiedId, id -> new TimingWindow());
			window.open(context.nowMs(), durationMs);
			return window;
		}

		@Override
		public Optional<TimingWindow> getWindow(String windowId) {
			String qualifiedId = qualify(ruleId, windowId);
			return Optional.ofNullable(windowsById.get(qualifiedId));
		}

		@Override
		public void closeWindow(String windowId) {
			String qualifiedId = qualify(ruleId, windowId);
			TimingWindow window = windowsById.get(qualifiedId);
			if (window != null) {
				window.close();
			}
		}

		@Override
		public TickCounter startTickCounter(String counterId, int ticks, long tickMs) {
			String qualifiedId = qualify(ruleId, counterId);
			TickCounter counter = countersById.computeIfAbsent(qualifiedId, id -> new TickCounter(tickMs));
			counter.start(ticks, context.nowMs());
			return counter;
		}

		@Override
		public Optional<TickCounter> getTickCounter(String counterId) {
			String qualifiedId = qualify(ruleId, counterId);
			return Optional.ofNullable(countersById.get(qualifiedId));
		}

		@Override
		public void stopTickCounter(String counterId) {
			String qualifiedId = qualify(ruleId, counterId);
			TickCounter counter = countersById.get(qualifiedId);
			if (counter != null) {
				counter.stop();
			}
		}

		@Override
		public void putOverride(AbilityOverrideSpec spec) {
			if (spec == null) {
				return;
			}
			String qualifiedId = qualify(ruleId, spec.id());
			String qualifiedWindow = spec.activeWhileWindowId() != null ? qualify(ruleId, spec.activeWhileWindowId()) : null;
			OverrideEntry entry = new OverrideEntry(spec.selector(), spec.patch(), qualifiedWindow, spec.expiresAtMs(),
					spec.usesRemaining(), spec.consumeOnMatch(), spec.priority(), System.nanoTime());
			overridesById.put(qualifiedId, entry);
		}

		@Override
		public void removeOverride(String overrideId) {
			String qualifiedId = qualify(ruleId, overrideId);
			overridesById.remove(qualifiedId);
		}

		@Override
		public void putTooltip(String tooltipId, SequenceTooltip tooltip, String activeWhileWindowId, Long expiresAtMs) {
			if (tooltip == null) {
				return;
			}
			String qualifiedId = qualify(ruleId, tooltipId);
			String qualifiedWindow = activeWhileWindowId != null ? qualify(ruleId, activeWhileWindowId) : null;
			tooltipsById.put(qualifiedId, new TooltipEntry(tooltip, qualifiedWindow, expiresAtMs));
		}

		@Override
		public void removeTooltip(String tooltipId) {
			String qualifiedId = qualify(ruleId, tooltipId);
			tooltipsById.remove(qualifiedId);
		}

		@Override
		public void emitTimingDirective(TimingDirective directive) {
			if (directive == null) {
				return;
			}
			pendingTimingDirectives.add(directive);
		}
	}

	private static final class OverrideEntry {
		private final AbilitySelector selector;
		private final AbilityOverridePatch patch;
		private final String activeWhileWindowId;
		private final Long expiresAtMs;
		private Integer usesRemaining;
		private final boolean consumeOnMatch;
		private final int priority;
		private final long createdAtNanos;

		private OverrideEntry(AbilitySelector selector,
		                      AbilityOverridePatch patch,
		                      String activeWhileWindowId,
		                      Long expiresAtMs,
		                      Integer usesRemaining,
		                      boolean consumeOnMatch,
		                      int priority,
		                      long createdAtNanos) {
			this.selector = selector;
			this.patch = patch;
			this.activeWhileWindowId = activeWhileWindowId;
			this.expiresAtMs = expiresAtMs;
			this.usesRemaining = usesRemaining;
			this.consumeOnMatch = consumeOnMatch;
			this.priority = priority;
			this.createdAtNanos = createdAtNanos;
		}

		private boolean isActive(long nowMs, Map<String, TimingWindow> windowsById) {
			if (expiresAtMs != null && nowMs >= expiresAtMs.longValue()) {
				return false;
			}
			if (usesRemaining != null && usesRemaining.intValue() <= 0) {
				return false;
			}
			if (activeWhileWindowId != null) {
				TimingWindow window = windowsById.get(activeWhileWindowId);
				if (window == null || window.isExpired(nowMs)) {
					return false;
				}
			}
			return true;
		}
	}

	private static final class TooltipEntry {
		private final SequenceTooltip tooltip;
		private final String activeWhileWindowId;
		private final Long expiresAtMs;

		private TooltipEntry(SequenceTooltip tooltip, String activeWhileWindowId, Long expiresAtMs) {
			this.tooltip = tooltip;
			this.activeWhileWindowId = activeWhileWindowId;
			this.expiresAtMs = expiresAtMs;
		}

		private boolean isActive(long nowMs, Map<String, TimingWindow> windowsById) {
			if (expiresAtMs != null && nowMs >= expiresAtMs.longValue()) {
				return false;
			}
			if (activeWhileWindowId != null) {
				TimingWindow window = windowsById.get(activeWhileWindowId);
				if (window == null || window.isExpired(nowMs)) {
					return false;
				}
			}
			return true;
		}
	}
}
