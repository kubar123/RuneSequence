package com.lansoftprogramming.runeSequence.application;

import com.lansoftprogramming.runeSequence.core.detection.DetectionResult;
import com.lansoftprogramming.runeSequence.core.sequence.model.SequenceDefinition;
import com.lansoftprogramming.runeSequence.core.sequence.runtime.ActiveSequence;
import com.lansoftprogramming.runeSequence.infrastructure.config.AbilityConfig;
import com.lansoftprogramming.runeSequence.ui.notification.NotificationService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.*;

public class SequenceManager implements SequenceController.StateChangeListener {

	private static final Logger logger = LoggerFactory.getLogger(SequenceManager.class);

	private final AbilityConfig abilityConfig;
	private final Map<String, SequenceDefinition> namedSequences;
	private final NotificationService notifications;
	private ActiveSequence activeSequence;
	private SequenceController sequenceController;
	private final GcdLatchTracker gcdLatchTracker = new GcdLatchTracker();
	private boolean sequenceComplete = false;

	public SequenceManager(Map<String, SequenceDefinition> namedSequences, AbilityConfig abilityConfig,
	                      NotificationService notifications) {
		this.abilityConfig = Objects.requireNonNull(abilityConfig);
		this.namedSequences = Objects.requireNonNull(namedSequences);
		this.notifications = Objects.requireNonNull(notifications);
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

		if (sequenceController != null) {
			sequenceController.addStateChangeListener(activeSequence);
			activeSequence.stepTimer.pause();
		}

		return true;
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

	public synchronized void processDetection(List<DetectionResult> results) {

		if (sequenceComplete || activeSequence == null) {
			return;
		}

		// Keep latch state synced to latest detections before step timers react
		gcdLatchTracker.onFrame(results);

		activeSequence.processDetections(results);

		if (activeSequence.isComplete()) {
			onSequenceCompleted();
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
		if (activeSequence != null) {
			activeSequence.reset();
		}
		sequenceComplete = false;
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
	}

	private final class GcdLatchTracker {
		private static final int CONSECUTIVE_MISS_THRESHOLD = 2;

		private boolean awaitingInitialDetection = false; // Force us to lock onto what is currently visible
		private boolean waitingForVanish = false; // Prevent latch until tracked abilities truly disappear
		private boolean waitingForReturn = false; // Fire only when the same abilities come back
		private List<TrackedTarget> trackedTargets = List.of();

		void onStateChanged(SequenceController.State newState) {
			if (newState == SequenceController.State.ARMED) {
				// Fresh arming ignores stale sightings so we only react to the next recycle
				logger.info("ARMED: awaiting detection snapshot for recycle latch");
				notifications.showInfo("Armed. Waiting for ability use to start the sequence.");
				awaitingInitialDetection = true;
				waitingForVanish = false;
				waitingForReturn = false;
				trackedTargets = List.of();
			} else {
				reset();
			}
		}

		void onFrame(List<DetectionResult> results) {
			if (sequenceController == null) {
				return;
			}

			// Build a lookup per frame so recycle checks stay O(1)
			Map<String, DetectionResult> indexed = indexByInstance(results);

			if (awaitingInitialDetection) {
				// Need one solid frame before we can watch for the cooldown cycle
				beginTracking(indexed);
			}

			if (!waitingForVanish && !waitingForReturn) {
				// Nothing to do until we have a full vanish/return journey
				return;
			}

			if (trackedTargets.isEmpty()) {
				return;
			}

			// Guard against false positives by requiring every tracked ability to vanish then return
			boolean allObservedAbsence = true;
			boolean allCurrentlyDetected = true;

			for (TrackedTarget target : trackedTargets) {
				target.update(indexed.get(target.instanceId));
				if (!target.hasObservedAbsence()) {
					allObservedAbsence = false;
				}
				if (!target.isCurrentlyDetected()) {
					allCurrentlyDetected = false;
				}
			}

			if (waitingForVanish && allObservedAbsence) {
				waitingForVanish = false;
				waitingForReturn = true;
				logger.info("ARMED: tracked abilities vanished - waiting for return");
			}

			if (waitingForReturn && allCurrentlyDetected) {
				latch();
			}
		}

		private void beginTracking(Map<String, DetectionResult> indexedResults) {
			List<TrackedTarget> selected = selectTargets(indexedResults);
			if (selected.isEmpty()) {
				return;
			}
			// Freeze the exact abilities we expect to recycle so stray detections do not latch
			trackedTargets = selected;
			awaitingInitialDetection = false;
			waitingForVanish = true;
			waitingForReturn = false;
			for (TrackedTarget target : trackedTargets) {
				target.initialize(indexedResults.get(target.instanceId));
			}
			logger.info("ARMED: tracking ability recycle targets={}", describeTargets());
		}

		private void latch() {
			if (!waitingForReturn || sequenceController == null) {
				return;
			}
			// As soon as we see the recycle, timers should spin up with zero added delay
			logger.info("LATCH: tracked abilities detected again -> RUNNING");
			reset();
			sequenceController.onLatchDetected();
			notifications.showSuccess("Sequence started!");
		}

		private List<TrackedTarget> selectTargets(Map<String, DetectionResult> indexedResults) {
			if (activeSequence == null) {
				return List.of();
			}
			// Focus on the first couple of GCD-triggering abilities so visual noise does not matter
			List<ActiveSequence.DetectionRequirement> requirements = activeSequence.getDetectionRequirements();
			List<TrackedTarget> selected = new ArrayList<>(2);
			for (ActiveSequence.DetectionRequirement requirement : requirements) {
				if (selected.size() >= 2) {
					break;
				}
				AbilityConfig.AbilityData ability = abilityConfig.getAbility(requirement.abilityKey());
				if (ability == null || !ability.isTriggersGcd()) {
					continue;
				}
				DetectionResult detection = indexedResults.get(requirement.instanceId());
				if (detection == null || !detection.found) {
					continue;
				}
				selected.add(new TrackedTarget(requirement.instanceId(), requirement.abilityKey()));
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

		private Map<String, DetectionResult> indexByInstance(List<DetectionResult> results) {
			Map<String, DetectionResult> indexed = new HashMap<>();
			for (DetectionResult result : results) {
				indexed.put(result.templateName, result);
			}
			return indexed;
		}

		private void reset() {
			awaitingInitialDetection = false;
			waitingForVanish = false;
			waitingForReturn = false;
			trackedTargets = List.of();
		}

		private final class TrackedTarget { // Tracks one HUD tile to prove that specific spell recycled
			private final String instanceId;
			private final String abilityKey;
			private int consecutiveMisses = 0;
			private boolean currentlyDetected = false;
			private boolean observedAbsence = false;

			private TrackedTarget(String instanceId, String abilityKey) {
				this.instanceId = instanceId;
				this.abilityKey = abilityKey;
			}

			private void initialize(DetectionResult detection) {
				// Baseline current visibility so we only count disappearances that happen after arming
				boolean detected = detection != null && detection.found;
				currentlyDetected = detected;
				consecutiveMisses = detected ? 0 : CONSECUTIVE_MISS_THRESHOLD;
				observedAbsence = !detected;
			}

			private void update(DetectionResult detection) {
				// Use short miss streaks so brief occlusions do not trip a false vanish
				boolean detected = detection != null && detection.found;
				if (detected) {
					currentlyDetected = true;
					consecutiveMisses = 0;
					return;
				}

				currentlyDetected = false;
				if (consecutiveMisses < CONSECUTIVE_MISS_THRESHOLD) {
					consecutiveMisses++;
				}
				if (consecutiveMisses >= CONSECUTIVE_MISS_THRESHOLD) {
					observedAbsence = true;
				}
			}

			private boolean hasObservedAbsence() {
				return observedAbsence;
			}

			private boolean isCurrentlyDetected() {
				return currentlyDetected;
			}
		}
	}

}
