package com.lansoftprogramming.runeSequence.infrastructure.hotkey;

import org.jnativehook.keyboard.NativeKeyEvent;
import org.junit.jupiter.api.Test;

import java.util.EnumSet;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

class KeyChordParserTest {

	private final KeyChordParser parser = new KeyChordParser();

	@Test
	void shouldParseSingleTokenChord() {
		KeyChord chord = parser.parse(List.of("CTRL+ALT+F11"));

		KeyChord expected = new KeyChord(EnumSet.of(ModifierKey.CTRL, ModifierKey.ALT), NativeKeyEvent.VC_F11);
		assertEquals(expected, chord, "Parser must combine modifiers within one token and resolve F11 key");
	}

	@Test
	void shouldMergeModifiersAcrossTokensAndPreferLastKey() {
		KeyChord chord = parser.parse(List.of("CTRL + A", "SHIFT + Delete"));

		KeyChord expected = new KeyChord(EnumSet.of(ModifierKey.CTRL, ModifierKey.SHIFT), NativeKeyEvent.VC_DELETE);
		assertEquals(expected, chord, "Later key tokens should override earlier ones while merging modifiers");
	}

	@Test
	void shouldReturnNullWhenNoKeyProvided() {
		assertNull(parser.parse(List.of("CTRL + ALT", "SHIFT")));
		assertNull(parser.parse(null));
		assertNull(parser.parse(List.of()));
	}

	@Test
	void shouldNormalizeTokensWithSpacesAndHyphens() {
		KeyChord chord = parser.parse(List.of("shift + page-down"));

		KeyChord expected = new KeyChord(EnumSet.of(ModifierKey.SHIFT), NativeKeyEvent.VC_PAGE_DOWN);
		assertEquals(expected, chord, "Page Down with mixed separators should resolve to the correct key code");
	}
}
