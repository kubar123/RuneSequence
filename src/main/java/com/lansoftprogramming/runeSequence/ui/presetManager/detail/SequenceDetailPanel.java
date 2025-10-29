package com.lansoftprogramming.runeSequence.ui.presetManager.detail;

import com.lansoftprogramming.runeSequence.infrastructure.config.RotationConfig;
import com.lansoftprogramming.runeSequence.ui.shared.model.AbilityItem;

import javax.swing.*;
import javax.swing.border.EmptyBorder;
import java.awt.*;

public class SequenceDetailPanel extends JPanel implements SequenceDetailPresenter.View {
	private final JTextField sequenceNameField;
	private final JButton settingsButton;
	private final JButton saveButton;
	private final JButton discardButton;
	private final AbilityFlowView abilityFlowView;
	private final SequenceDetailPresenter presenter;

	public SequenceDetailPanel(SequenceDetailService detailService) {
		setLayout(new BorderLayout());
		setBorder(new EmptyBorder(10, 10, 10, 10));

		sequenceNameField = new JTextField();
		settingsButton = new JButton("Settings");
		saveButton = new JButton("Save");
		discardButton = new JButton("Discard");
		abilityFlowView = new AbilityFlowView(detailService);
		presenter = new SequenceDetailPresenter(detailService, abilityFlowView, this);

		layoutComponents();
		registerEventHandlers();
	}

	private void layoutComponents() {
		JPanel headerPanel = createHeaderPanel();
		JScrollPane contentScrollPane = createContentScrollPane();

		add(headerPanel, BorderLayout.NORTH);
		add(contentScrollPane, BorderLayout.CENTER);
	}

	private JPanel createHeaderPanel() {
		JPanel headerPanel = new JPanel(new BorderLayout(10, 0));

		JPanel namePanel = new JPanel(new BorderLayout(5, 0));
		namePanel.add(new JLabel("�,?Sequence Name:"), BorderLayout.WEST);
		namePanel.add(sequenceNameField, BorderLayout.CENTER);

		JPanel buttonPanel = new JPanel(new FlowLayout(FlowLayout.RIGHT, 5, 0));
		buttonPanel.add(settingsButton);
		buttonPanel.add(discardButton);
		buttonPanel.add(saveButton);

		headerPanel.add(namePanel, BorderLayout.CENTER);
		headerPanel.add(buttonPanel, BorderLayout.EAST);

		return headerPanel;
	}

	private JScrollPane createContentScrollPane() {
		JScrollPane scrollPane = new JScrollPane(abilityFlowView);
		scrollPane.setBorder(BorderFactory.createLineBorder(Color.LIGHT_GRAY));
		scrollPane.setHorizontalScrollBarPolicy(JScrollPane.HORIZONTAL_SCROLLBAR_NEVER);
		scrollPane.setVerticalScrollBarPolicy(JScrollPane.VERTICAL_SCROLLBAR_AS_NEEDED);
		return scrollPane;
	}

	private void registerEventHandlers() {
		saveButton.addActionListener(e -> presenter.saveSequence());
	}

	public void startPaletteDrag(AbilityItem item, JPanel card, Point startPoint) {
		presenter.startPaletteDrag(item, card, startPoint);
	}

	public void loadSequence(RotationConfig.PresetData presetData) {
		presenter.loadSequence(presetData);
	}

	public void loadSequence(String presetId, RotationConfig.PresetData presetData) {
		presenter.loadSequence(presetId, presetData);
	}

	public void clear() {
		presenter.clear();
	}

	public void addSaveListener(SaveListener listener) {
		presenter.addSaveListener(listener);
	}

	@Override
	public void setSequenceName(String name) {
		sequenceNameField.setText(name);
	}

	@Override
	public String getSequenceName() {
		return sequenceNameField.getText();
	}

	@Override
	public void showSaveDialog(String message, int messageType) {
		JOptionPane.showMessageDialog(
				this,
				message,
				"Save Sequence",
				messageType
		);
	}

	@Override
	public JComponent asComponent() {
		return this;
	}

	public interface SaveListener {
		void onSequenceSaved(SequenceDetailService.SaveResult result);
	}
}
