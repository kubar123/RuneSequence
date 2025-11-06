package com.lansoftprogramming.runeSequence.infrastructure.hotkey;

public interface HotkeyListener {
    default void onStartSequence() {}
    default void onRestartSequence() {}
}
