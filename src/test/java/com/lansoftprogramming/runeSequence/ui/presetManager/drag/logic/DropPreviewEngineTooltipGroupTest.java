package com.lansoftprogramming.runeSequence.ui.presetManager.drag.logic;

import com.lansoftprogramming.runeSequence.ui.presetManager.drag.model.DropZoneType;
import com.lansoftprogramming.runeSequence.ui.presetManager.model.SequenceElement;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

class DropPreviewEngineTooltipGroupTest {

	@Test
	void resolveGroupZoneAt_shouldIgnoreTooltipsBetweenAbilityAndSeparator() {
		DropPreviewEngine engine = new DropPreviewEngine();
		List<SequenceElement> elements = List.of(
				SequenceElement.ability("A"),
				SequenceElement.tooltip("note"),
				SequenceElement.plus(),
				SequenceElement.ability("B")
		);

		assertEquals(DropZoneType.AND, engine.resolveGroupZoneAt(elements, 0));
		assertEquals(DropZoneType.AND, engine.resolveGroupZoneAt(elements, 3));
	}

	@Test
	void calculatePreview_shouldUsePreviewElementIndicesNotOriginalIndices() {
		DropPreviewEngine engine = new DropPreviewEngine();
		List<SequenceElement> original = List.of(
				SequenceElement.ability("A"),
				SequenceElement.tooltip("note"),
				SequenceElement.plus(),
				SequenceElement.ability("B"),
				SequenceElement.plus(),
				SequenceElement.ability("C")
		);
		List<SequenceElement> preview = List.of(
				SequenceElement.ability("A"),
				SequenceElement.tooltip("note"),
				SequenceElement.plus(),
				SequenceElement.ability("C")
		);

		DropPreviewEngine.CardSnapshot snapshot = new DropPreviewEngine.CardSnapshot(
				new java.awt.Rectangle(0, 0, 50, 68),
				3, // element index of "C" in the preview list
				0,
				null,
				"C"
		);
		DropPreviewEngine.GeometryCache geometry = new DropPreviewEngine.GeometryCache(List.of(snapshot), 0);

		// dragPoint far right to ensure dropSide RIGHT and beyondRightEdge => leaving group => NEXT
		java.awt.Point dragPoint = new java.awt.Point(1000, 34);
		var model = engine.calculatePreview(dragPoint, geometry, preview, original, true);

		assertEquals(DropZoneType.NEXT, model.getDropPreview().getZoneType());
		assertEquals(4, model.getDropPreview().getInsertIndex(), "Insert index should be at end of preview elements");
	}
}
