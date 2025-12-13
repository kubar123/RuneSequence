package com.lansoftprogramming.runeSequence.ui.presetManager;

import com.lansoftprogramming.runeSequence.application.SequenceRunService;
import com.lansoftprogramming.runeSequence.application.TooltipScheduleBuilder;
import com.lansoftprogramming.runeSequence.core.sequence.model.AbilitySettingsOverrides;
import com.lansoftprogramming.runeSequence.core.sequence.parser.RotationDslCodec;
import com.lansoftprogramming.runeSequence.infrastructure.config.AppSettings;
import com.lansoftprogramming.runeSequence.infrastructure.config.ConfigManager;
import com.lansoftprogramming.runeSequence.infrastructure.config.RotationConfig;
import com.lansoftprogramming.runeSequence.infrastructure.config.dto.PresetAbilitySettings;
import com.lansoftprogramming.runeSequence.ui.notification.DefaultNotificationService;
import com.lansoftprogramming.runeSequence.ui.notification.NotificationService;
import com.lansoftprogramming.runeSequence.ui.overlay.toast.ToastManager;
import com.lansoftprogramming.runeSequence.ui.presetManager.detail.SequenceDetailPanel;
import com.lansoftprogramming.runeSequence.ui.presetManager.detail.SequenceDetailService;
import com.lansoftprogramming.runeSequence.ui.presetManager.masterRotations.SelectedSequenceIndicator;
import com.lansoftprogramming.runeSequence.ui.presetManager.masterRotations.SequenceListModel;
import com.lansoftprogramming.runeSequence.ui.presetManager.masterRotations.SequenceMasterPanel;
import com.lansoftprogramming.runeSequence.ui.presetManager.palette.AbilityPalettePanel;
import com.lansoftprogramming.runeSequence.ui.presetManager.service.AbilityOverridesService;
import com.lansoftprogramming.runeSequence.ui.shared.service.AbilityIconLoader;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.swing.*;
import java.io.IOException;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

public class PresetManagerWindow extends JFrame {
    private static final Logger logger = LoggerFactory.getLogger(PresetManagerWindow.class);

    private final ConfigManager configManager;
    private final SequenceListModel sequenceListModel;
    private final AbilityIconLoader iconLoader;
    private final SequenceDetailService detailService;
    private final AbilityOverridesService overridesService;
    private final SelectedSequenceIndicator selectionIndicator;
    private final SequenceRunService sequenceRunService;

    private SequenceMasterPanel masterPanel;
    private SequenceDetailPanel detailPanel;
    private AbilityPalettePanel palettePanel;

    private JSplitPane verticalSplit;
    private JSplitPane horizontalSplit;
    private ToastManager toastManager;
    private NotificationService notifications;
    private boolean suppressSelectionUpdate;
    private String currentSelectionId;
    private boolean autoSaveInProgress;

    public PresetManagerWindow(
            ConfigManager configManager,
            SequenceListModel sequenceListModel,
            AbilityIconLoader iconLoader,
            SequenceDetailService detailService,
            AbilityOverridesService overridesService,
            SelectedSequenceIndicator selectionIndicator,
            SequenceRunService sequenceRunService) {
        this.configManager = configManager;
        this.sequenceListModel = sequenceListModel;
        this.iconLoader = iconLoader;
        this.detailService = detailService;
        this.overridesService = overridesService;
        this.selectionIndicator = selectionIndicator;
        this.sequenceRunService = sequenceRunService;

        initializeFrame();
        toastManager = new ToastManager(this);
        notifications = new DefaultNotificationService(this, toastManager);

        boolean initialized = initializeComponents();
        if (initialized) {
            layoutComponents();
            wireEventHandlers();
            loadSequences();
            setVisible(true);
        } else {
            logger.error("PresetManagerWindow failed to initialize; skipping layout and wiring.");
        }
    }

    private void initializeFrame() {
        setTitle("Preset Manager");
        setDefaultCloseOperation(JFrame.HIDE_ON_CLOSE);
        setSize(1000, 750);
        setLocationRelativeTo(null);
    }

    private boolean initializeComponents() {
        try {
            masterPanel = new SequenceMasterPanel(sequenceListModel, overridesService, selectionIndicator, sequenceRunService);
            detailPanel = new SequenceDetailPanel(detailService, overridesService, notifications);
            palettePanel = new AbilityPalettePanel(
                configManager.getAbilities(),
                configManager.getAbilityCategories(),
                iconLoader
            );

            masterPanel.setNotificationService(notifications);

            // Wire palette to detail panel for drag coordination
            palettePanel.setDetailPanel(detailPanel);
            return true;
        } catch (Exception e) {
            logger.error("Failed to initialize components", e);
            notifications.showError("Failed to initialize: " + e.getMessage());
            return false;
        }
    }

    private void layoutComponents() {
        horizontalSplit = new JSplitPane(
                JSplitPane.HORIZONTAL_SPLIT,
                masterPanel,
                palettePanel
        );
        horizontalSplit.setResizeWeight(0.25);
        horizontalSplit.setDividerLocation(275);

        verticalSplit = new JSplitPane(
                JSplitPane.VERTICAL_SPLIT,
                horizontalSplit,
                detailPanel
        );
        verticalSplit.setResizeWeight(0.5);
        verticalSplit.setDividerLocation(350);

        add(verticalSplit);
    }

    private void wireEventHandlers() {
		masterPanel.addAddListener(this::handleAddSequence);
		masterPanel.addDeleteListener(this::handleDeleteSequence);

		if (sequenceRunService != null) {
			masterPanel.setExpressionValidator(sequenceRunService::canBuildSequence);
		} else {
			TooltipScheduleBuilder scheduleBuilder = new TooltipScheduleBuilder(
					configManager.getAbilities().getAbilities().keySet()
			);
			masterPanel.setExpressionValidator(expression -> {
				if (expression == null || expression.isBlank()) {
					return false;
				}
				try {
					return scheduleBuilder.build(expression).definition() != null;
				} catch (Exception e) {
					return false;
				}
			});
		}
		masterPanel.addImportListener(this::handleImportSequence);

        masterPanel.addSelectionListener(entry -> {
            if (suppressSelectionUpdate) {
                currentSelectionId = entry != null ? entry.getId() : null;
                return;
            }

            if (isAutoSaveEnabled()) {
                maybeAutoSaveCurrent();
            } else {
                detailPanel.discardChanges();
            }

            currentSelectionId = entry != null ? entry.getId() : null;

            if (entry != null) {
                detailPanel.loadSequence(entry.getId(), entry.getPresetData());
                updateActiveRotation(entry.getId());
            } else {
                detailPanel.clear();
                updateActiveRotation(null);
            }
        });

        detailPanel.addSaveListener(result -> {
            sequenceListModel.upsert(result.getPresetId(), result.getPresetData());

            if (sequenceRunService != null) {
                boolean refreshed = sequenceRunService.refreshSequenceFromExpression(
                        result.getPresetId(),
                        result.getPresetData() != null ? result.getPresetData().getExpression() : ""
                );
                if (!refreshed) {
                    logger.warn("Saved preset '{}' could not be parsed into a runnable sequence.", result.getPresetId());
                }
            }

            if (result.isCreated()) {
                SwingUtilities.invokeLater(() -> masterPanel.selectSequenceById(result.getPresetId()));
                currentSelectionId = result.getPresetId();
            }
            notifications.showSuccess("Preset saved.");
        });
    }

    private void maybeAutoSaveCurrent() {
        if (autoSaveInProgress || !isAutoSaveEnabled()) {
            return;
        }
        if (currentSelectionId == null || sequenceListModel.indexOf(currentSelectionId) < 0) {
            return;
        }
        if (!detailPanel.hasUnsavedChanges()) {
            return;
        }

        autoSaveInProgress = true;
        try {
            detailPanel.saveSequence();
        } finally {
            autoSaveInProgress = false;
        }
    }

    private boolean isAutoSaveEnabled() {
        AppSettings.RotationSettings rotation = getRotationSettings(true);
        return rotation != null && rotation.isAutoSaveOnSwitch();
    }

	private void handleAddSequence() {
		createNewPreset("new sequence", "");
	}

	private void handleImportSequence(String expression) {
        try {
            RotationDslCodec.ParsedRotation parsed = RotationDslCodec.parse(expression);
            String importedExpression = parsed.expression();
            Map<String, AbilitySettingsOverrides> overridesByLabel = parsed.perInstanceOverrides();
            Set<String> labelsInExpression = RotationDslCodec.collectLabelsInExpression(importedExpression);

            Map<String, AbilitySettingsOverrides> filtered = new LinkedHashMap<>();
            for (Map.Entry<String, AbilitySettingsOverrides> entry : overridesByLabel.entrySet()) {
                String label = entry.getKey();
                if (label == null || label.isBlank()) {
                    continue;
                }
                if (!labelsInExpression.contains(label)) {
                    logger.warn("Ignoring imported per-instance override for label '{}' with no matching label in expression.", label);
                    continue;
                }
                filtered.put(label, entry.getValue());
            }

            PresetAbilitySettings abilitySettings = overridesService.buildAbilitySettingsFromOverrides(filtered);
            createNewPreset("Imported Sequence", importedExpression, abilitySettings);
        } catch (Exception e) {
            logger.warn("Failed to parse imported rotation DSL; falling back to legacy import.", e);
            createNewPreset("Imported Sequence", expression);
        }
	}

    private void handleDeleteSequence(SequenceListModel.SequenceEntry entry) {
        if (entry == null) {
            notifications.showInfo("Please select a preset to delete.");
            return;
        }

        AppSettings settings = configManager.getSettings();
        String previouslySelectedId = settings != null && settings.getRotation() != null
                ? settings.getRotation().getSelectedId()
                : null;

        //Confirmation
        boolean confirmed = notifications.showConfirmDialog(
                "Confirm Delete",
                "Are you sure you want to delete this item?"
        );
        if (!confirmed) return;

        try {
            RotationConfig rotations = configManager.getRotations();
            if (rotations == null || rotations.getPresets() == null) {
                notifications.showError("Rotation data is not available. Unable to delete preset.");
                return;
            }

            if (rotations.getPresets().remove(entry.getId()) == null) {
                notifications.showError("Preset could not be located by its identifier.");
                return;
            }

            configManager.saveRotations();
            sequenceListModel.loadFromConfig(rotations);

            String newSelectionId = null;
            // Keep previous selection if it still exists; otherwise fall back to first preset.
            if (previouslySelectedId != null && sequenceListModel.indexOf(previouslySelectedId) >= 0) {
                newSelectionId = previouslySelectedId;
            } else if (sequenceListModel.getSize() > 0) {
                newSelectionId = sequenceListModel.getElementAt(0).getId();
            }

            if (newSelectionId != null) {
                String selectionTarget = newSelectionId;
                SwingUtilities.invokeLater(() -> masterPanel.selectSequenceById(selectionTarget));
            } else {
                masterPanel.clearSelection();
                detailPanel.clear();
                updateActiveRotation(null);
            }

            notifications.showSuccess("Preset deleted.");
        } catch (Exception e) {
            logger.error("Failed to delete preset {}", entry.getId(), e);
            notifications.showError("Failed to delete preset: " + e.getMessage());
        }
    }

    private void loadSequences() {
        try {
            RotationConfig rotations = configManager.getRotations();
            sequenceListModel.loadFromConfig(rotations);
            logger.info("Loaded {} sequences", sequenceListModel.getSize());

            AppSettings settings = configManager.getSettings();
            if (settings != null && settings.getRotation() != null) {
                String selectedId = settings.getRotation().getSelectedId();
                if (selectedId != null && !selectedId.isBlank()) {
                    SwingUtilities.invokeLater(() -> masterPanel.selectSequenceById(selectedId));
                }
            }
        } catch (Exception e) {
            logger.error("Failed to load sequences", e);
            notifications.showError("Failed to load sequences: " + e.getMessage());
        }
    }

    /**
     * Clears the master list selection without triggering selection listeners.
     * This prevents programmatic actions (add/import) from overwriting the
     * persisted "active" rotation in settings.json.
     */
    private void clearSelectionSilently() {
        suppressSelectionUpdate = true;
        try {
            masterPanel.clearSelection();
        } finally {
            suppressSelectionUpdate = false;
        }
    }

    private void updateActiveRotation(String rotationId) {
        AppSettings settings = configManager.getSettings();
        if (settings == null) {
            logger.warn("Skipping active rotation update because settings are unavailable.");
            return;
        }

        AppSettings.RotationSettings rotationSettings = getRotationSettings(true);
        if (rotationSettings == null) {
            logger.warn("Rotation settings unavailable; cannot update active rotation.");
            return;
        }

        String currentId = rotationSettings.getSelectedId();
        boolean changed = (rotationId != null && !rotationId.equals(currentId))
                || (rotationId == null && currentId != null);
        rotationSettings.setSelectedId(rotationId);

        if (changed) {
            try {
                configManager.saveSettings();
            } catch (IOException e) {
                logger.error("Failed to persist active rotation {}", rotationId, e);
                notifications.showError("Failed to set active rotation: " + e.getMessage());
            }
        }

        masterPanel.refreshList();

        if (sequenceRunService != null) {
            boolean switched = sequenceRunService.switchActiveSequence(rotationId);
            if (!switched && rotationId != null && !rotationId.isBlank()) {
                logger.warn("Active rotation '{}' could not be applied to the detection engine.", rotationId);
                notifications.showError("Selected rotation is not available for detection.");
            }
        }
    }

	private void createNewPreset(String name, String expression) {
        createNewPreset(name, expression, null);
    }

    private void createNewPreset(String name, String expression, PresetAbilitySettings abilitySettings) {
		clearSelectionSilently();

		RotationConfig.PresetData newPreset = new RotationConfig.PresetData();
		newPreset.setName(name);
		newPreset.setExpression(expression);
        newPreset.setAbilitySettings(abilitySettings);

		String newPresetId = UUID.randomUUID().toString();

        detailPanel.startNewSequence(newPresetId, newPreset);
    }

    /**
     * Returns rotation settings, optionally creating and attaching a new instance to settings.
     * Does not persist to disk; callers remain responsible for saving if they mutate values.
     */
    private AppSettings.RotationSettings getRotationSettings(boolean createIfMissing) {
        AppSettings settings = configManager.getSettings();
        if (settings == null) {
            return null;
        }
        AppSettings.RotationSettings rotation = settings.getRotation();
        if (rotation == null && createIfMissing) {
            rotation = new AppSettings.RotationSettings();
            settings.setRotation(rotation);
        }
        return rotation;
    }
}