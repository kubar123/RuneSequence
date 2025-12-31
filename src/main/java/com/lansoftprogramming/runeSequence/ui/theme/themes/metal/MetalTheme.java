package com.lansoftprogramming.runeSequence.ui.theme.themes.metal;

import com.lansoftprogramming.runeSequence.ui.theme.*;

import java.awt.*;
import java.awt.image.BufferedImage;
import java.util.EnumMap;
import java.util.Map;
import java.util.Objects;

public final class MetalTheme implements Theme {

	private static final String NAME = "metal";

	private static final String BUTTON_DEFAULT_IMAGE = "/ui/metal/Btn_Default.png";
	private static final NineSliceSpec BUTTON_DEFAULT_SPEC = new NineSliceSpec(4, 5, 10, 5);
	private static final Insets BUTTON_DEFAULT_PADDING = new Insets(5, 12, 5, 12);

	private static final String TEXTBOX_DEFAULT_IMAGE = "/ui/metal/Txtbox.png";
	private static final NineSliceSpec TEXTBOX_DEFAULT_SPEC = new NineSliceSpec(4, 3, 4, 3);
	private static final Insets TEXTBOX_DEFAULT_PADDING = new Insets(4, 4, 3, 3);

	private static final String PANEL_DETAIL_BORDER_IMAGE = "/ui/metal/Panel_Gold_Border.png";
	private static final String PANEL_DETAIL_BACKGROUND_IMAGE = "/ui/metal/background.png";
	private static final NineSliceSpec PANEL_DETAIL_SPEC = new NineSliceSpec(13, 14, 9, 12);
	private static final Insets PANEL_DETAIL_PADDING = new Insets(10, 10, 10, 10);

	private static final String PANEL_DETAIL_HEADER_BACKGROUND_IMAGE = "/ui/metal/Top_Bar.png";
	private static final NineSliceSpec PANEL_DETAIL_HEADER_SPEC = new NineSliceSpec(0, 0, 0, 0);
	private static final Insets PANEL_DETAIL_HEADER_PADDING = new Insets(0, 0, 0, 0);

	private static final String PANEL_DIALOG_BORDER_IMAGE = "/ui/metal/Panel_Gold_Border.png";
	private static final String PANEL_DIALOG_BACKGROUND_IMAGE = "/ui/metal/Panel_Gold_Mid.png";
	private static final NineSliceSpec PANEL_DIALOG_SPEC = new NineSliceSpec(13, 14, 9, 12);
	private static final Insets PANEL_DIALOG_PADDING = new Insets(9, 13, 12, 14);

	private static final String DIALOG_DIVIDER_IMAGE = "/ui/metal/Divider2.png";
	private static final String DIALOG_TITLE_FONT = "/fonts/Cinzel-VariableFont_wght.ttf";

	private static final String TAB_BASE_IMAGE = "/ui/metal/tabs/4.png";
	private static final String TAB_ACTIVE_IMAGE = "/ui/metal/tabs/5.png";
	private static final String TAB_OPENED_MARKER_IMAGE = "/ui/metal/tabs/Tab_Opened_Marker.png";
	private static final NineSliceSpec TAB_SPEC = new NineSliceSpec(45, 11, 2, 2);
	private static final int TAB_OPENED_MARKER_ANCHOR_FROM_BOTTOM_PX = 11;

	private static final String PANEL_TAB_CONTENT_BORDER_IMAGE = "/ui/metal/tabs/Tab_Panel_Border.png";
	private static final String PANEL_TAB_CONTENT_BACKGROUND_IMAGE = "/ui/metal/tabs/Tab_Panel.png";
	private static final NineSliceSpec PANEL_TAB_CONTENT_SPEC = new NineSliceSpec(1, 1, 1, 1);
	private static final Insets PANEL_TAB_CONTENT_PADDING = new Insets(1, 1, 1, 1);

	private static final ButtonStateTreatment BUTTON_TREATMENT = new ButtonStateTreatment(
			UiColorPalette.BASE_WHITE, 0.08f,
			UiColorPalette.BASE_BLACK, 0.14f,
			0.55f
	);

	private final ThemeImageLoader imageLoader = new ThemeImageLoader(MetalTheme.class);
	private final ThemeFontLoader fontLoader = new ThemeFontLoader(MetalTheme.class);
	private final Map<ButtonStyle, ButtonImageSet> buttonImages = new EnumMap<>(ButtonStyle.class);
	private final Map<TextBoxStyle, BufferedImage> textBoxImages = new EnumMap<>(TextBoxStyle.class);
	private final Map<PanelStyle, BufferedImage> panelBorderImages = new EnumMap<>(PanelStyle.class);
	private final Map<PanelStyle, BufferedImage> panelBackgroundImages = new EnumMap<>(PanelStyle.class);
	private final Map<DialogStyle, BufferedImage> dialogDividerImages = new EnumMap<>(DialogStyle.class);
	private Font dialogTitleBaseFont;
	private BufferedImage tabBaseImage;
	private BufferedImage tabActiveImage;
	private BufferedImage tabOpenedMarkerImage;
	private Integer tabOverlapPx;

	private static Color blend(Color a, Color b, float bWeight) {
		if (a == null && b == null) {
			return null;
		}
		if (a == null) {
			return b;
		}
		if (b == null) {
			return a;
		}

		float clamped = Math.max(0f, Math.min(1f, bWeight));
		float aWeight = 1f - clamped;

		int r = Math.round(a.getRed() * aWeight + b.getRed() * clamped);
		int g = Math.round(a.getGreen() * aWeight + b.getGreen() * clamped);
		int bCh = Math.round(a.getBlue() * aWeight + b.getBlue() * clamped);
		int alpha = Math.round(a.getAlpha() * aWeight + b.getAlpha() * clamped);
		return new Color(
				Math.max(0, Math.min(255, r)),
				Math.max(0, Math.min(255, g)),
				Math.max(0, Math.min(255, bCh)),
				Math.max(0, Math.min(255, alpha))
		);
	}

	@Override
	public String getName() {
		return NAME;
	}

	@Override
	public NineSliceSpec getButtonSpec(ButtonStyle style) {
		Objects.requireNonNull(style, "style");
		return switch (style) {
			case DEFAULT -> BUTTON_DEFAULT_SPEC;
		};
	}

	@Override
	public Insets getButtonPadding(ButtonStyle style) {
		Objects.requireNonNull(style, "style");
		Insets padding = switch (style) {
			case DEFAULT -> BUTTON_DEFAULT_PADDING;
		};
		return new Insets(padding.top, padding.left, padding.bottom, padding.right);
	}

	@Override
	public BufferedImage getButtonBaseImage(ButtonStyle style) {
		Objects.requireNonNull(style, "style");
		return getButtonImageSet(style).get(ButtonVisualState.NORMAL);
	}

	@Override
	public BufferedImage getButtonImage(ButtonStyle style, ButtonVisualState state) {
		Objects.requireNonNull(style, "style");
		Objects.requireNonNull(state, "state");
		return getButtonImageSet(style).get(state);
	}

	@Override
	public NineSliceSpec getTextBoxSpec(TextBoxStyle style) {
		Objects.requireNonNull(style, "style");
		return switch (style) {
			case DEFAULT -> TEXTBOX_DEFAULT_SPEC;
		};
	}

	@Override
	public Insets getTextBoxPadding(TextBoxStyle style) {
		Objects.requireNonNull(style, "style");
		Insets padding = switch (style) {
			case DEFAULT -> TEXTBOX_DEFAULT_PADDING;
		};
		return new Insets(padding.top, padding.left, padding.bottom, padding.right);
	}

	@Override
	public BufferedImage getTextBoxImage(TextBoxStyle style) {
		Objects.requireNonNull(style, "style");
		return textBoxImages.computeIfAbsent(style, this::createTextBoxImage);
	}

	@Override
	public NineSliceSpec getPanelSpec(PanelStyle style) {
		Objects.requireNonNull(style, "style");
		return switch (style) {
			case DETAIL -> PANEL_DETAIL_SPEC;
			case DETAIL_HEADER -> PANEL_DETAIL_HEADER_SPEC;
			case TAB_CONTENT -> PANEL_TAB_CONTENT_SPEC;
			case DIALOG -> PANEL_DIALOG_SPEC;
		};
	}

	@Override
	public Insets getPanelPadding(PanelStyle style) {
		Objects.requireNonNull(style, "style");
		Insets padding = switch (style) {
			case DETAIL -> PANEL_DETAIL_PADDING;
			case DETAIL_HEADER -> PANEL_DETAIL_HEADER_PADDING;
			case TAB_CONTENT -> PANEL_TAB_CONTENT_PADDING;
			case DIALOG -> PANEL_DIALOG_PADDING;
		};
		return new Insets(padding.top, padding.left, padding.bottom, padding.right);
	}

	@Override
	public BufferedImage getPanelBorderImage(PanelStyle style) {
		Objects.requireNonNull(style, "style");
		return panelBorderImages.computeIfAbsent(style, this::createPanelBorderImage);
	}

	@Override
	public BufferedImage getPanelBackgroundImage(PanelStyle style) {
		Objects.requireNonNull(style, "style");
		return panelBackgroundImages.computeIfAbsent(style, this::createPanelBackgroundImage);
	}

	@Override
	public BufferedImage getDialogDividerImage(DialogStyle style) {
		Objects.requireNonNull(style, "style");
		return dialogDividerImages.computeIfAbsent(style, this::createDialogDividerImage);
	}

	@Override
	public Font getDialogTitleFont(float size) {
		float clampedSize = size > 0f ? size : 40f;
		Font base = getDialogTitleBaseFont();
		if (base == null) {
			return new Font(Font.SERIF, Font.BOLD, Math.round(clampedSize));
		}
		return base.deriveFont(Font.PLAIN, clampedSize);
	}

	@Override
	public BufferedImage getTabBaseImage() {
		if (tabBaseImage == null) {
			tabBaseImage = imageLoader.loadImage(TAB_BASE_IMAGE);
		}
		return tabBaseImage;
	}

	@Override
	public BufferedImage getTabActiveImage() {
		if (tabActiveImage == null) {
			tabActiveImage = imageLoader.loadImage(TAB_ACTIVE_IMAGE);
		}
		return tabActiveImage;
	}

	@Override
	public BufferedImage getTabContentBackgroundImage() {
		return getPanelBackgroundImage(PanelStyle.TAB_CONTENT);
	}

	@Override
	public NineSliceSpec getTabSpec() {
		return TAB_SPEC;
	}

	@Override
	public int getTabInterTabOverlapPx() {
		if (tabOverlapPx == null) {
			tabOverlapPx = computeInterTabOverlapPx(getTabBaseImage(), getTabActiveImage());
		}
		return tabOverlapPx;
	}

	@Override
	public BufferedImage getTabOpenedMarkerImage() {
		if (tabOpenedMarkerImage == null) {
			tabOpenedMarkerImage = imageLoader.loadImage(TAB_OPENED_MARKER_IMAGE);
		}
		return tabOpenedMarkerImage;
	}

	@Override
	public int getTabOpenedMarkerAnchorFromBottomPx() {
		return TAB_OPENED_MARKER_ANCHOR_FROM_BOTTOM_PX;
	}

	@Override
	public Color getTextPrimaryColor() {
		return UiColorPalette.UI_TEXT_COLOR;
	}

	@Override
	public Color getTextMutedColor() {
		return UiColorPalette.DIALOG_MESSAGE_TEXT;
	}

	@Override
	public Color getAccentPrimaryColor() {
		return UiColorPalette.TOAST_INFO_ACCENT;
	}

	@Override
	public Color getAccentHoverColor() {
		return blend(getAccentPrimaryColor(), UiColorPalette.BASE_WHITE, 0.18f);
	}

	@Override
	public Color getInsetBorderColor() {
		return UiColorPalette.withAlpha(UiColorPalette.BASE_BLACK, 150);
	}

	@Override
	public Color getWindowTitleBarBackground() {
		return UiColorPalette.UI_CARD_BACKGROUND;
	}

	@Override
	public Color getWindowTitleBarForeground() {
		return getTextPrimaryColor();
	}

	@Override
	public Color getWindowTitleBarInactiveBackground() {
		return blend(UiColorPalette.UI_CARD_BACKGROUND, UiColorPalette.BASE_BLACK, 0.22f);
	}

	@Override
	public Color getWindowTitleBarInactiveForeground() {
		return getTextMutedColor();
	}

	private ButtonImageSet getButtonImageSet(ButtonStyle style) {
		return buttonImages.computeIfAbsent(style, this::createButtonImageSet);
	}

	private ButtonImageSet createButtonImageSet(ButtonStyle style) {
		BufferedImage base = switch (style) {
			case DEFAULT -> imageLoader.loadImage(BUTTON_DEFAULT_IMAGE);
		};
		return ButtonImageSet.fromBase(base, BUTTON_TREATMENT);
	}

	private BufferedImage createTextBoxImage(TextBoxStyle style) {
		return switch (style) {
			case DEFAULT -> imageLoader.loadImage(TEXTBOX_DEFAULT_IMAGE);
		};
	}

	private BufferedImage createPanelBorderImage(PanelStyle style) {
		return switch (style) {
			case DETAIL -> imageLoader.loadImage(PANEL_DETAIL_BORDER_IMAGE);
			case DETAIL_HEADER -> null;
			case TAB_CONTENT -> imageLoader.loadImage(PANEL_TAB_CONTENT_BORDER_IMAGE);
			case DIALOG -> imageLoader.loadImage(PANEL_DIALOG_BORDER_IMAGE);
		};
	}

	private BufferedImage createPanelBackgroundImage(PanelStyle style) {
		return switch (style) {
			case DETAIL -> imageLoader.loadImage(PANEL_DETAIL_BACKGROUND_IMAGE);
			case DETAIL_HEADER -> imageLoader.loadImage(PANEL_DETAIL_HEADER_BACKGROUND_IMAGE);
			case TAB_CONTENT -> imageLoader.loadImage(PANEL_TAB_CONTENT_BACKGROUND_IMAGE);
			case DIALOG -> imageLoader.loadImage(PANEL_DIALOG_BACKGROUND_IMAGE);
		};
	}

	private BufferedImage createDialogDividerImage(DialogStyle style) {
		return switch (style) {
			case DEFAULT -> imageLoader.loadImage(DIALOG_DIVIDER_IMAGE);
		};
	}

	private Font getDialogTitleBaseFont() {
		if (dialogTitleBaseFont == null) {
			try {
				dialogTitleBaseFont = fontLoader.loadFont(DIALOG_TITLE_FONT);
			} catch (RuntimeException ex) {
				dialogTitleBaseFont = null;
			}
		}
		return dialogTitleBaseFont;
	}

	private static int computeInterTabOverlapPx(BufferedImage inactive, BufferedImage active) {
		if (inactive == null && active == null) {
			return 0;
		}

		// Tabs are slanted: there may be no fully-transparent columns, but still a diagonal gap per scanline.
		// Compute the maximum transparent margin sum (left + right) across all rows; that overlap removes seams.
		// Be conservative: an overlap that works for the "most transparent" tab can slice into the visible
		// border of a less-transparent variant (e.g. active tabs with a gold outline). Since the selected tab
		// is also drawn on top, prefer the smallest computed overlap to preserve borders.
		int overlap = Integer.MAX_VALUE;
		int maxOverlap = 0;
		if (inactive != null) {
			overlap = Math.min(overlap, computeMaxRowTransparentMarginSum(inactive));
			maxOverlap = Math.max(maxOverlap, inactive.getWidth() - 1);
		}
		if (active != null) {
			overlap = Math.min(overlap, computeMaxRowTransparentMarginSum(active));
			maxOverlap = Math.max(maxOverlap, active.getWidth() - 1);
		}
		if (overlap == Integer.MAX_VALUE) {
			return 0;
		}
		return Math.max(0, Math.min(maxOverlap, overlap));
	}

	private static int computeMaxRowTransparentMarginSum(BufferedImage image) {
		int w = image.getWidth();
		int h = image.getHeight();
		int max = 0;

		for (int y = 0; y < h; y++) {
			int leftMost = -1;
			int rightMost = -1;

			for (int x = 0; x < w; x++) {
				int a = (image.getRGB(x, y) >>> 24) & 0xFF;
				if (a == 0) {
					continue;
				}
				leftMost = x;
				break;
			}
			if (leftMost < 0) {
				continue;
			}
			for (int x = w - 1; x >= 0; x--) {
				int a = (image.getRGB(x, y) >>> 24) & 0xFF;
				if (a == 0) {
					continue;
				}
				rightMost = x;
				break;
			}
			if (rightMost < 0) {
				continue;
			}

			int leftTransparent = leftMost;
			int rightTransparent = (w - 1) - rightMost;
			max = Math.max(max, leftTransparent + rightTransparent);
		}

		return max;
	}
}
