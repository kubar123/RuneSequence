package com.lansoftprogramming.runeSequence.infrastructure.hotkey;

import java.util.Locale;

public enum HotkeyEvent {
    START_SEQUENCE("detection.start"),
    RESTART_SEQUENCE("detection.restart");

    private final String actionName;

    HotkeyEvent(String actionName) {
        this.actionName = actionName;
    }

    public static HotkeyEvent fromAction(String action) {
        if (action == null) return null;
        String normalized = action.trim().toLowerCase(Locale.ROOT);
        for (HotkeyEvent e : values()) {
            if (e.actionName.equals(normalized)) return e;
        }
        return null;
    }
}
