package com.lansoftprogramming.runeSequence.application;

import com.lansoftprogramming.runeSequence.core.detection.DetectionResult;
import com.lansoftprogramming.runeSequence.core.detection.TemplateDetector;
import com.lansoftprogramming.runeSequence.core.sequence.model.EffectiveAbilityConfig;
import com.lansoftprogramming.runeSequence.core.sequence.model.SequenceDefinition;
import com.lansoftprogramming.runeSequence.core.sequence.runtime.ActiveSequence;
import com.lansoftprogramming.runeSequence.core.sequence.runtime.SequenceTooltip;
import com.lansoftprogramming.runeSequence.core.sequence.runtime.TooltipSchedule;
import com.lansoftprogramming.runeSequence.core.sequence.runtime.modification.AbilityModificationEngine;
import com.lansoftprogramming.runeSequence.core.sequence.runtime.modification.GreaterBargeAlwaysRule;
import com.lansoftprogramming.runeSequence.core.sequence.runtime.modification.StepPosition;
import com.lansoftprogramming.runeSequence.infrastructure.config.AbilityConfig;
import com.lansoftprogramming.runeSequence.ui.notification.NotificationService;
import org.bytedeco.opencv.opencv_core.Mat;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.awt.*;
import java.util.*;
import java.util.List;
import java.util.function.Consumer;

public class SequenceManager implements SequenceController.StateChangeListener {

	private static final Logger logger = LoggerFactory.getLogger(SequenceManager.class);

	private final AbilityConfig abilityConfig;
	private final Map<String, SequenceDefinition> namedSequences;
	private final Map<String, TooltipSchedule> tooltipSchedules;
	private final NotificationService notifications;
	private final TemplateDetector templateDetector;
	private ActiveSequence activeSequence;
	private SequenceController sequenceController;
	private final GcdLatchTracker gcdLatchTracker = new GcdLatchTracker();
	private final AlwaysGBargeTracker alwaysGBargeTracker = new AlwaysGBargeTracker();
	private final List<Consumer<SequenceProgress>> progressListeners = new ArrayList<>();
	private boolean sequenceComplete = false;
	private String activeSequenceId;
	private final Map<String, Boolean> alwaysGBargeEnabledBySequenceId;

	private Rectangle resolveAbilityRoiForRequirement(ActiveSequence.DetectionRequirement requirement, Mat frame) {
		if (requirement == null || frame == null) {
			return null;
		}
		EffectiveAbilityConfig effectiveConfig = requirement.effectiveAbilityConfig();
		Double threshold = effectiveConfig != null
				? effectiveConfig.getDetectionThreshold().orElse(null)
				: null;
		return templateDetector.resolveAbilityRoi(frame, requirement.abilityKey(), threshold);
	}

	public SequenceManager(Map<String, SequenceDefinition> namedSequences,
	                       Map<String, TooltipSchedule> tooltipSchedules,
	                       AbilityConfig abilityConfig,
	                       NotificationService notifications,
	                       TemplateDetector templateDetector) {
		this(namedSequences, tooltipSchedules, abilityConfig, notifications, templateDetector, Map.of());
	}

	public SequenceManager(Map<String, SequenceDefinition> namedSequences,
	                       Map<String, TooltipSchedule> tooltipSchedules,
	                       AbilityConfig abilityConfig,
	                       NotificationService notifications,
	                       TemplateDetector templateDetector,
	                       Map<String, Boolean> alwaysGBargeEnabledBySequenceId) {
		this.abilityConfig = Objects.requireNonNull(abilityConfig);
		this.namedSequences = Objects.requireNonNull(namedSequences);
		this.tooltipSchedules = tooltipSchedules != null
				? new HashMap<>(tooltipSchedules)
				: new HashMap<>();
		this.notifications = Objects.requireNonNull(notifications);
		this.templateDetector = Objects.requireNonNull(templateDetector);
		this.alwaysGBargeEnabledBySequenceId = alwaysGBargeEnabledBySequenceId != null
				? new HashMap<>(alwaysGBargeEnabledBySequenceId)
				: new HashMap<>();
	}
	public void setSequenceController(SequenceController sequenceController) {
		if (this.sequenceController != null) {
			this.sequenceController.removeStateChangeListener(this);
		}
		this.sequenceController = sequenceController;
		if (sequenceController != null) {
			sequenceController.addStateChangeListener(this);
		}
	}
	// -------------------------
	// Public API
	// -------------------------

	public synchronized boolean activateSequence(String name) {
		if (name == null || name.isBlank()) {
			logger.warn("Sequence not found: <blank>");
			clearActiveSequence();
			return false;
		}
		SequenceDefinition def = namedSequences.get(name);
		if (def == null) {
			logger.warn("Sequence not found: {}", name);
			clearActiveSequence();
			return false;
		}

		if (activeSequence != null && sequenceController != null) {
			sequenceController.removeStateChangeListener(activeSequence);
		}

		this.activeSequence = new ActiveSequence(def, abilityConfig, buildModificationEngine(name));
		this.sequenceComplete = false;
		this.activeSequenceId = name;
		gcdLatchTracker.reset();
		alwaysGBargeTracker.reset();

		if (sequenceController != null) {
			sequenceController.addStateChangeListener(activeSequence);
			activeSequence.stepTimer.pause();
		}

		emitProgressUpdate();

		return true;
	}

	/**
	 * Upserts the runtime definition and tooltip schedule for a named sequence.
	 * If {@code definition} is null, the sequence is removed from the manager.
	 */
	public synchronized void upsertSequence(String id,
	                                        SequenceDefinition definition,
	                                        TooltipSchedule schedule) {
		upsertSequence(id, definition, schedule, null);
	}

	/**
	 * Upserts the runtime definition + schedule and optionally updates rotation-wide settings.
	 */
	public synchronized void upsertSequence(String id,
	                                        SequenceDefinition definition,
	                                        TooltipSchedule schedule,
	                                        Boolean alwaysGBargeEnabled) {
		if (id == null || id.isBlank()) {
			return;
		}

		if (alwaysGBargeEnabled != null) {
			alwaysGBargeEnabledBySequenceId.put(id, alwaysGBargeEnabled);
		}

		if (definition == null) {
			namedSequences.remove(id);
			tooltipSchedules.remove(id);
			alwaysGBargeEnabledBySequenceId.remove(id);
			if (id.equals(activeSequenceId)) {
				clearActiveSequence();
			}
			return;
		}

		namedSequences.put(id, definition);
		if (schedule != null) {
			tooltipSchedules.put(id, schedule);
		} else {
			tooltipSchedules.remove(id);
		}

		if (id.equals(activeSequenceId)) {
			activateSequence(id);
		}
	}

	private AbilityModificationEngine buildModificationEngine(String sequenceId) {
		if (sequenceId == null) {
			return AbilityModificationEngine.empty();
		}
		boolean alwaysGBargeEnabled = Boolean.TRUE.equals(alwaysGBargeEnabledBySequenceId.get(sequenceId));
		if (!alwaysGBargeEnabled) {
			return AbilityModificationEngine.empty();
		}
		return new AbilityModificationEngine(List.of(new GreaterBargeAlwaysRule()));
	}

	public synchronized void clearActiveSequence() {
		if (activeSequence != null && sequenceController != null) {
			sequenceController.removeStateChangeListener(activeSequence);
		}
		activeSequence = null;
		activeSequenceId = null;
		sequenceComplete = false;
		gcdLatchTracker.reset();
		alwaysGBargeTracker.reset();
		emitProgressUpdate();
	}
	/**
	 * Return the per-occurrence detection requirements (current + next step).
	 */
	public synchronized List<ActiveSequence.DetectionRequirement> getDetectionRequirements() {
		if (activeSequence == null || sequenceComplete) {
			return List.of();
		}
		return activeSequence.getDetectionRequirements();
	}

	public synchronized void processDetection(Mat frame, List<DetectionResult> results) {

		if (sequenceComplete || activeSequence == null) {
			return;
		}

		// Keep latch state synced to latest detections before step timers react
		gcdLatchTracker.onFrame(frame, results);
		alwaysGBargeTracker.onFrame(frame);

		int previousStep = activeSequence.getCurrentStepIndex();

		activeSequence.processDetections(results);

		if (activeSequence.isComplete()) {
			onSequenceCompleted();
			return;
		}

		if (activeSequence.getCurrentStepIndex() != previousStep) {
			emitProgressUpdate();
		}
	}

	public synchronized List<DetectionResult> getCurrentAbilities() {
		if (activeSequence == null || sequenceComplete) {
			return List.of();
		}
		return activeSequence.getCurrentAbilities();
	}

	public synchronized List<DetectionResult> getNextAbilities() {
		if (activeSequence == null || sequenceComplete) {
			return List.of();
		}
		return activeSequence.getNextAbilities();
	}

	/**
	 * Returns tooltip messages associated with the current step of the active sequence.
	 * Tooltips are display-only annotations and do not influence detection or timing.
	 */
	public synchronized List<SequenceTooltip> getCurrentTooltips() {
		if (activeSequence == null || sequenceComplete) {
			return List.of();
		}
		List<SequenceTooltip> runtimeTooltips = activeSequence.getRuntimeTooltips();

		if (activeSequenceId == null) {
			return runtimeTooltips;
		}
		TooltipSchedule schedule = tooltipSchedules.get(activeSequenceId);
		if (schedule == null) {
			return runtimeTooltips;
		}
		int stepIndex = activeSequence.getCurrentStepIndex();
		List<SequenceTooltip> scheduledTooltips = schedule.getTooltipsForStep(stepIndex);
		if (scheduledTooltips.isEmpty()) {
			return runtimeTooltips;
		}
		if (runtimeTooltips.isEmpty()) {
			return scheduledTooltips;
		}
		List<SequenceTooltip> merged = new ArrayList<>(scheduledTooltips.size() + runtimeTooltips.size());
		merged.addAll(scheduledTooltips);
		merged.addAll(runtimeTooltips);
		return List.copyOf(merged);
	}

	public synchronized List<String> getActiveSequenceAbilityKeys() {
		if (activeSequence == null || sequenceComplete) {
			return List.of();
		}
		return activeSequence.getAllAbilityKeys();
	}

	public synchronized boolean moveActiveSequenceToStep(int stepIndex) {
		if (activeSequence == null) {
			return false;
		}
		activeSequence.forceStepIndex(stepIndex);
		sequenceComplete = activeSequence.isComplete();
		gcdLatchTracker.reset();
		emitProgressUpdate();
		return true;
	}

	public synchronized List<ActiveSequence.DetectionRequirement> previewGcdLatchRequirements() {
		return gcdLatchTracker.previewGcdRequirements();
	}

	public synchronized void addNamedSequence(String name, SequenceDefinition def) {
		namedSequences.put(name, def);
	}


	public synchronized void resetActiveSequence() {
		boolean shouldRearm = sequenceController != null && sequenceController.isArmed();
		resetActiveSequence(shouldRearm);
	}

	public synchronized void resetActiveSequence(boolean rearmLatchIfArmed) {
		if (activeSequence != null) {
			activeSequence.reset();
			activeSequence.stepTimer.pause();
		}
		sequenceComplete = false;
		alwaysGBargeTracker.reset();
		if (rearmLatchIfArmed && sequenceController != null && sequenceController.isArmed()) {
			// If we're already ARMED, manually re-arm the latch tracker because no state change event will fire.
			gcdLatchTracker.onStateChanged(SequenceController.State.ARMED);
		} else {
			gcdLatchTracker.reset();
		}
		emitProgressUpdate();
	}

	public synchronized boolean hasActiveSequence() {
		boolean hasActive = activeSequence != null;
		return hasActive;
	}

	public synchronized boolean isSequenceComplete() {
		return sequenceComplete;
	}

	public synchronized boolean shouldDetect() {
		return activeSequence != null && !sequenceComplete;
	}

	public void addProgressListener(Consumer<SequenceProgress> listener) {
		if (listener == null) {
			return;
		}
		synchronized (progressListeners) {
			progressListeners.add(listener);
		}
	}

	public void removeProgressListener(Consumer<SequenceProgress> listener) {
		if (listener == null) {
			return;
		}
		synchronized (progressListeners) {
			progressListeners.remove(listener);
		}
	}

	public synchronized SequenceProgress snapshotProgress() {
		if (activeSequence == null) {
			return SequenceProgress.inactive(activeSequenceId);
		}

		int stepIndex = activeSequence.getCurrentStepIndex();
		List<String> stepAbilityKeys = activeSequence.getAbilityKeysForStep(stepIndex);
		List<String> stepLabels = stepAbilityKeys.stream()
				.map(this::resolveAbilityLabel)
				.toList();

		return new SequenceProgress(
				true,
				activeSequenceId,
				stepIndex,
				activeSequence.getStepCount(),
				stepLabels,
				sequenceComplete
		);
	}

	@Override
	public synchronized void onStateChanged(SequenceController.State oldState, SequenceController.State newState) {
		// Keep detection-side latch phases aligned with UI state machine
		gcdLatchTracker.onStateChanged(newState);
		if (newState != SequenceController.State.RUNNING) {
			alwaysGBargeTracker.reset();
		}
	}

	private void onSequenceCompleted() {
		if (sequenceComplete) {
			return;
		}
		sequenceComplete = true;
		gcdLatchTracker.reset();
		alwaysGBargeTracker.reset();
		logger.info("Sequence complete - halting detections until restart");

		if (sequenceController != null) {
			sequenceController.onSequenceCompleted();
		}
		emitProgressUpdate();
	}

	private void emitProgressUpdate() {
		SequenceProgress progress = snapshotProgress();
		notifyProgressListeners(progress);
	}

	private void notifyProgressListeners(SequenceProgress progress) {
		List<Consumer<SequenceProgress>> listenersCopy;
		synchronized (progressListeners) {
			listenersCopy = List.copyOf(progressListeners);
		}
		for (Consumer<SequenceProgress> listener : listenersCopy) {
			try {
				listener.accept(progress);
			} catch (Exception e) {
				logger.error("Error notifying progress listener", e);
			}
		}
	}

	private String resolveAbilityLabel(String abilityKey) {
		if (abilityKey == null) {
			return "";
		}
		AbilityConfig.AbilityData abilityData = abilityConfig.getAbility(abilityKey);
		if (abilityData != null && abilityData.getCommonName() != null && !abilityData.getCommonName().isBlank()) {
			return abilityData.getCommonName();
		}
		return abilityKey;
	}

	public static class SequenceProgress {
		private final boolean hasActiveSequence;
		private final String sequenceId;
		private final int currentStepIndex;
		private final int totalSteps;
		private final List<String> currentStepAbilities;
		private final boolean sequenceComplete;

		private SequenceProgress(boolean hasActiveSequence, String sequenceId, int currentStepIndex, int totalSteps,
		                         List<String> currentStepAbilities, boolean sequenceComplete) {
			this.hasActiveSequence = hasActiveSequence;
			this.sequenceId = sequenceId;
			this.currentStepIndex = currentStepIndex;
			this.totalSteps = totalSteps;
			this.currentStepAbilities = List.copyOf(currentStepAbilities);
			this.sequenceComplete = sequenceComplete;
		}

		public static SequenceProgress inactive(String sequenceId) {
			return new SequenceProgress(false, sequenceId, -1, 0, List.of(), false);
		}

		public boolean hasActiveSequence() {
			return hasActiveSequence;
		}

		public String getSequenceId() {
			return sequenceId;
		}

		public int getCurrentStepIndex() {
			return currentStepIndex;
		}

		public int getTotalSteps() {
			return totalSteps;
		}

		public List<String> getCurrentStepAbilities() {
			return currentStepAbilities;
		}

		public boolean isSequenceComplete() {
			return sequenceComplete;
		}
	}

	private final class GcdLatchTracker {
		private static final double DARKEN_PERCENT_THRESHOLD = 0.25; // 25% drop from baseline
		private static final int DARKEN_FRAMES_REQUIRED = 3;
		private static final double MIN_BASELINE_BRIGHTNESS = 1.0;
		private static final int MAX_TRACKED_GCD_ABILITIES = 2;

		private boolean awaitingInitialDetection = false; // Waiting for baseline capture
		private boolean waitingForDarken = false; // Watching for brightness drops
		private List<TrackedTarget> trackedTargets = List.of();

		void onStateChanged(SequenceController.State newState) {
			if (newState == SequenceController.State.ARMED) {
				logger.info("ARMED: awaiting brightness baseline for latch");
				notifications.showInfo("Armed. Waiting for ability use to start the sequence.");
				awaitingInitialDetection = true;
				waitingForDarken = false;
				trackedTargets = List.of();
			} else {
				reset();
			}
		}

		void onFrame(Mat frame, List<DetectionResult> results) {
			if (sequenceController == null || frame == null || frame.empty()) {
				return;
			}

			if (awaitingInitialDetection) {
				beginTracking(frame);
			}

			if (!waitingForDarken || trackedTargets.isEmpty()) {
				return;
			}

			boolean allDarkened = true;
			for (TrackedTarget target : trackedTargets) {
				double sample = templateDetector.measureBrightness(frame, target.roi);
				target.updateFromBrightness(sample);
				if (!target.hasDarkened()) {
					allDarkened = false;
				}
			}

			if (allDarkened) {
				latch();
			}
		}

		private void beginTracking(Mat frame) {
			List<ActiveSequence.DetectionRequirement> gcdRequirements = selectGcdRequirements();
			if (gcdRequirements.isEmpty()) {
				return;
			}

			List<TrackedTarget> resolved = new ArrayList<>(gcdRequirements.size());
			for (ActiveSequence.DetectionRequirement requirement : gcdRequirements) {
				Rectangle roi = resolveRoi(requirement, frame);
				if (roi == null) {
					logger.debug("Latch: ROI unavailable for {} (waiting for cache/search)", requirement.abilityKey());
					continue;
				}
				double baseline = templateDetector.measureBrightness(frame, roi);
				if (baseline < MIN_BASELINE_BRIGHTNESS) {
					logger.debug("Latch: baseline too low for {} (brightness={})", requirement.abilityKey(), baseline);
					continue;
				}
				resolved.add(new TrackedTarget(requirement.instanceId(), requirement.abilityKey(), roi, baseline));
			}

			if (resolved.isEmpty()) {
				// Still waiting for at least one usable ROI/baseline; keep awaitingInitialDetection true.
				return;
			}

			trackedTargets = List.copyOf(resolved);
			awaitingInitialDetection = false;
			waitingForDarken = true;
			logger.info("ARMED: brightness tracking {} targets; baselines={}", describeTargets(), describeBaselines());
		}

		private Rectangle resolveRoi(ActiveSequence.DetectionRequirement requirement, Mat frame) {
			return resolveAbilityRoiForRequirement(requirement, frame);
		}

		private void latch() {
			if (!waitingForDarken || sequenceController == null) {
				return;
			}
			long latchTimeMs = System.currentTimeMillis();
			logger.info("LATCH: tracked abilities darkened -> RUNNING");
			boolean completed = false;
			if (activeSequence != null) {
				completed = activeSequence.onLatchStart(latchTimeMs);
			}
			reset();
			sequenceController.onLatchDetected();
			notifications.showSuccess("Sequence started!");
			emitProgressUpdate();
			if (completed) {
				onSequenceCompleted();
			}
		}

		private List<ActiveSequence.DetectionRequirement> selectGcdRequirements() {
			if (activeSequence == null) {
				return List.of();
			}
			int stepIndex = activeSequence.getCurrentStepIndex();
			List<ActiveSequence.DetectionRequirement> requirements = activeSequence.getDetectionRequirementsForStep(stepIndex);
			List<ActiveSequence.DetectionRequirement> selected = new ArrayList<>(MAX_TRACKED_GCD_ABILITIES);
			for (ActiveSequence.DetectionRequirement requirement : requirements) {
				if (selected.size() >= MAX_TRACKED_GCD_ABILITIES) {
					break;
				}
				boolean triggersGcd = false;
				EffectiveAbilityConfig effectiveConfig = requirement.effectiveAbilityConfig();
				if (effectiveConfig != null) {
					triggersGcd = effectiveConfig.isTriggersGcd();
				} else {
					AbilityConfig.AbilityData ability = abilityConfig.getAbility(requirement.abilityKey());
					triggersGcd = ability != null && ability.isTriggersGcd();
				}
				if (!triggersGcd) {
					continue;
				}
				selected.add(requirement);
			}
			return List.copyOf(selected);
		}

		List<ActiveSequence.DetectionRequirement> previewGcdRequirements() {
			return selectGcdRequirements();
		}

		private String describeTargets() {
			List<String> labels = new ArrayList<>(trackedTargets.size());
			for (TrackedTarget target : trackedTargets) {
				labels.add(target.abilityKey);
			}
			return String.join(",", labels);
		}

		private String describeBaselines() {
			List<String> labels = new ArrayList<>(trackedTargets.size());
			for (TrackedTarget target : trackedTargets) {
				labels.add(target.abilityKey + "=" + Math.round(target.baselineBrightness));
			}
			return String.join(",", labels);
		}

		private void reset() {
			awaitingInitialDetection = false;
			waitingForDarken = false;
			trackedTargets = List.of();
		}

		private final class TrackedTarget { // Tracks one HUD tile's brightness drop
			private final String instanceId;
			private final String abilityKey;
			private final Rectangle roi;
			private final double baselineBrightness;
			private int consecutiveDarkFrames = 0;
			private boolean darkened = false;

			private TrackedTarget(String instanceId, String abilityKey, Rectangle roi, double baselineBrightness) {
				this.instanceId = instanceId;
				this.abilityKey = abilityKey;
				this.roi = new Rectangle(roi);
				this.baselineBrightness = baselineBrightness;
			}

			private void updateFromBrightness(double sample) {
				if (sample <= 0 || baselineBrightness <= 0) {
					consecutiveDarkFrames = 0;
					return;
				}
				double drop = (baselineBrightness - sample) / baselineBrightness;
				if (drop >= DARKEN_PERCENT_THRESHOLD) {
					if (consecutiveDarkFrames < DARKEN_FRAMES_REQUIRED) {
						consecutiveDarkFrames++;
					}
					if (consecutiveDarkFrames >= DARKEN_FRAMES_REQUIRED) {
						darkened = true;
					}
				} else {
					consecutiveDarkFrames = 0;
				}
			}

			private boolean hasDarkened() {
				return darkened;
			}
		}
	}

		private final class AlwaysGBargeTracker {
		// In-game GCD radial shading reduces the apparent "drop" when an ability is used mid-rotation.
		// We therefore use a lower threshold for detecting the CURRENT_STEP "used" signal (to start the 8-tick window)
		// while keeping the GBarge "used" detection strict to avoid false positives after eligibility.
			private static final double GBARGE_USED_DARKEN_THRESHOLD = 0.25;
			private static final double GBARGE_USED_SATURATION_THRESHOLD = 0.18;
			private static final int DARKEN_FRAMES_REQUIRED_GBARGE = 2;
			private static final long GBARGE_MIN_DARKEN_HOLD_MS = 800L;
			private static final long GBARGE_MIN_DARKEN_HOLD_HIGH_CONF_MS = 120L;
			private static final long MIN_BASELINE_STABILITY_MS = 750L;
			private static final double GBARGE_MIN_ABSOLUTE_BRIGHTNESS_DROP = 12.0;
			private static final double MIN_BASELINE_BRIGHTNESS = 1.0;
			private static final long TICK_MS = 600L;
			private static final long PRIME_SAMPLE_MS = 100L;
			private static final long PRIME_WINDOW_MS = 6 * 3 * TICK_MS; // 6 GCD @ 3 ticks per GCD

		private TrackedTarget currentTracked;
		private boolean gbargePrimed;
		private long gbargePrimeStartedAtMs;
		private long gbargePrimeNextSampleAtMs;
		private double gbargePrimeLastSampleBrightness;
		private double gbargePrimeLastSampleSaturation;
		private double gbargePrimedBaselineBrightness;
		private double gbargePrimedBaselineSaturation;
		private Rectangle gbargePrimedRoi;
		private int gbargePrimeSamples;
		private String lastResetReason;
		private long lastResetLoggedAtMs;
		private int lastStepIndex = -1;
		private List<String> lastCurrentKeys = List.of();
		private List<String> lastNextKeys = List.of();
		private boolean loggedCurrentTrackFailureThisStep;
		private long lastCurrentDropLogAtMs;

		void onFrame(Mat frame) {
			if (sequenceController == null || frame == null || frame.empty()) {
				return;
			}
			if (sequenceController.getState() != SequenceController.State.RUNNING) {
				resetAllWithReason("controller_not_running", null);
				return;
			}
			if (activeSequence == null || sequenceComplete || activeSequenceId == null) {
				resetAllWithReason("no_active_sequence", null);
				return;
			}

			boolean enabled = Boolean.TRUE.equals(alwaysGBargeEnabledBySequenceId.get(activeSequenceId));
			if (!enabled) {
				resetAllWithReason("always_gbarge_disabled_for_sequence", null);
				return;
			}

			long nowMs = System.currentTimeMillis();
			updateGbargeBaselinePrime(frame, nowMs);

			int stepIndex = activeSequence.getCurrentStepIndex();
			if (stepIndex != lastStepIndex) {
				lastStepIndex = stepIndex;
				loggedCurrentTrackFailureThisStep = false;
			}

			List<String> currentKeys = activeSequence.getAbilityKeysForStep(stepIndex);
			List<String> nextKeys = activeSequence.getAbilityKeysForStep(stepIndex + 1);
			lastCurrentKeys = currentKeys;
			lastNextKeys = nextKeys;

			boolean gbargeIsCurrent = currentKeys.contains("gbarge");
			if (!gbargeIsCurrent) {
				resetTrackingOnlyWithReason("gbarge_not_current_step", stepIndex, currentKeys, nextKeys);
				return;
			}

			if (currentTracked != null && currentTracked.stepIndex != stepIndex) {
				currentTracked = null;
			}

				if (currentTracked == null) {
					ActiveSequence.DetectionRequirement gbargeReq = selectByKey(
							activeSequence.getDetectionRequirementsForStep(stepIndex),
							"gbarge"
					);
					currentTracked = buildTracked(
							frame,
							nowMs,
							gbargeReq,
							DARKEN_FRAMES_REQUIRED_GBARGE,
							GBARGE_USED_DARKEN_THRESHOLD,
							"currentStepGbarge"
					);
				if (currentTracked == null && !loggedCurrentTrackFailureThisStep) {
					logger.info("AlwaysGBarge: cannot initialize CURRENT_STEP gbarge darken tracking (stepIndex={})", stepIndex);
					loggedCurrentTrackFailureThisStep = true;
				}
			}

			if (currentTracked == null) {
				return;
			}

			double brightness = templateDetector.measureBrightness(frame, currentTracked.roi);
			double saturation = templateDetector.measureSaturation(frame, currentTracked.roi);
			currentTracked.updateFromSamples(brightness, saturation, nowMs);
			if (currentTracked.hasDarkened()) {
				logger.info("AlwaysGBarge: ability used detected (CURRENT_STEP): {}", currentTracked.abilityKey);
				activeSequence.onAbilityUsed(currentTracked.abilityKey, currentTracked.instanceId, StepPosition.CURRENT_STEP, nowMs);
				currentTracked = null;
			}
		}

		private void resetWithReason(String reason) {
			resetAllWithReason(reason, null);
		}

		private void resetAllWithReason(String reason, String detail) {
			boolean wasActive = currentTracked != null || gbargePrimed;
			resetAll();
			if (!wasActive) {
				lastResetReason = reason;
				return;
			}
			long nowMs = System.currentTimeMillis();
			boolean reasonChanged = lastResetReason == null || !lastResetReason.equals(reason);
			boolean shouldLog = reasonChanged || (nowMs - lastResetLoggedAtMs) > 1000L;
			if (shouldLog) {
				if (detail != null && !detail.isBlank()) {
					logger.info("AlwaysGBarge: tracker reset (reason={}, detail={})", reason, detail);
				} else {
					logger.info("AlwaysGBarge: tracker reset (reason={})", reason);
				}
				lastResetLoggedAtMs = nowMs;
				lastResetReason = reason;
			}
		}

		private void resetTrackingOnlyWithReason(String reason, int stepIndex, List<String> currentKeys, List<String> nextKeys) {
			boolean wasActive = currentTracked != null;
			resetTrackingOnly();
			if (!wasActive) {
				lastResetReason = reason;
				return;
			}

			long nowMs = System.currentTimeMillis();
			boolean reasonChanged = lastResetReason == null || !lastResetReason.equals(reason);
			boolean shouldLog = reasonChanged || (nowMs - lastResetLoggedAtMs) > 1000L;
			if (shouldLog) {
				logger.info("AlwaysGBarge: tracker idle (reason={}, stepIndex={}, currentKeys={}, nextKeys={})",
						reason, stepIndex, currentKeys, nextKeys);
				lastResetLoggedAtMs = nowMs;
				lastResetReason = reason;
			}
		}

		private void updateGbargeBaselinePrime(Mat frame, long nowMs) {
			if (gbargePrimed) {
				return;
			}

			if (gbargePrimeStartedAtMs <= 0L) {
				gbargePrimeStartedAtMs = nowMs;
				gbargePrimeNextSampleAtMs = nowMs;
				gbargePrimeLastSampleBrightness = -1;
				gbargePrimeLastSampleSaturation = -1;
				gbargePrimedBaselineBrightness = -1;
				gbargePrimedBaselineSaturation = -1;
				gbargePrimedRoi = null;
				gbargePrimeSamples = 0;
				logger.info("AlwaysGBarge: priming gbarge baselines for {}ms (sampling every {}ms)", PRIME_WINDOW_MS, PRIME_SAMPLE_MS);
			}

			if (nowMs - gbargePrimeStartedAtMs > PRIME_WINDOW_MS) {
				gbargePrimed = true;
				if (gbargePrimedBaselineBrightness > 0) {
					if (gbargePrimedBaselineSaturation > 0) {
						logger.info("AlwaysGBarge: priming complete; lastSampleBrightness={}; maxBrightness={}; lastSampleSaturation={}; maxSaturation={}; samples={}",
								gbargePrimeLastSampleBrightness,
								gbargePrimedBaselineBrightness,
								gbargePrimeLastSampleSaturation,
								gbargePrimedBaselineSaturation,
								gbargePrimeSamples);
					} else {
						logger.info("AlwaysGBarge: priming complete; lastSampleBrightness={}; maxBrightness={}; samples={}",
								gbargePrimeLastSampleBrightness,
								gbargePrimedBaselineBrightness,
								gbargePrimeSamples);
					}
				} else {
					logger.warn("AlwaysGBarge: priming complete but no usable gbarge baseline sample was captured (samples={})", gbargePrimeSamples);
				}
				return;
			}

			if (nowMs < gbargePrimeNextSampleAtMs) {
				return;
			}
			gbargePrimeNextSampleAtMs = nowMs + PRIME_SAMPLE_MS;

			if (gbargePrimedRoi == null) {
				Rectangle roi = templateDetector.resolveAbilityRoi(frame, "gbarge");
				if (roi != null) {
					gbargePrimedRoi = new Rectangle(roi);
				}
			}

			if (gbargePrimedRoi == null) {
				return;
			}

			double brightness = templateDetector.measureBrightness(frame, gbargePrimedRoi);
			gbargePrimeLastSampleBrightness = brightness;
			double saturation = templateDetector.measureSaturation(frame, gbargePrimedRoi);
			gbargePrimeLastSampleSaturation = saturation;
			gbargePrimeSamples++;
			if (brightness > gbargePrimedBaselineBrightness) {
				double previousMax = gbargePrimedBaselineBrightness;
				gbargePrimedBaselineBrightness = brightness;
				logger.info("AlwaysGBarge: priming sample; brightness={}; previousMax={}; newMax={}",
						brightness, previousMax, gbargePrimedBaselineBrightness);
			}
			if (saturation > gbargePrimedBaselineSaturation) {
				gbargePrimedBaselineSaturation = saturation;
			}
		}

		private Rectangle resolveRoi(ActiveSequence.DetectionRequirement requirement, Mat frame) {
			return resolveAbilityRoiForRequirement(requirement, frame);
		}

			private TrackedTarget buildTracked(Mat frame,
			                                  long nowMs,
			                                  ActiveSequence.DetectionRequirement requirement,
			                                  int framesRequired,
			                                  double darkenPercentThreshold,
			                                  String role) {
			if (frame == null || requirement == null) {
				if (requirement == null) {
					logger.info("AlwaysGBarge: cannot track {}: no detection requirement", role);
				}
				return null;
			}
			Rectangle roi = resolveRoi(requirement, frame);
			if (roi == null) {
				logger.info("AlwaysGBarge: cannot track {} {}: ROI unresolved (instanceId={})", role, requirement.abilityKey(), requirement.instanceId());
				return null;
			}
			double rawBaseline = templateDetector.measureBrightness(frame, roi);
			double baseline = rawBaseline;
			double rawSaturation = templateDetector.measureSaturation(frame, roi);
			double baselineSaturation = rawSaturation;
			if (baseline < MIN_BASELINE_BRIGHTNESS) {
				logger.info("AlwaysGBarge: cannot track {} {}: baseline too low (rawBaseline={}, primedMaxBaseline={}, baselineUsed={}, minBaseline={})",
						role, requirement.abilityKey(), rawBaseline, gbargePrimedBaselineBrightness, baseline, MIN_BASELINE_BRIGHTNESS);
				return null;
			}
			if (baselineSaturation <= 0) {
				// Saturation is an optional secondary signal; allow tracking to proceed using brightness only.
				baselineSaturation = 0;
			}
				logger.info("AlwaysGBarge: tracking {} {} (instanceId={}, baseline={}, framesRequired={})",
						role, requirement.abilityKey(), requirement.instanceId(), baseline, framesRequired);
				logger.info("AlwaysGBarge: tracking {} {} (darkenThreshold={})", role, requirement.abilityKey(), darkenPercentThreshold);
				return new TrackedTarget(lastStepIndex, requirement.instanceId(), requirement.abilityKey(), roi, baseline, baselineSaturation, framesRequired, darkenPercentThreshold, nowMs);
			}

		private ActiveSequence.DetectionRequirement selectByKey(List<ActiveSequence.DetectionRequirement> requirements, String abilityKey) {
			if (requirements == null || requirements.isEmpty() || abilityKey == null) {
				return null;
			}
			for (ActiveSequence.DetectionRequirement requirement : requirements) {
				if (requirement != null && abilityKey.equals(requirement.abilityKey())) {
					return requirement;
				}
			}
			return null;
		}

		void reset() {
			resetAll();
		}

		private void resetTrackingOnly() {
			currentTracked = null;
			lastCurrentDropLogAtMs = 0L;
			lastCurrentKeys = List.of();
			lastNextKeys = List.of();
		}

		private void resetAll() {
			currentTracked = null;
			gbargePrimed = false;
			gbargePrimeStartedAtMs = 0L;
			gbargePrimeNextSampleAtMs = 0L;
			gbargePrimeLastSampleBrightness = -1;
			gbargePrimedBaselineBrightness = -1;
			gbargePrimeLastSampleSaturation = -1;
			gbargePrimedBaselineSaturation = -1;
			gbargePrimedRoi = null;
			gbargePrimeSamples = 0;
			lastCurrentDropLogAtMs = 0L;
			lastCurrentKeys = List.of();
			lastNextKeys = List.of();
		}

			private final class TrackedTarget {
			private final int stepIndex;
			private final String instanceId;
			private final String abilityKey;
			private final Rectangle roi;
			private double baselineBrightness;
			private double baselineSaturation;
			private final int framesRequired;
			private final double darkenPercentThreshold;
				private int consecutiveDarkFrames = 0;
				private boolean darkened = false;
				private long darkCandidateStartedAtMs = 0L;
				private long lastDarkCandidateLogAtMs = 0L;
				private boolean baselineFrozen = false;
				private long lastBaselineRaiseLogAtMs = 0L;
				private long lastBaselineRaisedAtMs = 0L;
				private boolean darkCandidateHighConfidence = false;

				private TrackedTarget(int stepIndex,
				                      String instanceId,
				                      String abilityKey,
				                      Rectangle roi,
				                      double baselineBrightness,
				                      double baselineSaturation,
				                      int framesRequired,
				                      double darkenPercentThreshold,
				                      long nowMs) {
					this.stepIndex = stepIndex;
					this.instanceId = instanceId;
					this.abilityKey = abilityKey;
					this.roi = new Rectangle(roi);
					this.baselineBrightness = baselineBrightness;
					this.baselineSaturation = baselineSaturation;
					this.framesRequired = Math.max(1, framesRequired);
					this.darkenPercentThreshold = Math.max(0.0, darkenPercentThreshold);
					this.lastBaselineRaisedAtMs = 0L;
				}

				private void updateFromSamples(double brightnessSample, double saturationSample, long nowMs) {
					if (brightnessSample <= 0 || baselineBrightness <= 0) {
						consecutiveDarkFrames = 0;
						darkCandidateStartedAtMs = 0L;
						darkCandidateHighConfidence = false;
						return;
					}
					maybeRaiseBaseline(brightnessSample, saturationSample, nowMs);

					double saturationDrop = dropFromSaturationBaseline(saturationSample);
					double brightnessDrop = dropFromBrightnessBaseline(brightnessSample);
					double absoluteDrop = baselineBrightness - brightnessSample;
					boolean darkBySaturation = baselineSaturation > 0
							&& saturationSample > 0
							&& saturationDrop >= GBARGE_USED_SATURATION_THRESHOLD;
					// GBarge "used" detection is based on darkness: compare current brightness to the max/old baseline.
					// Low-confidence (brightness-only) candidates are guarded by a minimum absolute drop and baseline stability.
					boolean darkByBrightness = brightnessDrop >= darkenPercentThreshold
							&& (baselineBrightness < 40.0 || absoluteDrop >= GBARGE_MIN_ABSOLUTE_BRIGHTNESS_DROP)
							&& (lastBaselineRaisedAtMs == 0L || (nowMs - lastBaselineRaisedAtMs) >= MIN_BASELINE_STABILITY_MS);
					boolean isDarkenedSample = darkBySaturation || darkByBrightness;
					if (isDarkenedSample) {
						if (darkCandidateStartedAtMs == 0L) {
							darkCandidateStartedAtMs = nowMs;
						}
						if (darkBySaturation) {
							darkCandidateHighConfidence = true;
						}
						if (consecutiveDarkFrames < framesRequired) {
							consecutiveDarkFrames++;
						}
						long darkHoldMs = nowMs - darkCandidateStartedAtMs;
						long requiredHoldMs = darkCandidateHighConfidence
								? GBARGE_MIN_DARKEN_HOLD_HIGH_CONF_MS
								: GBARGE_MIN_DARKEN_HOLD_MS;
						boolean holdSatisfied = darkHoldMs >= requiredHoldMs;
						if (consecutiveDarkFrames >= framesRequired && holdSatisfied) {
							darkened = true;
						} else if (nowMs - lastDarkCandidateLogAtMs >= 750L) {
							logger.info("AlwaysGBarge: gbarge darken candidate (baseline={}, sample={}, drop={}, satDrop={}, threshold={}, darkFrames={}/{}, holdMs={}/{}, highConfidence={})",
									baselineBrightness,
									brightnessSample,
									brightnessDrop,
									saturationDrop,
									darkenPercentThreshold,
									consecutiveDarkFrames,
									framesRequired,
									darkHoldMs,
									requiredHoldMs,
									darkCandidateHighConfidence);
							lastDarkCandidateLogAtMs = nowMs;
						}
					} else {
						consecutiveDarkFrames = 0;
						darkened = false;
						darkCandidateStartedAtMs = 0L;
						darkCandidateHighConfidence = false;
					}
				}

			private boolean hasDarkened() {
				return darkened;
			}

			private void freezeBaseline() {
				baselineFrozen = true;
			}

				private void maybeRaiseBaseline(double brightnessSample, double saturationSample, long nowMs) {
					if (baselineFrozen) {
						return;
					}
					if (consecutiveDarkFrames > 0) {
					// Don't chase the baseline while we're already observing a darkening streak.
					return;
				}
					if (brightnessSample > baselineBrightness) {
						double previous = baselineBrightness;
						baselineBrightness = brightnessSample;
						lastBaselineRaisedAtMs = nowMs;
						resetDetectionState();
						if (nowMs - lastBaselineRaiseLogAtMs >= 500L && (baselineBrightness - previous) >= 5.0) {
							logger.info("AlwaysGBarge: baseline raised for {} (from {} to {}, sample={})", abilityKey, previous, baselineBrightness, brightnessSample);
							lastBaselineRaiseLogAtMs = nowMs;
						}
					}
					if (saturationSample > 0 && saturationSample > baselineSaturation) {
						baselineSaturation = saturationSample;
					}
				}

			private double dropFromBrightnessBaseline(double brightnessSample) {
				if (brightnessSample <= 0 || baselineBrightness <= 0) {
					return 0.0;
				}
				return (baselineBrightness - brightnessSample) / baselineBrightness;
			}

			private double dropFromSaturationBaseline(double saturationSample) {
				if (saturationSample <= 0 || baselineSaturation <= 0) {
					return 0.0;
				}
				return (baselineSaturation - saturationSample) / baselineSaturation;
			}

				private void resetDetectionState() {
					consecutiveDarkFrames = 0;
					darkened = false;
					darkCandidateStartedAtMs = 0L;
					darkCandidateHighConfidence = false;
				}
			}
		}

}
