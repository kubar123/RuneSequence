package com.lansoftprogramming.runeSequence.application;

import com.lansoftprogramming.runeSequence.core.detection.TemplateDetector;
import com.lansoftprogramming.runeSequence.core.sequence.model.*;
import com.lansoftprogramming.runeSequence.core.sequence.runtime.ActiveSequence;
import com.lansoftprogramming.runeSequence.core.sequence.runtime.TooltipSchedule;
import com.lansoftprogramming.runeSequence.infrastructure.config.AbilityConfig;
import com.lansoftprogramming.runeSequence.ui.notification.NotificationService;
import org.bytedeco.opencv.opencv_core.Mat;
import org.junit.jupiter.api.Test;

import java.nio.file.Path;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SequenceManagerEffectiveConfigTest {

	@Test
	void latchSelectionShouldHonorEffectiveTriggersGcdOverrides() {
		AbilityConfig abilityConfig = new AbilityConfig();
		AbilityConfig.AbilityData base = new AbilityConfig.AbilityData();
		base.setTriggersGcd(false);
		abilityConfig.putAbility("Alpha", base);

		AbilitySettingsOverrides overrides = AbilitySettingsOverrides.builder()
				.triggersGcd(true)
				.build();

		SequenceDefinition definition = new SequenceDefinition(List.of(
				new Step(List.of(new Term(List.of(new Alternative("Alpha", overrides)))))
		));

		Map<String, SequenceDefinition> namedSequences = new HashMap<>();
		namedSequences.put("test", definition);

		SequenceManager manager = new SequenceManager(
				namedSequences,
				Map.of("test", TooltipSchedule.empty()),
				abilityConfig,
				new NoopNotificationService(),
				new TemplateDetector(new TestTemplateCache(), abilityConfig)
		);
		manager.activateSequence("test");

		List<ActiveSequence.DetectionRequirement> selected = manager.previewGcdLatchRequirements();

		assertEquals(1, selected.size());
		ActiveSequence.DetectionRequirement requirement = selected.get(0);
		assertEquals("Alpha#0", requirement.instanceId());
		assertTrue(requirement.effectiveAbilityConfig().isTriggersGcd(), "Override should mark ability as GCD-triggering");
	}

	@Test
	void latchSelectionShouldOnlyTrackCurrentStepRequirements() {
		AbilityConfig abilityConfig = new AbilityConfig();

		AbilityConfig.AbilityData alpha = new AbilityConfig.AbilityData();
		alpha.setTriggersGcd(true);
		abilityConfig.putAbility("Alpha", alpha);

		AbilityConfig.AbilityData beta = new AbilityConfig.AbilityData();
		beta.setTriggersGcd(true);
		abilityConfig.putAbility("Beta", beta);

		SequenceDefinition definition = new SequenceDefinition(List.of(
				new Step(List.of(new Term(List.of(new Alternative("Alpha", null))))),
				new Step(List.of(new Term(List.of(new Alternative("Beta", null)))))
		));

		Map<String, SequenceDefinition> namedSequences = new HashMap<>();
		namedSequences.put("test", definition);

		SequenceManager manager = new SequenceManager(
				namedSequences,
				Map.of("test", TooltipSchedule.empty()),
				abilityConfig,
				new NoopNotificationService(),
				new TemplateDetector(new TestTemplateCache(), abilityConfig)
		);
		manager.activateSequence("test");

		List<ActiveSequence.DetectionRequirement> selected = manager.previewGcdLatchRequirements();

		assertEquals(1, selected.size());
		assertEquals("Alpha#0", selected.get(0).instanceId());
	}

	private static final class NoopNotificationService implements NotificationService {
		@Override
		public void showInfo(String message) {
		}

		@Override
		public void showSuccess(String message) {
		}

		@Override
		public void showWarning(String message) {
		}

		@Override
		public void showError(String message) {
		}

		@Override
		public boolean showConfirmDialog(String title, String message) {
			return false;
		}
	}

	private static final class TestTemplateCache extends TemplateCache {
		TestTemplateCache() {
			super(Path.of("."));
		}

		@Override
		public int initialize() {
			return 0;
		}

		@Override
		public Mat getTemplate(String abilityName) {
			return null;
		}

		@Override
		public boolean hasTemplate(String abilityName) {
			return false;
		}
	}
}
