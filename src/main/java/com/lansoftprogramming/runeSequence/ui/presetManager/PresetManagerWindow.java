package com.lansoftprogramming.runeSequence.ui.presetManager;

import com.lansoftprogramming.runeSequence.core.sequence.parser.SequenceParser;
import com.lansoftprogramming.runeSequence.infrastructure.config.AppSettings;
import com.lansoftprogramming.runeSequence.infrastructure.config.ConfigManager;
import com.lansoftprogramming.runeSequence.infrastructure.config.RotationConfig;
import com.lansoftprogramming.runeSequence.ui.overlay.toast.ToastClient;
import com.lansoftprogramming.runeSequence.ui.overlay.toast.ToastManager;
import com.lansoftprogramming.runeSequence.ui.presetManager.detail.SequenceDetailPanel;
import com.lansoftprogramming.runeSequence.ui.presetManager.detail.SequenceDetailService;
import com.lansoftprogramming.runeSequence.ui.presetManager.masterRotations.SelectedSequenceIndicator;
import com.lansoftprogramming.runeSequence.ui.presetManager.masterRotations.SequenceListModel;
import com.lansoftprogramming.runeSequence.ui.presetManager.masterRotations.SequenceMasterPanel;
import com.lansoftprogramming.runeSequence.ui.presetManager.palette.AbilityPalettePanel;
import com.lansoftprogramming.runeSequence.ui.presetManager.service.SequenceVisualService;
import com.lansoftprogramming.runeSequence.ui.shared.service.AbilityIconLoader;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.swing.*;
import java.io.IOException;
import java.util.UUID;

public class PresetManagerWindow extends JFrame {
    private static final Logger logger = LoggerFactory.getLogger(PresetManagerWindow.class);

    private final ConfigManager configManager;
    private final SequenceListModel sequenceListModel;

    private SequenceMasterPanel masterPanel;
    private SequenceDetailPanel detailPanel;
    private AbilityPalettePanel palettePanel;

    private JSplitPane verticalSplit;
    private JSplitPane horizontalSplit;
    private ToastManager toastManager;
    private ToastClient toasts = ToastManager.loggingFallback(logger);
    private boolean suppressSelectionUpdate;
    private String currentSelectionId;
    private boolean autoSaveInProgress;

    public PresetManagerWindow(ConfigManager configManager) {
        this.configManager = configManager;
        this.sequenceListModel = new SequenceListModel();

        initializeFrame();
        toastManager = new ToastManager(this);
        toasts = toastManager;

        initializeComponents();
        layoutComponents();
        wireEventHandlers();
        loadSequences();
        setVisible(true);

        SwingUtilities.invokeLater(() -> {
            detailPanel.setToastClient(toasts);
            masterPanel.setToastClient(toasts);
        });
    }

    private void initializeFrame() {
        setTitle("Preset Manager");
        setDefaultCloseOperation(JFrame.HIDE_ON_CLOSE);
        setSize(1000, 750);
        setLocationRelativeTo(null);
    }

    private void initializeComponents() {
        try {
            AbilityIconLoader iconLoader = new AbilityIconLoader(
                configManager.getConfigDir().resolve("Abilities")
            );

            SequenceVisualService visualService = new SequenceVisualService();
            SequenceDetailService detailService = new SequenceDetailService(
                configManager, iconLoader, visualService
            );

            SelectedSequenceIndicator selectionIndicator = SelectedSequenceIndicator.forSettings(
                configManager.getSettings()
            );

            masterPanel = new SequenceMasterPanel(sequenceListModel, selectionIndicator);
            detailPanel = new SequenceDetailPanel(detailService);
            palettePanel = new AbilityPalettePanel(
                configManager.getAbilities(),
                configManager.getAbilityCategories(),
                iconLoader
            );

            // Wire palette to detail panel for drag coordination
            palettePanel.setDetailPanel(detailPanel);

        } catch (Exception e) {
            logger.error("Failed to initialize components", e);
            toasts.error("Failed to initialize: " + e.getMessage());
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

		masterPanel.setExpressionValidator(expression -> {
			if (expression == null || expression.isBlank()) {
				return false;
			}
			if (!expression.contains("→") && !expression.contains("->")) {
				return false;
			}
			try {
				SequenceParser.parse(expression);
				return true;
			} catch (Exception e) {
				return false;
			}
		});
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
            if (result.isCreated()) {
                SwingUtilities.invokeLater(() -> masterPanel.selectSequenceById(result.getPresetId()));
                currentSelectionId = result.getPresetId();
            }
            toasts.success("Preset saved.");
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
		createNewPreset("Imported Sequence", expression);
	}

    private void handleDeleteSequence(SequenceListModel.SequenceEntry entry) {
        if (entry == null) {
            toasts.info("Please select a preset to delete.");
            return;
        }

        AppSettings settings = configManager.getSettings();
        String previouslySelectedId = settings != null && settings.getRotation() != null
                ? settings.getRotation().getSelectedId()
                : null;

        //Confirmation
        int confirm = JOptionPane.showConfirmDialog(
                masterPanel,
                "Are you sure you want to delete this item?",
                "Confirm Delete",
                JOptionPane.YES_NO_OPTION,
                JOptionPane.WARNING_MESSAGE
        );
        if (confirm != JOptionPane.YES_OPTION) return;

        try {
            RotationConfig rotations = configManager.getRotations();
            if (rotations == null || rotations.getPresets() == null) {
                toasts.error("Rotation data is not available. Unable to delete preset.");
                return;
            }

            if (rotations.getPresets().remove(entry.getId()) == null) {
                toasts.error("Preset could not be located by its identifier.");
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

            toasts.success("Preset deleted.");
        } catch (Exception e) {
            logger.error("Failed to delete preset {}", entry.getId(), e);
            toasts.error("Failed to delete preset: " + e.getMessage());
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
            toasts.error("Failed to load sequences: " + e.getMessage());
        }
    }

    public ToastClient toasts() {
        return toasts;
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
                toasts.error("Failed to set active rotation: " + e.getMessage());
            }
        }

        masterPanel.refreshList();
    }

	private void createNewPreset(String name, String expression) {
		clearSelectionSilently();

		RotationConfig.PresetData newPreset = new RotationConfig.PresetData();
		newPreset.setName(name);
		newPreset.setExpression(expression);

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
