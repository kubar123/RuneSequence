package com.lansoftprogramming.runeSequence.application;

import com.lansoftprogramming.runeSequence.core.detection.TemplateDetector;
import com.lansoftprogramming.runeSequence.core.sequence.model.Alternative;
import com.lansoftprogramming.runeSequence.core.sequence.model.SequenceDefinition;
import com.lansoftprogramming.runeSequence.core.sequence.model.Step;
import com.lansoftprogramming.runeSequence.core.sequence.model.Term;
import com.lansoftprogramming.runeSequence.core.sequence.runtime.SequenceTooltip;
import com.lansoftprogramming.runeSequence.core.sequence.runtime.TooltipSchedule;
import com.lansoftprogramming.runeSequence.infrastructure.config.AbilityConfig;
import com.lansoftprogramming.runeSequence.ui.notification.NotificationService;
import org.bytedeco.opencv.opencv_core.Mat;
import org.junit.jupiter.api.Test;

import java.nio.file.Path;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

class SequenceManagerTooltipTest {


	@Test
	void shouldReturnTooltipsForCurrentStep() {
		String expression = "(First) A→(Second) B→(Third) C";

		AbilityConfig abilityConfig = new AbilityConfig();
		abilityConfig.putAbility("A", new AbilityConfig.AbilityData());
		abilityConfig.putAbility("B", new AbilityConfig.AbilityData());
		abilityConfig.putAbility("C", new AbilityConfig.AbilityData());

		TooltipScheduleBuilder builder = new TooltipScheduleBuilder(abilityConfig.getAbilities().keySet());
		TooltipScheduleBuilder.BuildResult buildResult = builder.build(expression);
		SequenceDefinition definition = buildResult.definition();
		TooltipSchedule schedule = buildResult.schedule();

		Map<String, SequenceDefinition> namedSequences = new HashMap<>();
		namedSequences.put("test", definition);

		Map<String, TooltipSchedule> tooltipSchedules = new HashMap<>();
		tooltipSchedules.put("test", schedule);

		NotificationService notifications = new NoopNotificationService();
		TemplateDetector detector = new TemplateDetector(new TestTemplateCache(), abilityConfig);

		SequenceManager manager = new SequenceManager(namedSequences, tooltipSchedules, abilityConfig, notifications, detector);
		manager.activateSequence("test");

		List<String> step0 = manager.getCurrentTooltips().stream()
				.map(SequenceTooltip::message)
				.toList();
		assertEquals(List.of("First"), step0);

		manager.moveActiveSequenceToStep(1);
		List<String> step1 = manager.getCurrentTooltips().stream()
				.map(SequenceTooltip::message)
				.toList();
		assertEquals(List.of("Second"), step1);

		manager.moveActiveSequenceToStep(2);
		List<String> step2 = manager.getCurrentTooltips().stream()
				.map(SequenceTooltip::message)
				.toList();
		assertEquals(List.of("Third"), step2);

		manager.moveActiveSequenceToStep(3);
		List<SequenceTooltip> step3 = manager.getCurrentTooltips();
		assertTrue(step3.isEmpty());
	}

	@Test
	void shouldClearActiveSequenceWhenActivationFails() {
		AbilityConfig abilityConfig = new AbilityConfig();
		abilityConfig.putAbility("A", new AbilityConfig.AbilityData());

		SequenceDefinition definition = new SequenceDefinition(List.of(
				new Step(List.of(new Term(List.of(new Alternative("A")))))
		));

		Map<String, SequenceDefinition> namedSequences = new HashMap<>();
		namedSequences.put("test", definition);

		NotificationService notifications = new NoopNotificationService();
		TemplateDetector detector = new TemplateDetector(new TestTemplateCache(), abilityConfig);
		SequenceManager manager = new SequenceManager(
				namedSequences,
				Map.of("test", TooltipSchedule.empty()),
				abilityConfig,
				notifications,
				detector
		);

		assertTrue(manager.activateSequence("test"));
		assertTrue(manager.hasActiveSequence());

		assertFalse(manager.activateSequence("missing"));
		assertFalse(manager.hasActiveSequence());
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