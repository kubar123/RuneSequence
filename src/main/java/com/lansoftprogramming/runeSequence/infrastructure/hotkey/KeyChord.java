package com.lansoftprogramming.runeSequence.infrastructure.hotkey;

import java.util.EnumSet;
import java.util.Objects;

public final class KeyChord {
    private final EnumSet<ModifierKey> modifiers;
    private final int keyCode;

    public KeyChord(EnumSet<ModifierKey> modifiers, int keyCode) {
        this.modifiers = (modifiers == null || modifiers.isEmpty())
                ? EnumSet.noneOf(ModifierKey.class)
                : EnumSet.copyOf(modifiers);
        this.keyCode = keyCode;
    }

    @Override
    public boolean equals(Object obj) {
        if (this == obj) return true;
        if (!(obj instanceof KeyChord other)) return false;
        return keyCode == other.keyCode && Objects.equals(modifiers, other.modifiers);
    }

    @Override
    public int hashCode() {
        return Objects.hash(modifiers, keyCode);
    }
}
