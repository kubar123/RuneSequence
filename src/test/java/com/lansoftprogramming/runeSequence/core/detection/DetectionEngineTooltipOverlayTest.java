package com.lansoftprogramming.runeSequence.core.detection;

import com.lansoftprogramming.runeSequence.application.SequenceManager;
import com.lansoftprogramming.runeSequence.application.TemplateCache;
import com.lansoftprogramming.runeSequence.core.sequence.runtime.SequenceTooltip;
import com.lansoftprogramming.runeSequence.infrastructure.capture.ScreenCapture;
import com.lansoftprogramming.runeSequence.infrastructure.config.AbilityConfig;
import com.lansoftprogramming.runeSequence.ui.notification.NotificationService;
import com.lansoftprogramming.runeSequence.ui.overlay.MouseTooltipOverlay;
import com.lansoftprogramming.runeSequence.ui.overlay.OverlayRenderer;
import org.bytedeco.opencv.opencv_core.Mat;
import org.junit.jupiter.api.Test;

import java.awt.*;
import java.nio.file.Path;
import java.util.Collections;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

class DetectionEngineTooltipOverlayTest {

	@Test
	void shouldForwardCurrentTooltipsToMouseOverlay() throws Exception {
		RecordingSequenceManager sequenceManager = new RecordingSequenceManager();
		OverlayRenderer overlayRenderer = new OverlayRenderer(() -> false);
		RecordingTooltipOverlay tooltipOverlay = new RecordingTooltipOverlay();
		NotificationService notifications = new NoopNotificationService();

		DetectionEngine engine = new DetectionEngine(
				new TestScreenCapture(),
				new TemplateDetector(new TestTemplateCache(), new AbilityConfig()),
				sequenceManager,
				overlayRenderer,
				tooltipOverlay,
				notifications,
				50
		);

		var updateMethod = DetectionEngine.class.getDeclaredMethod("updateOverlays");
		updateMethod.setAccessible(true);

		List<SequenceTooltip> tooltips = List.of(
				new SequenceTooltip(0, null, "First"),
				new SequenceTooltip(0, null, "Second")
		);
		sequenceManager.setTooltips(tooltips);

		updateMethod.invoke(engine);

		assertEquals(tooltips, tooltipOverlay.lastTooltips);
	}

	private static final class RecordingSequenceManager extends SequenceManager {
		private List<SequenceTooltip> tooltips = List.of();

		RecordingSequenceManager() {
			super(Collections.emptyMap(), Collections.emptyMap(), new AbilityConfig(), new NoopNotificationService(),
					new TemplateDetector(new TestTemplateCache(), new AbilityConfig()));
		}

		void setTooltips(List<SequenceTooltip> tooltips) {
			this.tooltips = tooltips != null ? List.copyOf(tooltips) : List.of();
		}

		@Override
		public synchronized List<com.lansoftprogramming.runeSequence.core.detection.DetectionResult> getCurrentAbilities() {
			return List.of();
		}

		@Override
		public synchronized List<com.lansoftprogramming.runeSequence.core.detection.DetectionResult> getNextAbilities() {
			return List.of();
		}

		@Override
		public synchronized List<SequenceTooltip> getCurrentTooltips() {
			return tooltips;
		}

		@Override
		public synchronized boolean shouldDetect() {
			return true;
		}
	}

	private static final class RecordingTooltipOverlay extends MouseTooltipOverlay {
		private List<SequenceTooltip> lastTooltips = List.of();

		@Override
		public void showTooltips(List<SequenceTooltip> tooltips) {
			this.lastTooltips = tooltips != null ? List.copyOf(tooltips) : List.of();
		}

		@Override
		public void clear() {
			this.lastTooltips = List.of();
		}
	}

	private static final class TestScreenCapture extends ScreenCapture {
		@Override
		public Mat captureScreen() {
			return new Mat();
		}

		@Override
		public Rectangle getRegion() {
			return new Rectangle(0, 0, 1, 1);
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
