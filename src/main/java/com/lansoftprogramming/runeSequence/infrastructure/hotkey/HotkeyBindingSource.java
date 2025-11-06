package com.lansoftprogramming.runeSequence.infrastructure.hotkey;

import com.lansoftprogramming.runeSequence.infrastructure.config.AppSettings;

import java.util.*;

public class HotkeyBindingSource {

	private final KeyChordParser parser = new KeyChordParser();

	public Map<HotkeyEvent, List<KeyChord>> loadBindings(AppSettings.HotkeySettings hotkeySettings) {
		if (hotkeySettings == null) {
			return Collections.emptyMap();
		}
		List<AppSettings.HotkeySettings.Binding> bindings = hotkeySettings.getBindings();
		if (bindings == null || bindings.isEmpty()) {
			return Collections.emptyMap();
		}

		Map<HotkeyEvent, List<KeyChord>> hotkeyBindings = new EnumMap<>(HotkeyEvent.class);

		for (AppSettings.HotkeySettings.Binding b : bindings) {
			if (b == null) continue;
			HotkeyEvent event = HotkeyEvent.fromAction(b.getAction());
			List<List<String>> alternatives;
			if (b.isUserEnabled() && b.getUser() != null && !b.getUser().isEmpty()) {
				alternatives = b.getUser();
			} else {
				alternatives = b.getGlobal();
			}
			if (event == null || alternatives == null) continue;

			List<KeyChord> chordsForEvent = hotkeyBindings.computeIfAbsent(event, k -> new ArrayList<>());
			for (List<String> alt : alternatives) {
				KeyChord chord = parser.parse(alt);
				if (chord != null) {
					chordsForEvent.add(chord);
				}
			}
		}

		// Make the inner lists unmodifiable
		hotkeyBindings.replaceAll((e, v) -> Collections.unmodifiableList(v));

		return Collections.unmodifiableMap(hotkeyBindings);
	}
}