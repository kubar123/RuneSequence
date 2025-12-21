package com.lansoftprogramming.runeSequence.core.sequence.runtime.modification;

/**
 * A timing window that can stay open across multiple steps until it expires or is closed.
 */
public class TimingWindow {
	private long openedAtMs;
	private long durationMs;
	private boolean open;
	private long pausedAtMs;
	private long totalPausedMs;
	private boolean paused;

	public void open(long nowMs, long durationMs) {
		this.open = true;
		this.openedAtMs = nowMs;
		this.durationMs = Math.max(0, durationMs);
		this.pausedAtMs = 0L;
		this.totalPausedMs = 0L;
		this.paused = false;
	}

	public void close() {
		open = false;
	}

	public boolean isOpen() {
		return open;
	}

	public boolean isExpired(long nowMs) {
		if (!open) {
			return true;
		}
		long effectiveElapsed = effectiveElapsedMs(nowMs);
		return effectiveElapsed >= durationMs;
	}

	public long remainingMs(long nowMs) {
		if (!open) {
			return 0L;
		}
		long remaining = durationMs - effectiveElapsedMs(nowMs);
		return Math.max(0L, remaining);
	}

	public void pause(long nowMs) {
		if (open && !paused) {
			paused = true;
			pausedAtMs = nowMs;
		}
	}

	public void resume(long nowMs) {
		if (open && paused) {
			totalPausedMs += nowMs - pausedAtMs;
			paused = false;
		}
	}

	private long effectiveElapsedMs(long nowMs) {
		long raw = nowMs - openedAtMs;
		long pausedMs = totalPausedMs;
		if (paused) {
			pausedMs += nowMs - pausedAtMs;
		}
		return raw - pausedMs;
	}
}

