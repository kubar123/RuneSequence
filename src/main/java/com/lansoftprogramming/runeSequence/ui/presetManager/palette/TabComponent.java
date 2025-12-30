package com.lansoftprogramming.runeSequence.ui.presetManager.palette;

import com.lansoftprogramming.runeSequence.ui.theme.*;

import javax.swing.*;
import java.awt.*;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.awt.event.MouseMotionAdapter;
import java.awt.image.BufferedImage;
import java.util.*;

final class TabComponent extends JComponent {
	private static final int MIN_TEXT_WIDTH_PX = 24;
	private static final ButtonStyle TAB_STYLE = ButtonStyle.DEFAULT;
	private static final float ACTIVE_DARKEN_ALPHA_FLOOR = 0.08f;
	private static final float ACTIVE_DARKEN_ALPHA_CEIL = 0.22f;

	private final TabBar owner;
	private final int index;
	private String title;
	private boolean hovered;
	private boolean pressed;
	private boolean selected;
	private java.awt.Insets padding;
	private int fixedHeightPx;
	private Theme lastTheme;
	private NineSliceSpec tabAssetSpec;
	private final EnumMap<TabVisualState, SizeCache> backgroundCache;
	private final EnumMap<TabVisualState, BufferedImage> stateBaseCache;
	private BufferedImage tabBaseImage;
	private BufferedImage tabActiveImage;
	private boolean tabBaseFromAsset;
	private boolean tabActiveFromAsset;
	private float inferredHoverAlpha = 0.08f;
	private float inferredPressedAlpha = 0.14f;
	private float inferredDisabledAlpha = 0.55f;

	private record InferredButtonTreatment(float hoverAlpha, float pressedAlpha, float disabledAlpha) {
	}

	private static final Map<Theme, InferredButtonTreatment> TREATMENT_CACHE =
			Collections.synchronizedMap(new WeakHashMap<>());

	TabComponent(TabBar owner, String title, int index) {
		this.owner = Objects.requireNonNull(owner, "owner");
		this.title = Objects.requireNonNull(title, "title");
		this.index = index;
		this.backgroundCache = new EnumMap<>(TabVisualState.class);
		for (TabVisualState state : TabVisualState.values()) {
			backgroundCache.put(state, new SizeCache());
		}
		this.stateBaseCache = new EnumMap<>(TabVisualState.class);

		setOpaque(false);
		setBorder(javax.swing.BorderFactory.createEmptyBorder());
		setFocusable(false);
		updateCursor();

		refreshThemeMetrics();
		installMouseHandlers();
	}

	void setTitle(String title) {
		String next = title != null ? title : "";
		if (Objects.equals(this.title, next)) {
			return;
		}
		this.title = next;
		revalidate();
		repaint();
	}

	void setSelected(boolean selected) {
		if (this.selected == selected) {
			return;
		}
		this.selected = selected;
		pressed = false;
		hovered = false;
		updateCursor();
		repaint();
	}

	void refreshThemeMetrics() {
		Theme theme = ThemeManager.getTheme();
		if (lastTheme != theme) {
			lastTheme = theme;
			clearCache();
			stateBaseCache.clear();
			tabBaseImage = null;
			tabActiveImage = null;
			tabBaseFromAsset = false;
			tabActiveFromAsset = false;
			tabAssetSpec = null;
		}

		java.awt.Insets nextPadding = new java.awt.Insets(4, 10, 4, 10);
		int nextHeight = 28;
		try {
			java.awt.Insets themePadding = theme.getButtonPadding(TAB_STYLE);
			if (themePadding != null) {
				nextPadding = themePadding;
			}
			tabAssetSpec = theme.getTabSpec();
			BufferedImage baseImage = getTabBaseImage(theme);
			if (baseImage != null && baseImage.getHeight() > 0) {
				nextHeight = baseImage.getHeight();
			}
			inferStateTreatmentFromButtons(theme);
		} catch (RuntimeException ignored) {
			// Fall back to reasonable defaults if theme lookup fails.
		}
		this.padding = new java.awt.Insets(nextPadding.top, nextPadding.left, nextPadding.bottom, nextPadding.right);
		this.fixedHeightPx = nextHeight;
	}

	@Override
	public Dimension getPreferredSize() {
		Font font = getFont();
		FontMetrics metrics = font != null ? getFontMetrics(font) : null;
		int textWidth = metrics != null ? metrics.stringWidth(title) : 0;
		int width = Math.max(MIN_TEXT_WIDTH_PX, textWidth) + padding.left + padding.right;
		width = Math.max(width, computeMinimumTabWidth());
		int heightFromText = (metrics != null ? metrics.getHeight() : 0) + padding.top + padding.bottom;
		int height = Math.max(fixedHeightPx, heightFromText);
		return new Dimension(width, height);
	}

	@Override
	public void invalidate() {
		super.invalidate();
		// Preferred size changes (font/title/theme) can shift dimensions; invalidate cached size variants.
		clearCache();
	}

	@Override
	protected void paintComponent(Graphics g) {
		Graphics2D g2 = g instanceof Graphics2D ? (Graphics2D) g.create() : null;
		if (g2 == null) {
			return;
		}
		try {
			g2.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BILINEAR);
			g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
			g2.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);

			int width = getWidth();
			int height = getHeight();
			if (width <= 0 || height <= 0) {
				return;
			}

			TabVisualState state = resolveState();
			BufferedImage background = getCachedBackground(state, width, height);
			if (background != null) {
				g2.drawImage(background, 0, 0, null);
			}

			Graphics2D textGraphics = (Graphics2D) g2.create();
			try {
				if (shouldOffsetPressedText(state)) {
					textGraphics.translate(0, 1);
				}
				paintCenteredText(textGraphics, width, height);
			} finally {
				textGraphics.dispose();
			}
		} finally {
			g2.dispose();
		}
	}

	private void paintCenteredText(Graphics2D g2, int width, int height) {
		Font font = getFont();
		if (font != null) {
			g2.setFont(font);
		}
		FontMetrics metrics = g2.getFontMetrics();
		if (metrics == null) {
			return;
		}

		String text = title != null ? title : "";
		int textWidth = metrics.stringWidth(text);
		int textX = (width - textWidth) / 2;
		int textY = (height - metrics.getHeight()) / 2 + metrics.getAscent();

		Color textColor = resolveTextColor();
		if (textColor != null) {
			g2.setColor(textColor);
		}
		g2.drawString(text, textX, textY);
	}

	private Color resolveTextColor() {
		Theme theme = ThemeManager.getTheme();
		Color enabledColor = theme != null ? theme.getTextPrimaryColor() : UiColorPalette.UI_TEXT_COLOR;
		Color disabledColor = theme != null ? theme.getTextMutedColor() : UiColorPalette.TEXT_MUTED;
		if (!isEnabled()) {
			Color foreground = getForeground();
			if (foreground != null) {
				return foreground.darker();
			}
			return disabledColor;
		}
		return getForeground() != null ? getForeground() : enabledColor;
	}

	private TabVisualState resolveState() {
		if (!isEnabled()) {
			return TabVisualState.DISABLED;
		}
		if (selected) {
			return TabVisualState.ACTIVE;
		}
		if (pressed) {
			return TabVisualState.PRESSED;
		}
		if (hovered) {
			return TabVisualState.HOVER;
		}
		return TabVisualState.NORMAL;
	}

	private static boolean shouldOffsetPressedText(TabVisualState state) {
		return state == TabVisualState.PRESSED;
	}

	private BufferedImage getCachedBackground(TabVisualState state, int width, int height) {
		Theme theme = ThemeManager.getTheme();
		if (theme == null) {
			return null;
		}

		SizeCache sizeCache = backgroundCache.get(state);
		if (sizeCache == null) {
			return null;
		}
		BufferedImage cached = sizeCache.get(width, height);
		if (cached != null) {
			return cached;
		}

		BufferedImage rendered = renderBackground(theme, state, width, height);
		if (rendered != null) {
			sizeCache.put(width, height, rendered);
		}
		return rendered;
	}

	private BufferedImage renderBackground(Theme theme, TabVisualState state, int width, int height) {
		BufferedImage stateImage;
		NineSliceSpec spec;
		try {
			// Load images first so asset-detection flags are up to date before choosing a 9-slice spec.
			stateImage = getTabStateBaseImage(theme, state);
			spec = resolveTabSpec(theme, state);
		} catch (RuntimeException e) {
			return null;
		}

		if (stateImage == null || spec == null) {
			return null;
		}

		BufferedImage buffer = new BufferedImage(width, height, BufferedImage.TYPE_INT_ARGB);
		Graphics2D g2 = buffer.createGraphics();
		try {
			g2.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BILINEAR);
			ThemeRenderers.paintNineSlice(g2, stateImage, spec, 0, 0, width, height);
		} finally {
			g2.dispose();
		}
		return buffer;
	}

	private BufferedImage getTabBaseImage(Theme theme) {
		if (tabBaseImage != null) {
			return tabBaseImage;
		}
		if (theme == null) {
			return null;
		}

		try {
			BufferedImage loaded = theme.getTabBaseImage();
			if (loaded != null) {
				tabBaseImage = loaded;
				tabBaseFromAsset = true;
				return tabBaseImage;
			}
		} catch (RuntimeException ignored) {
			// Fall through to button fallback.
		}

		// Fallback: render tabs with the themed button base if a tab asset is not provided.
		try {
			tabBaseImage = theme.getButtonBaseImage(TAB_STYLE);
			tabBaseFromAsset = false;
		} catch (RuntimeException ignored) {
			tabBaseImage = null;
			tabBaseFromAsset = false;
		}
		return tabBaseImage;
	}

	private BufferedImage getTabActiveImage(Theme theme) {
		if (tabActiveImage != null) {
			return tabActiveImage;
		}
		if (theme == null) {
			return null;
		}

		try {
			BufferedImage loaded = theme.getTabActiveImage();
			if (loaded != null) {
				tabActiveImage = loaded;
				tabActiveFromAsset = true;
				return tabActiveImage;
			}
		} catch (RuntimeException ignored) {
			// Fall through to base fallback.
		}

		tabActiveImage = getTabBaseImage(theme);
		tabActiveFromAsset = tabBaseFromAsset;
		return tabActiveImage;
	}

	private BufferedImage getTabStateBaseImage(Theme theme, TabVisualState state) {
		BufferedImage cached = stateBaseCache.get(state);
		if (cached != null) {
			return cached;
		}

		BufferedImage base = getTabBaseImage(theme);
		BufferedImage activeBase = getTabActiveImage(theme);
		if (base == null) {
			base = activeBase;
		}
		if (base == null && activeBase == null) {
			return null;
		}

		BufferedImage derived = switch (state) {
			case NORMAL -> base;
			case HOVER -> ThemeRenderers.applyOverlay(base, UiColorPalette.BASE_WHITE, inferredHoverAlpha);
			case PRESSED -> ThemeRenderers.applyOverlay(base, UiColorPalette.BASE_BLACK, inferredPressedAlpha);
			case ACTIVE -> applyActiveTreatment(activeBase != null ? activeBase : base);
			case DISABLED -> ThemeRenderers.toDisabled(base, inferredDisabledAlpha);
		};

		stateBaseCache.put(state, derived);
		return derived;
	}

	private NineSliceSpec resolveTabSpec(Theme theme, TabVisualState state) {
		if (tabAssetSpec != null && state == TabVisualState.ACTIVE && tabActiveFromAsset) {
			return tabAssetSpec;
		}
		if (tabAssetSpec != null && state != TabVisualState.ACTIVE && tabBaseFromAsset) {
			return tabAssetSpec;
		}
		return theme.getButtonSpec(TAB_STYLE);
	}

	private BufferedImage applyActiveTreatment(BufferedImage source) {
		float alpha = Math.max(ACTIVE_DARKEN_ALPHA_FLOOR, inferredPressedAlpha * 0.9f);
		alpha = Math.min(ACTIVE_DARKEN_ALPHA_CEIL, alpha);
		return ThemeRenderers.applyOverlay(source, UiColorPalette.BASE_BLACK, alpha);
	}

	private int computeMinimumTabWidth() {
		if (tabAssetSpec != null && (tabBaseFromAsset || tabActiveFromAsset)) {
			// Ensure we never compress a tab below the sum of the non-stretchable 9-slice borders.
			// Otherwise, the slanted top edge/border gets clipped or distorted (especially for short titles).
			return tabAssetSpec.left() + tabAssetSpec.right() + 1;
		}
		return 0;
	}

	private void inferStateTreatmentFromButtons(Theme theme) {
		if (theme == null) {
			return;
		}

		InferredButtonTreatment cached = TREATMENT_CACHE.get(theme);
		if (cached != null) {
			applyInferredTreatment(cached);
			return;
		}

		InferredButtonTreatment inferred = inferButtonTreatment(theme);
		if (inferred == null) {
			return;
		}
		TREATMENT_CACHE.put(theme, inferred);
		applyInferredTreatment(inferred);
	}

	private void applyInferredTreatment(InferredButtonTreatment treatment) {
		if (treatment == null) {
			return;
		}
		boolean changed = false;

		if (Float.compare(inferredHoverAlpha, treatment.hoverAlpha()) != 0) {
			inferredHoverAlpha = treatment.hoverAlpha();
			changed = true;
		}
		if (Float.compare(inferredPressedAlpha, treatment.pressedAlpha()) != 0) {
			inferredPressedAlpha = treatment.pressedAlpha();
			changed = true;
		}
		if (Float.compare(inferredDisabledAlpha, treatment.disabledAlpha()) != 0) {
			inferredDisabledAlpha = treatment.disabledAlpha();
			changed = true;
		}

		if (changed) {
			stateBaseCache.clear();
		}
	}

	private static InferredButtonTreatment inferButtonTreatment(Theme theme) {
		// Infer overlay alphas from the themed button states so tab overlays match the active theme conventions.
		BufferedImage buttonBase;
		BufferedImage buttonHover;
		BufferedImage buttonPressed;
		BufferedImage buttonDisabled;
		try {
			buttonBase = theme.getButtonImage(TAB_STYLE, ButtonVisualState.NORMAL);
			buttonHover = theme.getButtonImage(TAB_STYLE, ButtonVisualState.HOVER);
			buttonPressed = theme.getButtonImage(TAB_STYLE, ButtonVisualState.PRESSED);
			buttonDisabled = theme.getButtonImage(TAB_STYLE, ButtonVisualState.DISABLED);
		} catch (RuntimeException ignored) {
			return null;
		}

		if (buttonBase == null) {
			return null;
		}

		float hoverAlpha = 0.08f;
		float pressedAlpha = 0.14f;
		float disabledAlpha = 0.55f;

		if (buttonHover != null && sameDimensions(buttonBase, buttonHover)) {
			Float alpha = inferOverlayAlpha(buttonBase, buttonHover, UiColorPalette.BASE_WHITE);
			if (alpha != null) {
				hoverAlpha = alpha;
			}
		}
		if (buttonPressed != null && sameDimensions(buttonBase, buttonPressed)) {
			Float alpha = inferOverlayAlpha(buttonBase, buttonPressed, UiColorPalette.BASE_BLACK);
			if (alpha != null) {
				pressedAlpha = alpha;
			}
		}
		if (buttonDisabled != null && sameDimensions(buttonBase, buttonDisabled)) {
			Float alpha = inferDisabledAlpha(buttonBase, buttonDisabled);
			if (alpha != null) {
				disabledAlpha = alpha;
			}
		}

		return new InferredButtonTreatment(hoverAlpha, pressedAlpha, disabledAlpha);
	}

	private static boolean sameDimensions(BufferedImage a, BufferedImage b) {
		return a != null && b != null && a.getWidth() == b.getWidth() && a.getHeight() == b.getHeight();
	}

	private static Float inferOverlayAlpha(BufferedImage base, BufferedImage target, Color overlay) {
		if (base == null || target == null || overlay == null) {
			return null;
		}

		int width = base.getWidth();
		int height = base.getHeight();
		if (width <= 0 || height <= 0 || target.getWidth() != width || target.getHeight() != height) {
			return null;
		}

		int overlayRgb = overlay.getRGB();
		int or = (overlayRgb >> 16) & 0xFF;
		int og = (overlayRgb >> 8) & 0xFF;
		int ob = overlayRgb & 0xFF;

		float[] samples = new float[96];
		int sampleCount = 0;

		int stepX = Math.max(1, width / 32);
		int stepY = Math.max(1, height / 32);

		for (int y = 0; y < height && sampleCount < samples.length; y += stepY) {
			for (int x = 0; x < width && sampleCount < samples.length; x += stepX) {
				int baseArgb = base.getRGB(x, y);
				int baseA = (baseArgb >> 24) & 0xFF;
				if (baseA == 0) {
					continue;
				}

				int targetArgb = target.getRGB(x, y);

				int br = (baseArgb >> 16) & 0xFF;
				int bg = (baseArgb >> 8) & 0xFF;
				int bb = baseArgb & 0xFF;

				int tr = (targetArgb >> 16) & 0xFF;
				int tg = (targetArgb >> 8) & 0xFF;
				int tb = targetArgb & 0xFF;

				Float aR = solveAlpha(br, tr, or);
				Float aG = solveAlpha(bg, tg, og);
				Float aB = solveAlpha(bb, tb, ob);

				float alpha = averageNonNull(aR, aG, aB);
				if (alpha > 0f) {
					samples[sampleCount++] = alpha;
				}
			}
		}

		if (sampleCount < 8) {
			return null;
		}
		Arrays.sort(samples, 0, sampleCount);
		float median = samples[sampleCount / 2];
		if (median <= 0f || median > 1f) {
			return null;
		}
		return median;
	}

	private static Float solveAlpha(int base, int target, int overlay) {
		int denom = overlay - base;
		int num = target - base;
		if (denom == 0) {
			return null;
		}
		float a = (float) num / (float) denom;
		if (Float.isNaN(a) || Float.isInfinite(a)) {
			return null;
		}
		// Clamp; allow some tolerance for compression/rounding noise.
		float clamped = Math.max(0f, Math.min(1f, a));
		if (Math.abs(a - clamped) > 0.12f) {
			return null;
		}
		return clamped;
	}

	private static float averageNonNull(Float a, Float b, Float c) {
		float sum = 0f;
		int count = 0;
		if (a != null) {
			sum += a;
			count++;
		}
		if (b != null) {
			sum += b;
			count++;
		}
		if (c != null) {
			sum += c;
			count++;
		}
		return count == 0 ? 0f : (sum / count);
	}

	private static Float inferDisabledAlpha(BufferedImage base, BufferedImage disabled) {
		if (base == null || disabled == null) {
			return null;
		}
		int width = base.getWidth();
		int height = base.getHeight();
		if (width <= 0 || height <= 0 || disabled.getWidth() != width || disabled.getHeight() != height) {
			return null;
		}

		float[] samples = new float[96];
		int sampleCount = 0;

		int stepX = Math.max(1, width / 32);
		int stepY = Math.max(1, height / 32);

		for (int y = 0; y < height && sampleCount < samples.length; y += stepY) {
			for (int x = 0; x < width && sampleCount < samples.length; x += stepX) {
				int baseArgb = base.getRGB(x, y);
				int baseA = (baseArgb >> 24) & 0xFF;
				if (baseA == 0) {
					continue;
				}
				int disabledArgb = disabled.getRGB(x, y);
				int disabledA = (disabledArgb >> 24) & 0xFF;
				float ratio = (float) disabledA / (float) baseA;
				if (ratio >= 0f && ratio <= 1f) {
					samples[sampleCount++] = ratio;
				}
			}
		}

		if (sampleCount < 8) {
			return null;
		}
		Arrays.sort(samples, 0, sampleCount);
		return samples[sampleCount / 2];
	}

	private void clearCache() {
		for (SizeCache cache : backgroundCache.values()) {
			cache.clear();
		}
	}

	private void installMouseHandlers() {
		addMouseListener(new MouseAdapter() {
			@Override
			public void mouseEntered(MouseEvent e) {
				if (!isEnabled() || selected) {
					return;
				}
				hovered = true;
				repaint();
			}

			@Override
			public void mouseExited(MouseEvent e) {
				hovered = false;
				pressed = false;
				repaint();
			}

			@Override
			public void mousePressed(MouseEvent e) {
				if (!isEnabled() || selected) {
					return;
				}
				if (e.getButton() != MouseEvent.BUTTON1) {
					return;
				}
				pressed = true;
				repaint();
			}

			@Override
			public void mouseReleased(MouseEvent e) {
				if (e.getButton() != MouseEvent.BUTTON1) {
					pressed = false;
					repaint();
					return;
				}

				boolean shouldSelect = isEnabled() && !selected && pressed && contains(e.getPoint());
				pressed = false;
				repaint();

				if (shouldSelect) {
					owner.setSelectedIndex(index);
				}
			}
		});
		addMouseMotionListener(new MouseMotionAdapter() {
			@Override
			public void mouseMoved(MouseEvent e) {
				updateCursor();
			}
		});
	}

	private void updateCursor() {
		setCursor(isEnabled() && !selected
				? Cursor.getPredefinedCursor(Cursor.HAND_CURSOR)
				: Cursor.getDefaultCursor());
	}

	private static final class SizeCache {
		private int width = -1;
		private int height = -1;
		private BufferedImage image;

		private BufferedImage get(int width, int height) {
			if (image == null || this.width != width || this.height != height) {
				return null;
			}
			return image;
		}

		private void put(int width, int height, BufferedImage image) {
			this.width = width;
			this.height = height;
			this.image = image;
		}

		private void clear() {
			width = -1;
			height = -1;
			image = null;
		}
	}
}
