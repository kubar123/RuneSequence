package com.lansoftprogramming.runeSequence.ui.theme;

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
			case DIALOG -> PANEL_DIALOG_SPEC;
		};
	}

	@Override
	public Insets getPanelPadding(PanelStyle style) {
		Objects.requireNonNull(style, "style");
		Insets padding = switch (style) {
			case DETAIL -> PANEL_DETAIL_PADDING;
			case DETAIL_HEADER -> PANEL_DETAIL_HEADER_PADDING;
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
			case DIALOG -> imageLoader.loadImage(PANEL_DIALOG_BORDER_IMAGE);
		};
	}

	private BufferedImage createPanelBackgroundImage(PanelStyle style) {
		return switch (style) {
			case DETAIL -> imageLoader.loadImage(PANEL_DETAIL_BACKGROUND_IMAGE);
			case DETAIL_HEADER -> imageLoader.loadImage(PANEL_DETAIL_HEADER_BACKGROUND_IMAGE);
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
}
