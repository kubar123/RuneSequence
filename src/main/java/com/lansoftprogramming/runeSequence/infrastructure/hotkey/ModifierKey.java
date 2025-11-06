package com.lansoftprogramming.runeSequence.infrastructure.hotkey;

import org.jnativehook.keyboard.NativeKeyEvent;

import java.util.Locale;

enum ModifierKey {
    CTRL, SHIFT, ALT, META;

    static ModifierKey fromToken(String token) {
        String t = token.trim().toUpperCase(Locale.ROOT);
        return switch (t) {
            case "CTRL", "CONTROL" -> CTRL;
            case "SHIFT" -> SHIFT;
            case "ALT" -> ALT;
            case "META" -> META;
            default -> null;
        };
    }

    static ModifierKey fromKeyCode(int keyCode) {
        return switch (keyCode) {
            case NativeKeyEvent.VC_CONTROL -> CTRL;
            case NativeKeyEvent.VC_SHIFT -> SHIFT;
            case NativeKeyEvent.VC_ALT -> ALT;
            case NativeKeyEvent.VC_META -> META;
            default -> null;
        };
    }
}