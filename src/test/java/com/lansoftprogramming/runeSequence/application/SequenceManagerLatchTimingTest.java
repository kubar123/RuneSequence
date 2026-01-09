package com.lansoftprogramming.runeSequence.application;

import com.lansoftprogramming.runeSequence.core.detection.TemplateDetector;
import com.lansoftprogramming.runeSequence.core.sequence.model.Alternative;
import com.lansoftprogramming.runeSequence.core.sequence.model.SequenceDefinition;
import com.lansoftprogramming.runeSequence.core.sequence.model.Step;
import com.lansoftprogramming.runeSequence.core.sequence.model.Term;
import com.lansoftprogramming.runeSequence.core.sequence.runtime.ActiveSequence;
import com.lansoftprogramming.runeSequence.core.sequence.runtime.TooltipSchedule;
import com.lansoftprogramming.runeSequence.infrastructure.config.AbilityConfig;
import com.lansoftprogramming.runeSequence.ui.notification.NotificationService;
import org.bytedeco.opencv.opencv_core.Mat;
import org.junit.jupiter.api.Test;

import java.awt.*;
import java.lang.reflect.Field;
import java.nio.file.Path;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.List;
import java.util.Map;
import java.util.function.LongSupplier;

import static org.bytedeco.opencv.global.opencv_core.CV_8UC3;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SequenceManagerLatchTimingTest {

	@Test
	void shouldBackdateLatchTimeToFirstDarkFrame() throws Exception {
		AbilityConfig abilityConfig = new AbilityConfig();
		AbilityConfig.AbilityData a = new AbilityConfig.AbilityData();
		a.setTriggersGcd(true);
		abilityConfig.putAbility("A", a);
		AbilityConfig.AbilityData b = new AbilityConfig.AbilityData();
		b.setTriggersGcd(true);
		abilityConfig.putAbility("B", b);

		SequenceDefinition definition = new SequenceDefinition(List.of(
				new Step(List.of(new Term(List.of(new Alternative("A"))))),
				new Step(List.of(new Term(List.of(new Alternative("B")))))
		));

		Deque<Double> brightnessSamples = new ArrayDeque<>(List.of(
				100.0, 100.0, // baselines
				70.0, 70.0,   // frame 1
				70.0, 70.0,   // frame 2
				70.0, 70.0    // frame 3
		));
		TemplateDetector detector = new FakeTemplateDetector(new TestTemplateCache(), abilityConfig, brightnessSamples);
		NotificationService notifications = new NoopNotificationService();

		long[] times = new long[]{1000L, 1600L, 2200L};
		LongSupplier nowMs = new LongSupplier() {
			private int index = 0;

			@Override
			public long getAsLong() {
				int current = Math.min(index, times.length - 1);
				index++;
				return times[current];
			}
		};

		SequenceManager manager = new SequenceManager(
				Map.of("test", definition),
				Map.of("test", TooltipSchedule.empty()),
				abilityConfig,
				notifications,
				detector,
				nowMs
		);

		assertTrue(manager.activateSequence("test"));

		CapturingActiveSequence capturing = new CapturingActiveSequence(definition, abilityConfig);
		Field activeSequenceField = SequenceManager.class.getDeclaredField("activeSequence");
		activeSequenceField.setAccessible(true);
		activeSequenceField.set(manager, capturing);

		SequenceController controller = new SequenceController(manager);
		manager.setSequenceController(controller);
		controller.onStartSequence();

		Mat frame = new Mat(20, 20, CV_8UC3);
		try {
			manager.processDetection(frame, List.of());
			manager.processDetection(frame, List.of());
			manager.processDetection(frame, List.of());
		} finally {
			frame.close();
		}

		assertEquals(1000L, capturing.latchTimeMs);
		assertEquals(SequenceController.State.RUNNING, controller.getState());
	}

	private static final class CapturingActiveSequence extends ActiveSequence {
		private long latchTimeMs = 0L;

		private CapturingActiveSequence(SequenceDefinition def, AbilityConfig abilityConfig) {
			super(def, abilityConfig);
		}

		@Override
		public void processDetections(List<com.lansoftprogramming.runeSequence.core.detection.DetectionResult> results) {
			// No-op: this test only validates latch timing and should not advance steps via real timers.
		}

		@Override
		public boolean onLatchStart(long latchTimeMs) {
			this.latchTimeMs = latchTimeMs;
			return false;
		}
	}

	private static final class FakeTemplateDetector extends TemplateDetector {
		private final Deque<Double> brightnessSamples;
		private final Rectangle roi = new Rectangle(0, 0, 10, 10);

		private FakeTemplateDetector(TemplateCache templateCache, AbilityConfig abilityConfig, Deque<Double> brightnessSamples) {
			super(templateCache, abilityConfig);
			this.brightnessSamples = brightnessSamples;
		}

		@Override
		public Rectangle resolveAbilityRoi(Mat frame, String abilityKey, Double detectionThreshold) {
			return new Rectangle(roi);
		}

		@Override
		public double measureBrightness(Mat frame, Rectangle roi) {
			Double sample = brightnessSamples.pollFirst();
			if (sample == null) {
				throw new IllegalStateException("No more brightness samples");
			}
			return sample;
		}
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
