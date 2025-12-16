package com.lansoftprogramming.runeSequence.core.sequence.model;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class AbilityTokenTest {

	@Test
	void shouldStripInstanceLabelFromToken() {
		assertEquals("fireball", AbilityToken.baseAbilityKey("fireball[*2]"));
	}

	@Test
	void shouldReturnBaseKeyWhenTokenHasNoLabel() {
		assertEquals("fireball", AbilityToken.baseAbilityKey("fireball"));
	}

	@Test
	void shouldParseInstanceLabel() {
		AbilityToken token = AbilityToken.parse("frostbolt[*12]");

		assertEquals("frostbolt", token.getAbilityKey());
		assertTrue(token.getInstanceLabel().isPresent());
		assertEquals("12", token.getInstanceLabel().get());
	}

	@Test
	void shouldFormatTokenWithLabel() {
		assertEquals("ignite[*3]", AbilityToken.format("ignite", "3"));
	}

	@Test
	void shouldFormatTokenWithoutLabelWhenBlank() {
		assertEquals("ignite", AbilityToken.format("ignite", " "));
	}

	@Test
	void shouldHandleNullAbilityKey() {
		assertNull(AbilityToken.format(null, "3"));
	}
}