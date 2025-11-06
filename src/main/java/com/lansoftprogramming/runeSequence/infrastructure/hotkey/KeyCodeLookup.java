package com.lansoftprogramming.runeSequence.infrastructure.hotkey;

import org.jnativehook.keyboard.NativeKeyEvent;

import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import java.util.Collections;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;

class KeyCodeLookup {
    private static final Map<String, Integer> KEY_CODES = buildKeyCodes();

    static Integer getKeyCode(String token) {
        if (token == null) return null;
        String normalized = token.trim().toUpperCase(Locale.ROOT).replace(' ', '_').replace('-', '_');
        return KEY_CODES.get(normalized);
    }

    private static Map<String, Integer> buildKeyCodes() {
        Map<String, Integer> mapping = new HashMap<>();
        for (Field f : NativeKeyEvent.class.getFields()) {
            int mods = f.getModifiers();
            if (!Modifier.isStatic(mods) || !f.getName().startsWith("VC_") || f.getType() != int.class) {
                continue;
            }
            try {
                int value = f.getInt(null);
                String key = f.getName().substring(3); // drop "VC_"
                mapping.put(key, value);
            } catch (IllegalAccessException ignored) { }
        }
        return Collections.unmodifiableMap(mapping);
    }
}
