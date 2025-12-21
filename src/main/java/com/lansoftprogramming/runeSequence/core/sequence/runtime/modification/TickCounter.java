package com.lansoftprogramming.runeSequence.core.sequence.runtime.modification;

/**
 * A tick-based countdown where each tick represents a fixed number of milliseconds.
 */
public class TickCounter {
	private final long tickMs;
	private boolean running;
	private int totalTicks;
	private long startedAtMs;
	private long pausedAtMs;
	private long totalPausedMs;
	private boolean paused;

	public TickCounter(long tickMs) {
		this.tickMs = Math.max(1L, tickMs);
	}

	public void start(int ticks, long nowMs) {
		running = true;
		totalTicks = Math.max(0, ticks);
		startedAtMs = nowMs;
		pausedAtMs = 0L;
		totalPausedMs = 0L;
		paused = false;
	}

	public void stop() {
		running = false;
	}

	public boolean isRunning() {
		return running;
	}

	public boolean isExpired(long nowMs) {
		if (!running) {
			return true;
		}
		return remainingTicks(nowMs) <= 0;
	}

	public int remainingTicks(long nowMs) {
		if (!running) {
			return 0;
		}
		long elapsedMs = effectiveElapsedMs(nowMs);
		long ticksElapsed = elapsedMs / tickMs;
		long remaining = (long) totalTicks - ticksElapsed;
		return (int) Math.max(0L, remaining);
	}

	public void pause(long nowMs) {
		if (running && !paused) {
			paused = true;
			pausedAtMs = nowMs;
		}
	}

	public void resume(long nowMs) {
		if (running && paused) {
			totalPausedMs += nowMs - pausedAtMs;
			paused = false;
		}
	}

	private long effectiveElapsedMs(long nowMs) {
		long raw = nowMs - startedAtMs;
		long pausedMs = totalPausedMs;
		if (paused) {
			pausedMs += nowMs - pausedAtMs;
		}
		return raw - pausedMs;
	}
}

