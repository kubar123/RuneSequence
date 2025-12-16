package com.lansoftprogramming.runeSequence.ui.presetManager.detail;

import com.lansoftprogramming.runeSequence.core.sequence.model.AbilitySettingsOverrides;
import com.lansoftprogramming.runeSequence.infrastructure.config.AbilitySettingsOverridesMapper;
import com.lansoftprogramming.runeSequence.infrastructure.config.RotationConfig;
import com.lansoftprogramming.runeSequence.infrastructure.config.dto.PresetAbilitySettings;
import com.lansoftprogramming.runeSequence.ui.notification.NotificationService;
import com.lansoftprogramming.runeSequence.ui.presetManager.model.SequenceElement;
import com.lansoftprogramming.runeSequence.ui.presetManager.service.AbilityOverridesService;
import com.lansoftprogramming.runeSequence.ui.shared.model.AbilityItem;
import org.junit.jupiter.api.Test;

import javax.swing.*;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class SequenceDetailPresenterAbilitySettingsTest {

	@Test
	void saveSequenceShouldNotPersistAbilitySettingsWhenNoOverrides() {
		CapturingDetailService detailService = new CapturingDetailService(elements(
				SequenceElement.ability("Alpha"),
				SequenceElement.arrow(),
				SequenceElement.ability("Beta")
		));
		AbilityOverridesService overridesService = new AbilityOverridesService(new AbilitySettingsOverridesMapper());
		AbilityFlowView flowView = new AbilityFlowView(detailService);
		TestView view = new TestView("TestRotation");

		SequenceDetailPresenter presenter = new SequenceDetailPresenter(detailService, overridesService, flowView, view, new NoopNotifications());
		presenter.loadSequence(preset("TestRotation", "Alpha→Beta", null));

		presenter.saveSequence();

		assertEquals("Alpha→Beta", detailService.lastSavedExpression);
		assertNull(detailService.lastSavedAbilitySettings, "No overrides should behave exactly like legacy sequences and omit ability_settings");
	}

	@Test
	void saveSequenceShouldAutoAssignLabelsForOverridesAndPersistSettings() {
		AbilitySettingsOverrides overrides = AbilitySettingsOverrides.builder().cooldown((short) 12).build();
		CapturingDetailService detailService = new CapturingDetailService(elements(
				SequenceElement.ability("Alpha", null, overrides)
		));
		AbilityOverridesService overridesService = new AbilityOverridesService(new AbilitySettingsOverridesMapper());
		AbilityFlowView flowView = new AbilityFlowView(detailService);
		TestView view = new TestView("Overrides");

		SequenceDetailPresenter presenter = new SequenceDetailPresenter(detailService, overridesService, flowView, view, new NoopNotifications());
		presenter.loadSequence(preset("Overrides", "Alpha", null));

		presenter.saveSequence();

		assertNotNull(detailService.lastSavedAbilitySettings);
		AbilitySettingsOverridesMapper mapper = new AbilitySettingsOverridesMapper();
		assertEquals(
				java.util.Map.of("1", overrides),
				mapper.toDomain(detailService.lastSavedAbilitySettings),
				"Overrides should be persisted under an auto-assigned instance label"
		);
		assertEquals("Alpha[*1]", detailService.lastSavedExpression, "Expression should include the assigned label when overrides exist");
	}

	@Test
	void saveSequenceShouldStripAutoLabelsWhenNoOverridesRemain() {
		CapturingDetailService detailService = new CapturingDetailService(elements(
				SequenceElement.ability("Alpha", "1", null)
		));
		AbilityOverridesService overridesService = new AbilityOverridesService(new AbilitySettingsOverridesMapper());
		AbilityFlowView flowView = new AbilityFlowView(detailService);
		TestView view = new TestView("Reset");

		SequenceDetailPresenter presenter = new SequenceDetailPresenter(detailService, overridesService, flowView, view, new NoopNotifications());
		presenter.loadSequence(preset("Reset", "Alpha[*1]", null));

		presenter.saveSequence();

		assertEquals("Alpha", detailService.lastSavedExpression, "Legacy export should not retain labels when no overrides exist");
		assertNull(detailService.lastSavedAbilitySettings);
	}

	private static RotationConfig.PresetData preset(String name, String expression, PresetAbilitySettings settings) {
		RotationConfig.PresetData preset = new RotationConfig.PresetData();
		preset.setName(name);
		preset.setExpression(expression);
		preset.setAbilitySettings(settings);
		return preset;
	}

	private static List<SequenceElement> elements(SequenceElement... elements) {
		return List.of(elements);
	}

	private static final class CapturingDetailService extends SequenceDetailService {
		private final List<SequenceElement> parsed;
		private String lastSavedExpression;
		private PresetAbilitySettings lastSavedAbilitySettings;

		CapturingDetailService(List<SequenceElement> parsed) {
			super(null, null, null);
			this.parsed = parsed;
		}

		@Override
		public List<SequenceElement> parseSequenceExpression(String expression,
		                                                    java.util.Map<String, AbilitySettingsOverrides> overridesByLabel) {
			return parsed;
		}

		@Override
		public AbilityItem createAbilityItem(String abilityKey) {
			return new AbilityItem(abilityKey, abilityKey, 0, "Unknown", null);
		}

		@Override
		public SaveOutcome saveSequence(String existingId,
		                               RotationConfig.PresetData referencePreset,
		                               String sequenceName,
		                               String expression,
		                               PresetAbilitySettings abilitySettings) {
			this.lastSavedExpression = expression;
			this.lastSavedAbilitySettings = abilitySettings;
			RotationConfig.PresetData saved = new RotationConfig.PresetData();
			saved.setName(sequenceName);
			saved.setExpression(expression);
			saved.setAbilitySettings(abilitySettings);
			return SaveOutcome.success(new SaveResult("id", saved, false), "ok");
		}
	}

	private static final class TestView implements SequenceDetailPresenter.View {
		private final JComponent component = new JPanel();
		private String name;

		TestView(String name) {
			this.name = name;
		}

		@Override
		public void setSequenceName(String name) {
			this.name = name;
		}

		@Override
		public String getSequenceName() {
			return name;
		}

		@Override
		public JComponent asComponent() {
			return component;
		}

		@Override
		public void showRotationSettings(String presetId, RotationConfig.PresetData presetData) {
		}
	}

	private static final class NoopNotifications implements NotificationService {
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
}
