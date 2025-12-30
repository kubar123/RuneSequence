package com.lansoftprogramming.runeSequence.core.sequence.modifier;

import com.lansoftprogramming.runeSequence.core.sequence.model.Alternative;
import com.lansoftprogramming.runeSequence.core.sequence.model.EffectiveAbilityConfig;
import com.lansoftprogramming.runeSequence.core.sequence.model.SequenceDefinition;
import com.lansoftprogramming.runeSequence.core.sequence.model.Step;
import com.lansoftprogramming.runeSequence.core.sequence.parser.SequenceParser;
import com.lansoftprogramming.runeSequence.infrastructure.config.AbilityConfig;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class AbilityModifierEngineTest {

	@Test
	void listDefinitionsForTargetShouldReturnKnownSpecModifiers() {
		List<String> keys = AbilityModifierEngine.listDefinitionsForTarget("spec").stream()
				.map(AbilityModifierEngine.ModifierDefinition::canonicalKey)
				.toList();
		assertTrue(keys.contains("gmaul"));
		assertTrue(keys.contains("armadylbattlestaff"));
	}

	@Test
	void gmaulShouldCollapseIntoSpecTokenAndDisableGcd() {
		SequenceDefinition definition = SequenceParser.parse("gmaul eofspec");
		assertNotNull(definition);
		assertEquals(1, definition.size());

		Step step = definition.getStep(0);
		assertNotNull(step);
		assertEquals(1, step.getTerms().size(), "Modifier+target should collapse into a single term");

		Alternative alt = step.getTerms().get(0).getAlternatives().get(0);
		assertEquals("eofspec", alt.getToken());
		assertEquals(List.of("gmaul"), alt.getAbilityModifiers());
		assertNull(alt.getAbilitySettingsOverrides(), "Modifier effects must not be persisted as per-instance overrides");

		AbilityConfig abilityConfig = new AbilityConfig();
		abilityConfig.putAbility("eofspec", abilityData(true, (short) 0, (short) 0));

		List<EffectiveAbilityConfig> effective = step.getEffectiveAbilityConfigs(abilityConfig);
		assertEquals(1, effective.size(), "Modifier tokens should not become detectable abilities");
		EffectiveAbilityConfig config = effective.get(0);
		assertEquals("eofspec", config.getAbilityKey());
		assertFalse(config.isTriggersGcd(), "gmaul modifier must disable global cooldown");
	}

	@Test
	void armadylBattlestaffShouldMakeSpecChanneledForFiveTicks() {
		SequenceDefinition definition = SequenceParser.parse("armadylbattlestaff spec");
		assertNotNull(definition);
		assertEquals(1, definition.size());

		Step step = definition.getStep(0);
		assertNotNull(step);
		assertEquals(1, step.getTerms().size(), "Modifier+target should collapse into a single term");

		Alternative alt = step.getTerms().get(0).getAlternatives().get(0);
		assertEquals("spec", alt.getToken());
		assertEquals(List.of("armadylbattlestaff"), alt.getAbilityModifiers());
		assertNull(alt.getAbilitySettingsOverrides());

		AbilityConfig abilityConfig = new AbilityConfig();
		abilityConfig.putAbility("spec", abilityData(true, (short) 0, (short) 0));

		List<EffectiveAbilityConfig> effective = step.getEffectiveAbilityConfigs(abilityConfig);
		assertEquals(1, effective.size());
		EffectiveAbilityConfig config = effective.get(0);
		assertEquals((short) 5, config.getCastDuration(), "armadylbattlestaff modifier must override cast duration");
	}

	private static AbilityConfig.AbilityData abilityData(boolean triggersGcd, short castDuration, short cooldown) {
		AbilityConfig.AbilityData data = new AbilityConfig.AbilityData();
		data.setTriggersGcd(triggersGcd);
		data.setCastDuration(castDuration);
		data.setCooldown(cooldown);
		return data;
	}
}
