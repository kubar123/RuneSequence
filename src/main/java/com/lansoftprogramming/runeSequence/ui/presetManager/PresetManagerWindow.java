package com.lansoftprogramming.runeSequence.ui.presetManager;

import com.lansoftprogramming.runeSequence.infrastructure.config.ConfigManager;
import com.lansoftprogramming.runeSequence.infrastructure.config.RotationConfig;
import com.lansoftprogramming.runeSequence.ui.overlay.toast.ToastManager;
import com.lansoftprogramming.runeSequence.ui.presetManager.detail.SequenceDetailPanel;
import com.lansoftprogramming.runeSequence.ui.presetManager.detail.SequenceDetailService;
import com.lansoftprogramming.runeSequence.ui.presetManager.masterRotations.SequenceListModel;
import com.lansoftprogramming.runeSequence.ui.presetManager.masterRotations.SequenceMasterPanel;
import com.lansoftprogramming.runeSequence.ui.presetManager.palette.AbilityPalettePanel;
import com.lansoftprogramming.runeSequence.ui.presetManager.service.SequenceVisualService;
import com.lansoftprogramming.runeSequence.ui.shared.service.AbilityIconLoader;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.swing.*;
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

    // Toast helpers with logger fallback
    private void toastInfo(String msg) {
        if (toastManager != null) toastManager.info(msg);
        else logger.warn("INFO: {}", msg);
    }

    private void toastError(String msg) {
        if (toastManager != null) toastManager.error(msg);
        else logger.error("ERROR: {}", msg);
    }

    private void toastOk(String msg) {
        if (toastManager != null) toastManager.success(msg);
        else logger.info("OK: {}", msg);
    }

    public PresetManagerWindow(ConfigManager configManager) {
        this.configManager = configManager;
        this.sequenceListModel = new SequenceListModel();

        initializeFrame();
        toastManager = new ToastManager(this);

        initializeComponents();
        layoutComponents();
        wireEventHandlers();
        loadSequences();
        setVisible(true);

        SwingUtilities.invokeLater(() -> detailPanel.setToastManager(toastManager));
    }

    private void initializeFrame() {
        setTitle("Preset Manager");
        setDefaultCloseOperation(JFrame.HIDE_ON_CLOSE);
        setSize(1400, 900);
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

            masterPanel = new SequenceMasterPanel(sequenceListModel);
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
            toastError("Failed to initialize: " + e.getMessage());
        }
    }

    private void layoutComponents() {
        horizontalSplit = new JSplitPane(
                JSplitPane.HORIZONTAL_SPLIT,
                masterPanel,
                palettePanel
        );
        horizontalSplit.setResizeWeight(0.25);
        horizontalSplit.setDividerLocation(0.25);

        verticalSplit = new JSplitPane(
                JSplitPane.VERTICAL_SPLIT,
                horizontalSplit,
                detailPanel
        );
        verticalSplit.setResizeWeight(0.25);
        verticalSplit.setDividerLocation(0.25);

        add(verticalSplit);
    }

    private void wireEventHandlers() {
        masterPanel.addAddListener(this::handleAddSequence);
        masterPanel.addDeleteListener(this::handleDeleteSequence);

        masterPanel.addSelectionListener(entry -> {
            if (entry != null) {
                detailPanel.loadSequence(entry.getId(), entry.getPresetData());
            } else {
                detailPanel.clear();
            }
        });

        detailPanel.addSaveListener(result -> {
            sequenceListModel.upsert(result.getPresetId(), result.getPresetData());
            SwingUtilities.invokeLater(() -> masterPanel.selectSequenceById(result.getPresetId()));
            if (toastManager != null) {
                toastManager.success("Preset saved.");
            }
        });
    }

    private void handleAddSequence() {
        masterPanel.clearSelection();

        RotationConfig.PresetData newPreset = new RotationConfig.PresetData();
       newPreset.setName("new sequence");
       newPreset.setExpression("");

        String newPresetId = UUID.randomUUID().toString();

        detailPanel.startNewSequence(newPresetId, newPreset);
    }

    private void handleDeleteSequence(SequenceListModel.SequenceEntry entry) {
        if (entry == null) {
            toastInfo("Please select a preset to delete.");
            return;
        }

        try {
            RotationConfig rotations = configManager.getRotations();
            if (rotations == null || rotations.getPresets() == null) {
                toastError("Rotation data is not available. Unable to delete preset.");
                return;
            }

            if (rotations.getPresets().remove(entry.getId()) == null) {
                toastError("Preset could not be located by its identifier.");
                return;
            }

            configManager.saveRotations();
            sequenceListModel.loadFromConfig(rotations);
            masterPanel.clearSelection();
            detailPanel.clear();
            toastOk("Preset deleted.");
        } catch (Exception e) {
            logger.error("Failed to delete preset {}", entry.getId(), e);
            toastError("Failed to delete preset: " + e.getMessage());
        }
    }

    private void loadSequences() {
        try {
            RotationConfig rotations = configManager.getRotations();
            sequenceListModel.loadFromConfig(rotations);
            logger.info("Loaded {} sequences", sequenceListModel.getSize());
        } catch (Exception e) {
            logger.error("Failed to load sequences", e);
            toastError("Failed to load sequences: " + e.getMessage());
        }
    }

    public ToastManager toasts() {
        return toastManager;
    }
}