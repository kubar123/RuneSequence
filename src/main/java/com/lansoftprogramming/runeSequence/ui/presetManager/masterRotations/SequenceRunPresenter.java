package com.lansoftprogramming.runeSequence.ui.presetManager.masterRotations;

import com.lansoftprogramming.runeSequence.application.SequenceController;
import com.lansoftprogramming.runeSequence.application.SequenceManager;
import com.lansoftprogramming.runeSequence.application.SequenceRunService;
import com.lansoftprogramming.runeSequence.ui.notification.NotificationService;

import javax.swing.*;

class SequenceRunPresenter {
	enum StartAccent {
		NEUTRAL,
		STARTED,
		ARMED
	}

	private final SequenceRunService sequenceRunService;
	private NotificationService notificationService;
	private SequenceRunView view;

	private SequenceController.State currentState = SequenceController.State.READY;
	private SequenceManager.SequenceProgress currentProgress;
	private boolean detectionRunning;

	SequenceRunPresenter(SequenceRunService sequenceRunService, NotificationService notificationService) {
		this.sequenceRunService = sequenceRunService;
		this.notificationService = notificationService;
		if (sequenceRunService != null) {
			refreshFromService();
		}
	}

	void attachView(SequenceRunView view) {
		this.view = view;
		applyView();
	}

	void setNotificationService(NotificationService notificationService) {
		this.notificationService = notificationService;
	}

	void refreshFromService() {
		if (sequenceRunService != null) {
			currentState = sequenceRunService.getCurrentState();
			currentProgress = sequenceRunService.getProgressSnapshot();
			detectionRunning = sequenceRunService.isDetectionRunning();
		} else {
			currentState = SequenceController.State.READY;
			currentProgress = null;
			detectionRunning = false;
		}
		applyView();
	}

	void onStartRequested() {
		if (sequenceRunService == null) {
			showError("Start action unavailable.");
			return;
		}
		SequenceManager.SequenceProgress progress = sequenceRunService.getProgressSnapshot();
		if (progress != null && progress.isSequenceComplete()) {
			sequenceRunService.prepareReadyState();
		}
		sequenceRunService.start();
		refreshFromService();
		showInfo("Start requested.");
	}

	void onPauseRequested() {
		if (sequenceRunService == null) {
			showError("Pause action unavailable.");
			return;
		}
		sequenceRunService.pause();
		refreshFromService();
		showInfo("Detection paused.");
	}

	void onRestartRequested() {
		if (sequenceRunService == null) {
			showError("Restart action unavailable.");
			return;
		}
		sequenceRunService.restart();
		refreshFromService();
		showSuccess("Restart requested.");
	}

	void onStateChanged(SequenceController.State oldState, SequenceController.State newState) {
		currentState = newState != null ? newState : SequenceController.State.READY;
		detectionRunning = sequenceRunService != null && sequenceRunService.isDetectionRunning();
		applyView();
	}

	void onProgressChanged(SequenceManager.SequenceProgress progress) {
		currentProgress = progress;
		detectionRunning = sequenceRunService != null && sequenceRunService.isDetectionRunning();
		applyView();
	}

	private void applyView() {
		if (view == null) {
			return;
		}
		SequenceController.State state = currentState != null ? currentState : SequenceController.State.READY;
		SequenceManager.SequenceProgress progress = currentProgress;
		boolean serviceAvailable = sequenceRunService != null;
		StartAccent startAccent = resolveStartAccent(state);
		boolean pauseHighlighted = state == SequenceController.State.PAUSED;
		String startLabel = resolveStartLabel(state);
		boolean startEnabled = serviceAvailable && (state == SequenceController.State.READY || state == SequenceController.State.PAUSED);
		boolean pauseEnabled = serviceAvailable;
		boolean restartEnabled = serviceAvailable;
		String statusText = serviceAvailable
				? buildStatusText(state, progress, detectionRunning)
				: "Status: Controls unavailable";

		SwingUtilities.invokeLater(() -> {
			view.setStartButtonState(startLabel, startEnabled, startAccent);
			view.setPauseButtonState(pauseEnabled, pauseHighlighted);
			view.setRestartButtonEnabled(restartEnabled);
			view.setStatusText(statusText);
		});
	}

	private StartAccent resolveStartAccent(SequenceController.State state) {
		if (state == null) {
			return StartAccent.NEUTRAL;
		}
		return switch (state) {
			case RUNNING -> StartAccent.STARTED;
			case ARMED -> StartAccent.ARMED;
			default -> StartAccent.NEUTRAL;
		};
	}

	private String resolveStartLabel(SequenceController.State state) {
		return switch (state) {
			case RUNNING -> "Running";
			case ARMED -> "Armed";
			case PAUSED -> "Start";
			default -> "Arm";
		};
	}

	private String buildStatusText(SequenceController.State state,
	                               SequenceManager.SequenceProgress progress,
	                               boolean detectionRunning) {
		if (progress == null || !progress.hasActiveSequence()) {
			return "Status: No rotation selected";
		}

		String rotationSuffix = "";
		if (progress.getSequenceId() != null && !progress.getSequenceId().isBlank()) {
			rotationSuffix = " [" + progress.getSequenceId() + "]";
		}

		if (progress.isSequenceComplete()) {
			return "Status: Rotation complete" + rotationSuffix + ". Restart to run again.";
		}

		String abilities = describeAbilities(progress);
		int totalSteps = progress.getTotalSteps();
		int displayStep = progress.getCurrentStepIndex() >= 0 ? progress.getCurrentStepIndex() + 1 : 0;
		String stepInfo = totalSteps > 0 && displayStep > 0
				? "step " + displayStep + "/" + totalSteps
				: "current step";

		return switch (state) {
			case PAUSED -> "Status: Paused" + rotationSuffix + " - detection stopped.";
			case ARMED -> "Status: Armed" + rotationSuffix + " - waiting for latch (" + abilities + ").";
			case RUNNING -> "Status: Running" + rotationSuffix + " " + stepInfo + " (" + abilities + ").";
			case READY -> {
				if (!detectionRunning) {
					yield "Status: Ready" + rotationSuffix + " - detection halted.";
				}
				yield "Status: Ready" + rotationSuffix + " - standing by (" + abilities + ").";
			}
			default -> "Status: " + state;
		};
	}

	private String describeAbilities(SequenceManager.SequenceProgress progress) {
		if (progress == null || progress.getCurrentStepAbilities() == null
				|| progress.getCurrentStepAbilities().isEmpty()) {
			return "looking for abilities";
		}
		return String.join(" / ", progress.getCurrentStepAbilities());
	}

	private void showInfo(String message) {
		if (notificationService != null) {
			notificationService.showInfo(message);
		}
	}

	private void showError(String message) {
		if (notificationService != null) {
			notificationService.showError(message);
		}
	}

	private void showSuccess(String message) {
		if (notificationService != null) {
			notificationService.showSuccess(message);
		}
	}

	interface SequenceRunView {
		void setStartButtonState(String label, boolean enabled, StartAccent accent);

		void setPauseButtonState(boolean enabled, boolean highlighted);

		void setRestartButtonEnabled(boolean enabled);

		void setStatusText(String text);
	}
}
