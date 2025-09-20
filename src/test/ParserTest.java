//package com.lansoftprogramming.runeSequence.sequence;
//
//import org.junit.jupiter.api.Test;
//
//import static org.junit.jupiter.api.Assertions.*;
//
//public class ParserTest {
//
//	private void assertParsed(String expected, String expression) {
//		assertEquals(expected, SequenceParser.parse(expression).toString());
//	}
//
//	@Test
//	void testSimpleSequence() {
//		assertParsed("A → B → C", "A → B → C");
//	}
//
//	@Test
//	void testAndSequence() {
//		assertParsed("A + B + C", "A + B + C");
//	}
//
//	@Test
//	void testOrSequence() {
//		assertParsed("(A / B / C)", "A / B / C");
//	}
//
//	@Test
//	void testCombined() {
//		assertParsed("A + (B / C) → D", "A + B/C → D");
//	}
//
//	@Test
//	void testParentheses() {
//		assertParsed("(A + B) → C", "(A+B) → C");
//	}
//
//	@Test
//	void testNestedParentheses() {
//		assertParsed("X → (Y + (W / Z)) → V", "X → (Y + (W/Z)) → V");
//	}
//
//	@Test
//	void testMultiWordAbilities() {
//		assertParsed("limitless rout → piercing shot", "limitless rout → piercing shot");
//	}
//
//	@Test
//	void testAbilityWithParenthesesInName() {
//		assertParsed("roarofawakening odetodeceit spec (0 tick)", "roarofawakening odetodeceit spec (0 tick)");
//	}
//
//	@Test
//	void testComplexUserExample1() {
//		String expression = "surge + vulnbomb + bloat → deathskulls  → omniguard spec → necroauto → necroauto → ingen + roarofawakening odetodeceit spec (0 tick) → soulsap → commandskeleton → soulsap → deathguard90 eofspec → necroauto → volleyofsouls → soulsap → necroauto → touchofdeath";
//		String expected = "surge + vulnbomb + bloat → deathskulls → omniguard spec → necroauto → necroauto → ingen + roarofawakening odetodeceit spec (0 tick) → soulsap → commandskeleton → soulsap → deathguard90 eofspec → necroauto → volleyofsouls → soulsap → necroauto → touchofdeath";
//		assertParsed(expected, expression);
//	}
//
//	@Test
//	void testComplexUserExample2() {
//		String expression = "necroauto → roarofawakening odetodeceit spec (0 tick) → soulsap → omniguard spec → necroauto → soulsap → commandskeleton → ivingdeath + adrenrenewal";
//		String expected = "necroauto → roarofawakening odetodeceit spec (0 tick) → soulsap → omniguard spec → necroauto → soulsap → commandskeleton → ivingdeath + adrenrenewal";
//		assertParsed(expected, expression);
//	}
//
//	@Test
//	void testDoubleArrowShouldFail() {
//		String expression = "A → → B";
//		assertThrows(IllegalStateException.class, () -> SequenceParser.parse(expression));
//	}
//
//	@Test
//	void testComplexUserExample3() {
//		String expression = "volleyofsouls → ingen + roarofawakening odetodeceit spec (0 tick) → soulsap → omniguard spec → necroauto";
//		String expected = "volleyofsouls → ingen + roarofawakening odetodeceit spec (0 tick) → soulsap → omniguard spec → necroauto";
//		assertParsed(expected, expression);
//	}
//
//	@Test
//	void testComplexUserExample4() {
//		String expression = "livingdeath + adrenrenewal + vulnbomb → touchofdeath";
//		String expected = "livingdeath + adrenrenewal + vulnbomb → touchofdeath";
//		assertParsed(expected, expression);
//	}
//
//	@Test
//	void testComplexUserExample5() {
//		// Note: The expected output correctly reflects that the input expression contained groups.
//		String expression = "frag + walk → (limitless rout) → (piercing / snap / grico)";
//		String expected = "frag + walk → (limitless rout) → (piercing / snap / grico)";
//		assertParsed(expected, expression);
//	}
//
//	@Test
//	void testInvalidSyntaxMissingOperand() {
//		assertThrows(IllegalStateException.class, () -> SequenceParser.parse("A +"));
//	}
//
//	@Test
//	void testInvalidSyntaxUnmatchedParen() {
//		assertThrows(IllegalStateException.class, () -> SequenceParser.parse("(A + B"));
//	}
//}