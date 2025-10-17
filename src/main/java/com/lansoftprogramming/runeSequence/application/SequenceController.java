package com.lansoftprogramming.runeSequence.application;


import com.lansoftprogramming.runeSequence.infrastructure.hotkey.HotkeyManager;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.List;

public class SequenceController implements HotkeyManager.HotkeyListener {
	private static final Logger logger = LoggerFactory.getLogger(SequenceController.class);

	public enum State {
		READY,    // Showing current/next, waiting for start
		RUNNING,  // Active sequence with timers
		PAUSED    // Currently unused but ready for future
	}

	private final SequenceManager sequenceManager;
	private volatile State currentState = State.READY;
	private final List<StateChangeListener> listeners = new ArrayList<>();

	public SequenceController(SequenceManager sequenceManager) {
		this.sequenceManager = sequenceManager;
	}

	@Override
	public void onStartSequence() {
		synchronized (this) {
			if (currentState == State.READY) {
				setState(State.RUNNING);
				logger.info("Sequence started - GCD timers active");
			}
		}
	}

	@Override
	public void onRestartSequence() {
		synchronized (this) {
			sequenceManager.resetActiveSequence();
			setState(State.READY);
			logger.info("Sequence restarted - back to ready state");
		}
	}

	public synchronized State getState() {
		return currentState;
	}

	public synchronized boolean isRunning() {
		return currentState == State.RUNNING;
	}

	private synchronized void setState(State newState) {
		if (this.currentState != newState) {
			State oldState = this.currentState;
			this.currentState = newState;
			notifyStateChange(oldState, newState);
		}
	}

	public void addStateChangeListener(StateChangeListener listener) {
		listeners.add(listener);
	}
	public void removeStateChangeListener(StateChangeListener listener) {
		listeners.remove(listener);
	}

	private void notifyStateChange(State oldState, State newState) {
		for (StateChangeListener listener : listeners) {
			try {
				listener.onStateChanged(oldState, newState);
			} catch (Exception e) {
				logger.error("Error notifying state change listener", e);
			}
		}
	}

	public interface StateChangeListener {
		void onStateChanged(State oldState, State newState);
	}
}