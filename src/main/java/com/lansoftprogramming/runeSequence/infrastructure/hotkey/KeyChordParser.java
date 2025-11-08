package com.lansoftprogramming.runeSequence.infrastructure.hotkey;

import java.util.EnumSet;
import java.util.List;

class KeyChordParser {

    KeyChord parse(List<String> tokens) {
        if (tokens == null || tokens.isEmpty()) return null;

        EnumSet<ModifierKey> modifiers = EnumSet.noneOf(ModifierKey.class);
        Integer keyCode = null;

        for (String raw : tokens) {
            if (raw == null) continue;
            String[] parts = raw.split("\\+");
            for (String p : parts) {
                String token = p.trim();
                if (token.isEmpty()) continue;

                ModifierKey mk = ModifierKey.fromToken(token);
                if (mk != null) {
                    modifiers.add(mk);
                    continue;
                }
                Integer candidate = KeyCodeLookup.getKeyCode(token);
                if (candidate != null) {
                    keyCode = candidate;
                }
            }
        }
        if (keyCode == null) return null;
        return new KeyChord(modifiers, keyCode);
    }
}
