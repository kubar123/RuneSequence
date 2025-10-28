package com.lansoftprogramming.runeSequence.ui.presetManager;

import com.lansoftprogramming.runeSequence.infrastructure.config.ConfigManager;
import com.lansoftprogramming.runeSequence.infrastructure.config.RotationConfig;
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

public class PresetManagerWindow extends JFrame {
    private static final Logger logger = LoggerFactory.getLogger(PresetManagerWindow.class);

    private final ConfigManager configManager;
    private final SequenceListModel sequenceListModel;

    private SequenceMasterPanel masterPanel;
    private SequenceDetailPanel detailPanel;
    private AbilityPalettePanel palettePanel;

    private JSplitPane verticalSplit;
    private JSplitPane horizontalSplit;

    public PresetManagerWindow(ConfigManager configManager) {
       this.configManager = configManager;
       this.sequenceListModel = new SequenceListModel();

       initializeFrame();
       initializeComponents();
       layoutComponents();
       wireEventHandlers();
       loadSequences();
       setVisible(true);
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
          JOptionPane.showMessageDialog(this,
                "Failed to initialize: " + e.getMessage(),
                "Error",
                JOptionPane.ERROR_MESSAGE);
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
       });
    }

    private void loadSequences() {
       try {
          RotationConfig rotations = configManager.getRotations();
          sequenceListModel.loadFromConfig(rotations);
          logger.info("Loaded {} sequences", sequenceListModel.getSize());
       } catch (Exception e) {
          logger.error("Failed to load sequences", e);
          JOptionPane.showMessageDialog(this,
                "Failed to load sequences: " + e.getMessage(),
                "Error",
                JOptionPane.ERROR_MESSAGE);
       }
    }
}
