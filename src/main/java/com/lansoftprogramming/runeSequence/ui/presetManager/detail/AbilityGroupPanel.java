package com.lansoftprogramming.runeSequence.ui.presetManager.detail;

import javax.swing.*;
import java.awt.*;

class AbilityGroupPanel extends JPanel {
	AbilityGroupPanel(Color groupColor) {
		setLayout(new FlowLayout(FlowLayout.LEFT, 5, 5));
		setBackground(groupColor);
		setBorder(BorderFactory.createCompoundBorder(
				BorderFactory.createLineBorder(groupColor.darker()),
				BorderFactory.createEmptyBorder(5, 5, 5, 5)
		));
	}
}
