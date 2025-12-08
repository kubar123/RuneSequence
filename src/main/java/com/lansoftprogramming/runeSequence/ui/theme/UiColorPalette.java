package com.lansoftprogramming.runeSequence.ui.theme;

import com.lansoftprogramming.runeSequence.ui.presetManager.drag.model.DropZoneType;

import javax.swing.*;
import javax.swing.border.Border;
import javax.swing.border.EmptyBorder;
import java.awt.*;
import java.util.EnumMap;
import java.util.Map;

/**
 * Central palette for UI colors to keep visual choices consistent.
 */
public final class UiColorPalette {

	private static final Map<DropZoneType, Color> DROP_ZONE_OVERLAY_COLORS = buildOverlayColors();
	private static final Map<DropZoneType, Color> DROP_ZONE_HIGHLIGHT_COLORS = buildHighlightColors();

	// --- Base & neutrals (common primitives used across screens) ---
	public static final Color TRANSPARENT = new Color(0, 0, 0, 0);
	public static final Color TRANSLUCENT_WINDOW = new Color(0, 0, 0, 1);
	public static final Color BASE_WHITE = Color.WHITE;
	public static final Color BASE_BLACK = Color.BLACK;
	public static final Color TEXT_PRIMARY = new Color(34, 38, 42);
	public static final Color TEXT_INVERSE = Color.WHITE;
	public static final Color TEXT_MUTED = Color.GRAY;
	public static final Color TEXT_DANGER = Color.RED;
	public static final Color TEXT_SUCCESS = new Color(0, 128, 0);
	public static final Color INPUT_INVALID_BACKGROUND = new Color(255, 230, 230);

	private static final Color DEFAULT_DROP_ZONE_OVERLAY = new Color(50, 50, 50, 200);
	private static final Color DEFAULT_DROP_ZONE_HIGHLIGHT = Color.WHITE;

	// --- Drag-and-drop visuals (palette + insertion helpers) ---
	public static final Color INSERTION_LINE = new Color(50, 50, 50, 200);

	// --- Detection overlay borders (live detection highlights) ---
	public static final Color OVERLAY_CURRENT_AND = new Color(0, 255, 0, 220);
	public static final Color OVERLAY_NEXT_AND = new Color(255, 0, 0, 200);
	public static final Color OVERLAY_CURRENT_OR = new Color(221, 0, 255, 220);
	public static final Color OVERLAY_NEXT_OR = new Color(140, 0, 255, 200);

	// --- Tooltip overlay (mouse-follow runtime hints) ---
	public static final Color TOOLTIP_OVERLAY_BACKGROUND = new Color(20, 20, 20, 220);
	public static final Color TOOLTIP_OVERLAY_BORDER = new Color(255, 255, 255, 200);
	public static final Color TOOLTIP_OVERLAY_TEXT = Color.WHITE;

	// --- Ability flow grouping cards (AND/OR grouping chips) ---
	public static final Color ABILITY_GROUP_AND_BACKGROUND = new Color(220, 255, 223);
	public static final Color ABILITY_GROUP_OR_BACKGROUND = new Color(196, 163, 231);

	// --- Core UI surfaces (cards, text, borders) ---
	public static final Color UI_TEXT_COLOR = new Color(255, 255, 255);
	public static final Color UI_CARD_BACKGROUND = new Color(10, 29, 38);
	public static final Color UI_CARD_DIMMED_BACKGROUND = new Color(240, 240, 240);
	public static final Color UI_CARD_BORDER_SUBTLE = Color.LIGHT_GRAY;
	public static final Color UI_CARD_BORDER_STRONG = new Color(180, 180, 180);

	// --- Region selector overlays ---
	public static final Color REGION_OVERLAY_SCRIM = new Color(0, 0, 0, 100);
	public static final Color REGION_SELECTION_BORDER = new Color(0, 120, 215, 180);
	public static final Color REGION_SELECTION_FILL = new Color(0, 120, 215, 60);

	// --- Toast notifications ---
	public static final Color TOAST_SUCCESS_ACCENT = new Color(46, 170, 112);
	public static final Color TOAST_SUCCESS_BACKGROUND = new Color(233, 247, 238);
	public static final Color TOAST_SUCCESS_FOREGROUND = new Color(23, 111, 74);
	public static final Color TOAST_INFO_ACCENT = new Color(64, 140, 236);
	public static final Color TOAST_INFO_BACKGROUND = new Color(233, 240, 250);
	public static final Color TOAST_INFO_FOREGROUND = new Color(41, 92, 168);
	public static final Color TOAST_WARNING_ACCENT = new Color(245, 189, 65);
	public static final Color TOAST_WARNING_BACKGROUND = new Color(253, 246, 229);
	public static final Color TOAST_WARNING_FOREGROUND = new Color(151, 111, 24);
	public static final Color TOAST_ERROR_ACCENT = new Color(220, 82, 70);
	public static final Color TOAST_ERROR_BACKGROUND = new Color(250, 234, 232);
	public static final Color TOAST_ERROR_FOREGROUND = new Color(160, 36, 28);
	public static final Color TOAST_MESSAGE_FOREGROUND = TEXT_PRIMARY;
	public static final Color TOAST_SHADOW = new Color(0, 0, 0, 160);

	// --- Placeholders and icons (fallback glyphs) ---
	public static final Color PLACEHOLDER_BACKGROUND = new Color(220, 220, 220);
	public static final Color PLACEHOLDER_BORDER = new Color(180, 180, 180);
	public static final Color PLACEHOLDER_FOREGROUND = new Color(150, 150, 150);
	public static final Color INSERT_ICON_FILL = new Color(66, 133, 244);

	// --- Drop zone indicators (drag affordances) ---
	public static final Color DROP_ZONE_LABEL_BACKGROUND = new Color(50, 50, 50, 220);
	public static final Color DROP_ZONE_LABEL_BORDER = new Color(100, 100, 100);

	// --- Selection state indicators (e.g., selected preset) ---
	public static final Color SELECTION_ACTIVE_FILL = new Color(76, 175, 80);
	public static final Color SELECTION_ACTIVE_BORDER = new Color(46, 125, 50);

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

	// Fonts
	public static final Font SYMBOL_LARGE = new Font(Font.SANS_SERIF, Font.BOLD, 18);

	public static Font boldSans(int size) {
		return new Font(Font.SANS_SERIF, Font.BOLD, size);
	}

	// Borders
	public static final Border CARD_BORDER = BorderFactory.createLineBorder(UI_CARD_BORDER_SUBTLE);
	public static final Border STRONG_CARD_BORDER = BorderFactory.createLineBorder(UI_CARD_BORDER_STRONG);
	public static final Border SCROLL_BORDER = CARD_BORDER;

	public static Border paddedLineBorder(Color color, int padding) {
		return BorderFactory.createCompoundBorder(
				BorderFactory.createLineBorder(color),
				new EmptyBorder(padding, padding, padding, padding)
		);
	}

	public static Border lineBorder(Color color, int thickness) {
		return BorderFactory.createLineBorder(color, thickness);
	}
}
