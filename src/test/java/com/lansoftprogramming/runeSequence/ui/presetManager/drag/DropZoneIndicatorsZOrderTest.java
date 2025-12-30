package com.lansoftprogramming.runeSequence.ui.presetManager.drag;

import org.junit.jupiter.api.Test;

import javax.swing.*;
import java.awt.*;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class DropZoneIndicatorsZOrderTest {

	@Test
	void setGlassPane_shouldKeepIndicatorsAboveFloatingDragCard() throws Exception {
		SwingUtilities.invokeAndWait(() -> {
			JPanel glassPane = new JPanel(null);

			JPanel floatingCard = new JPanel();
			floatingCard.setName("floatingDragCard");
			glassPane.add(floatingCard);

			DropZoneIndicators indicators = new DropZoneIndicators();
			indicators.setGlassPane(glassPane);

			Component top = findByName(glassPane, "dropZoneTopIndicator");
			Component bottom = findByName(glassPane, "dropZoneBottomIndicator");
			Component line = findByName(glassPane, "dropZoneInsertionLine");
			assertNotNull(top);
			assertNotNull(bottom);
			assertNotNull(line);

			int floatingZ = glassPane.getComponentZOrder(floatingCard);
			int topZ = glassPane.getComponentZOrder(top);
			int bottomZ = glassPane.getComponentZOrder(bottom);
			int lineZ = glassPane.getComponentZOrder(line);

			// Lower Z-order means painted later (on top).
			assertTrue(topZ < floatingZ, "Top indicator should paint above floating drag card");
			assertTrue(bottomZ < floatingZ, "Bottom indicator should paint above floating drag card");
			assertTrue(lineZ < floatingZ, "Insertion line should paint above floating drag card");
		});
	}

	private static Component findByName(Container container, String name) {
		for (Component component : container.getComponents()) {
			if (name.equals(component.getName())) {
				return component;
			}
		}
		return null;
	}
}