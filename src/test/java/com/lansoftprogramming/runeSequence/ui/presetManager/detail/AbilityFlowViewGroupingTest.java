package com.lansoftprogramming.runeSequence.ui.presetManager.detail;

import com.lansoftprogramming.runeSequence.ui.presetManager.drag.model.DragPreviewModel;
import com.lansoftprogramming.runeSequence.ui.presetManager.model.SequenceElement;
import com.lansoftprogramming.runeSequence.ui.shared.model.AbilityItem;
import org.junit.jupiter.api.Test;

import javax.swing.*;
import java.awt.*;
import java.awt.image.BufferedImage;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class AbilityFlowViewGroupingTest {

	@Test
	void renderSequenceElements_shouldGroupAbilityPlusAbilityEvenWithTooltipBetweenAbilityAndSeparator() throws Exception {
		SwingUtilities.invokeAndWait(() -> {
			AbilityFlowView view = new AbilityFlowView(stubDetailService());
			view.attachDragController(stubDragCallback(view));

			List<SequenceElement> elements = List.of(
					SequenceElement.ability("A"),
					SequenceElement.tooltip("note"),
					SequenceElement.plus(),
					SequenceElement.ability("B")
			);

			view.renderSequenceElements(elements);

			assertEquals(1, view.getComponentCount(), "Expected a single AND group panel");
			assertTrue(view.getComponent(0) instanceof AbilityGroupPanel);

			AbilityGroupPanel group = (AbilityGroupPanel) view.getComponent(0);
			Component[] children = group.getComponents();
			assertEquals(4, children.length, "Expected A, tooltip, +, B inside group");

			long cardCount = List.of(children).stream()
					.filter(c -> c instanceof JPanel p && "abilityCard".equals(p.getName()))
					.count();
			assertEquals(3, cardCount, "Expected ability cards for A, tooltip, and B");

			JPanel tooltipCard = (JPanel) List.of(children).stream()
					.filter(c -> c instanceof JPanel p && "abilityCard".equals(p.getName()))
					.filter(c -> {
						Object idx = ((JComponent) c).getClientProperty("elementIndex");
						return idx instanceof Integer && (Integer) idx == 1;
					})
					.findFirst()
					.orElse(null);
			assertNotNull(tooltipCard, "Tooltip card should remain inside group panel");
		});
	}

	@Test
	void renderSequenceElements_shouldGroupAbilityPlusAbilityEvenWhenTooltipBetweenSeparatorAndAbility() throws Exception {
		SwingUtilities.invokeAndWait(() -> {
			AbilityFlowView view = new AbilityFlowView(stubDetailService());
			view.attachDragController(stubDragCallback(view));

			List<SequenceElement> elements = List.of(
					SequenceElement.ability("A"),
					SequenceElement.plus(),
					SequenceElement.tooltip("note"),
					SequenceElement.ability("B")
			);

			view.renderSequenceElements(elements);

			assertEquals(1, view.getComponentCount(), "Expected a single AND group panel");
			assertTrue(view.getComponent(0) instanceof AbilityGroupPanel);

			AbilityGroupPanel group = (AbilityGroupPanel) view.getComponent(0);
			Component[] children = group.getComponents();
			assertEquals(4, children.length, "Expected A, +, tooltip, B inside group");

			assertTrue(children[1] instanceof JLabel, "Second element should be the '+' separator label");
		});
	}

	private SequenceDetailService stubDetailService() {
		return new SequenceDetailService(null, null, null) {
			@Override
			public AbilityItem createAbilityItem(String abilityKey) {
				BufferedImage image = new BufferedImage(1, 1, BufferedImage.TYPE_INT_ARGB);
				return new AbilityItem(abilityKey, abilityKey, 1, "Stub", new ImageIcon(image));
			}
		};
	}

	private com.lansoftprogramming.runeSequence.ui.presetManager.drag.handler.AbilityDragController.DragCallback stubDragCallback(AbilityFlowView view) {
		return new com.lansoftprogramming.runeSequence.ui.presetManager.drag.handler.AbilityDragController.DragCallback() {
			@Override
			public void onDragStart(AbilityItem item, boolean isFromPalette, int abilityIndex) {
			}

			@Override
			public void onDragMove(AbilityItem draggedItem, DragPreviewModel previewModel) {
			}

			@Override
			public void onDragEnd(AbilityItem draggedItem, boolean commit) {
			}

			@Override
			public List<SequenceElement> getCurrentElements() {
				return List.of();
			}

			@Override
			public Component[] getAllCards() {
				return view.getAbilityCardArray();
			}
		};
	}
}

