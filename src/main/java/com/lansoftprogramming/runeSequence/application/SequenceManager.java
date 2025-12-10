package com.lansoftprogramming.runeSequence.application;

import com.lansoftprogramming.runeSequence.core.detection.DetectionResult;
import com.lansoftprogramming.runeSequence.core.detection.TemplateDetector;
import com.lansoftprogramming.runeSequence.core.sequence.model.SequenceDefinition;
import com.lansoftprogramming.runeSequence.core.sequence.runtime.ActiveSequence;
import com.lansoftprogramming.runeSequence.core.sequence.runtime.SequenceTooltip;
import com.lansoftprogramming.runeSequence.core.sequence.runtime.TooltipSchedule;
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
	private final List<Consumer<SequenceProgress>> progressListeners = new ArrayList<>();
	private boolean sequenceComplete = false;
	private String activeSequenceId;

	public SequenceManager(Map<String, SequenceDefinition> namedSequences,
	                       Map<String, TooltipSchedule> tooltipSchedules,
	                       AbilityConfig abilityConfig,
	                       NotificationService notifications,
	                       TemplateDetector templateDetector) {
		this.abilityConfig = Objects.requireNonNull(abilityConfig);
		this.namedSequences = Objects.requireNonNull(namedSequences);
		this.tooltipSchedules = tooltipSchedules != null
				? new HashMap<>(tooltipSchedules)
				: new HashMap<>();
		this.notifications = Objects.requireNonNull(notifications);
		this.templateDetector = Objects.requireNonNull(templateDetector);
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
		SequenceDefinition def = namedSequences.get(name);
		if (def == null) {
			logger.warn("Sequence not found: {}", name);
			return false;
		}

		if (activeSequence != null && sequenceController != null) {
			sequenceController.removeStateChangeListener(activeSequence);
		}

		this.activeSequence = new ActiveSequence(def, abilityConfig);
		this.sequenceComplete = false;
		this.activeSequenceId = name;

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
		if (id == null || id.isBlank()) {
			return;
		}

		if (definition == null) {
			namedSequences.remove(id);
			tooltipSchedules.remove(id);
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

	public synchronized void clearActiveSequence() {
		if (activeSequence != null && sequenceController != null) {
			sequenceController.removeStateChangeListener(activeSequence);
		}
		activeSequence = null;
		activeSequenceId = null;
		sequenceComplete = false;
		gcdLatchTracker.reset();
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
		if (activeSequenceId == null) {
			return List.of();
		}
		TooltipSchedule schedule = tooltipSchedules.get(activeSequenceId);
		if (schedule == null) {
			return List.of();
		}
		int stepIndex = activeSequence.getCurrentStepIndex();
		return schedule.getTooltipsForStep(stepIndex);
	}

	public synchronized List<String> getActiveSequenceAbilityKeys() {
		if (activeSequence == null || sequenceComplete) {
			return List.of();
		}
		return activeSequence.getAllAbilityKeys();
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
	}

	private void onSequenceCompleted() {
		if (sequenceComplete) {
			return;
		}
		sequenceComplete = true;
		gcdLatchTracker.reset();
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
				Rectangle roi = resolveRoi(requirement.abilityKey(), frame);
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

		private Rectangle resolveRoi(String abilityKey, Mat frame) {
			return templateDetector.resolveAbilityRoi(frame, abilityKey);
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
			List<ActiveSequence.DetectionRequirement> requirements = activeSequence.getDetectionRequirements();
			List<ActiveSequence.DetectionRequirement> selected = new ArrayList<>(MAX_TRACKED_GCD_ABILITIES);
			for (ActiveSequence.DetectionRequirement requirement : requirements) {
				if (selected.size() >= MAX_TRACKED_GCD_ABILITIES) {
					break;
				}
				AbilityConfig.AbilityData ability = abilityConfig.getAbility(requirement.abilityKey());
				if (ability == null || !ability.isTriggersGcd()) {
					continue;
				}
				selected.add(requirement);
			}
			return List.copyOf(selected);
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

}