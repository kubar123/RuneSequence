package com.lansoftprogramming.runeSequence.ui.theme;

import com.lansoftprogramming.runeSequence.ui.presetManager.drag.model.DropZoneType;

import java.awt.*;
import java.util.EnumMap;
import java.util.Map;

/**
 * Central palette for UI colors to keep visual choices consistent.
 */
public final class UiColorPalette {

	private static final Map<DropZoneType, Color> DROP_ZONE_OVERLAY_COLORS = buildOverlayColors();
	private static final Map<DropZoneType, Color> DROP_ZONE_HIGHLIGHT_COLORS = buildHighlightColors();

	private static final Color DEFAULT_DROP_ZONE_OVERLAY = new Color(50, 50, 50, 200);
	private static final Color DEFAULT_DROP_ZONE_HIGHLIGHT = Color.WHITE;

	// Drag-and-drop visuals
	public static final Color INSERTION_LINE = new Color(50, 50, 50, 200);

	// Detection overlay borders
	public static final Color OVERLAY_CURRENT_AND = new Color(0, 255, 0, 220);
	public static final Color OVERLAY_NEXT_AND = new Color(255, 0, 0, 200);
	public static final Color OVERLAY_CURRENT_OR = new Color(221, 0, 255, 220);
	public static final Color OVERLAY_NEXT_OR = new Color(140, 0, 255, 200);

	// Ability flow grouping cards
	public static final Color ABILITY_GROUP_AND_BACKGROUND = new Color(220, 255, 223);
	public static final Color ABILITY_GROUP_OR_BACKGROUND = new Color(196, 163, 231);

	private UiColorPalette() {
		// Utility class
	}

	public static Color getDropZoneOverlayColor(DropZoneType zoneType) {
		if (zoneType == null) {
			return DEFAULT_DROP_ZONE_OVERLAY;
		}
		return DROP_ZONE_OVERLAY_COLORS.getOrDefault(zoneType, DEFAULT_DROP_ZONE_OVERLAY);
	}

	public static Color getDropZoneHighlightColor(DropZoneType zoneType) {
		if (zoneType == null) {
			return DEFAULT_DROP_ZONE_HIGHLIGHT;
		}
		return DROP_ZONE_HIGHLIGHT_COLORS.getOrDefault(zoneType, DEFAULT_DROP_ZONE_HIGHLIGHT);
	}

	private static Map<DropZoneType, Color> buildOverlayColors() {
		Map<DropZoneType, Color> colors = new EnumMap<>(DropZoneType.class);
		colors.put(DropZoneType.AND, new Color(170, 255, 171, 220));
		colors.put(DropZoneType.OR, new Color(158, 99, 220, 220));
		colors.put(DropZoneType.NEXT, new Color(250, 117, 159, 220));
		return colors;
	}

	private static Map<DropZoneType, Color> buildHighlightColors() {
		Map<DropZoneType, Color> colors = new EnumMap<>(DropZoneType.class);
		colors.put(DropZoneType.AND, new Color(170, 255, 171));
		colors.put(DropZoneType.OR, new Color(158, 99, 220));
		colors.put(DropZoneType.NEXT, new Color(250, 117, 159));
		return colors;
	}
}