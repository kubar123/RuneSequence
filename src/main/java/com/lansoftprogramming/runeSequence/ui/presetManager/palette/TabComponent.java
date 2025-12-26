package com.lansoftprogramming.runeSequence.ui.presetManager.palette;

import com.lansoftprogramming.runeSequence.ui.theme.*;

import javax.imageio.ImageIO;
import javax.swing.*;
import java.awt.*;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.awt.event.MouseMotionAdapter;
import java.awt.image.BufferedImage;
import java.io.IOException;
import java.io.InputStream;
import java.util.*;

final class TabComponent extends JComponent {
	private static final int MIN_TEXT_WIDTH_PX = 24;
	private static final ButtonStyle TAB_STYLE = ButtonStyle.DEFAULT;
	private static final String TAB_BASE_RESOURCE_SUFFIX = "/tabs/4.png";
	private static final String TAB_ACTIVE_RESOURCE_SUFFIX = "/tabs/5.png";
	private static final NineSliceSpec TAB_NINE_SLICE_SPEC = new NineSliceSpec(45, 11, 2, 2);
	private static final float ACTIVE_LIGHTEN_ALPHA_FLOOR = 0.08f;

	private final TabBar owner;
	private final int index;
	private String title;
	private boolean hovered;
	private boolean pressed;
	private boolean selected;
	private java.awt.Insets padding;
	private int fixedHeightPx;
	private String lastThemeName;
	private final EnumMap<TabVisualState, Map<Dimension, BufferedImage>> backgroundCache;
	private final EnumMap<TabVisualState, BufferedImage> stateBaseCache;
	private BufferedImage tabBaseImage;
	private BufferedImage tabActiveImage;
	private boolean tabBaseFromAsset;
	private boolean tabActiveFromAsset;
	private float inferredHoverAlpha = 0.08f;
	private float inferredPressedAlpha = 0.14f;
	private float inferredDisabledAlpha = 0.55f;

	TabComponent(TabBar owner, String title, int index) {
		this.owner = Objects.requireNonNull(owner, "owner");
		this.title = Objects.requireNonNull(title, "title");
		this.index = index;
		this.backgroundCache = new EnumMap<>(TabVisualState.class);
		for (TabVisualState state : TabVisualState.values()) {
			backgroundCache.put(state, new HashMap<>());
		}
		this.stateBaseCache = new EnumMap<>(TabVisualState.class);

		setOpaque(false);
		setBorder(javax.swing.BorderFactory.createEmptyBorder());
		setFocusable(false);
		setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));

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
		repaint();
	}

	void refreshThemeMetrics() {
		Theme theme = ThemeManager.getTheme();
		String themeName = theme != null ? theme.getName() : null;
		if (!Objects.equals(lastThemeName, themeName)) {
			lastThemeName = themeName;
			clearCache();
			stateBaseCache.clear();
			tabBaseImage = null;
			tabActiveImage = null;
			tabBaseFromAsset = false;
			tabActiveFromAsset = false;
		}

		java.awt.Insets nextPadding = new java.awt.Insets(4, 10, 4, 10);
		int nextHeight = 28;
		try {
			java.awt.Insets themePadding = theme.getButtonPadding(TAB_STYLE);
			if (themePadding != null) {
				nextPadding = themePadding;
			}
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
		Color uiColor = UIManager.getColor("Button.foreground");
		if (!isEnabled()) {
			Color disabled = UIManager.getColor("Button.disabledText");
			if (disabled == null) {
				disabled = UIManager.getColor("Label.disabledForeground");
			}
			if (disabled != null) {
				return disabled;
			}
			if (uiColor != null) {
				return uiColor.darker();
			}
			return getForeground() != null ? getForeground().darker() : UiColorPalette.TEXT_MUTED;
		}
		if (uiColor != null) {
			return uiColor;
		}
		return getForeground();
	}

	private TabVisualState resolveState() {
		if (!isEnabled()) {
			return TabVisualState.DISABLED;
		}
		if (pressed) {
			return TabVisualState.PRESSED;
		}
		if (selected) {
			return TabVisualState.ACTIVE;
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

		Map<Dimension, BufferedImage> sizeMap = backgroundCache.get(state);
		if (sizeMap == null) {
			return null;
		}
		Dimension key = new Dimension(width, height);
		BufferedImage cached = sizeMap.get(key);
		if (cached != null) {
			return cached;
		}

		BufferedImage rendered = renderBackground(theme, state, width, height);
		if (rendered != null) {
			sizeMap.put(key, rendered);
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
			paintNineSlice(g2, stateImage, spec, 0, 0, width, height);
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

		// Prefer the theme's tab asset (e.g., /ui/metal/tabs/4.png) without hardcoding a specific theme name.
		String themeName = theme.getName();
		if (themeName != null && !themeName.isBlank()) {
			String resourcePath = "/ui/" + themeName + TAB_BASE_RESOURCE_SUFFIX;
			try (InputStream input = theme.getClass().getResourceAsStream(resourcePath)) {
				if (input != null) {
					BufferedImage loaded = ImageIO.read(input);
					if (loaded != null) {
						tabBaseImage = loaded;
						tabBaseFromAsset = true;
						return tabBaseImage;
					}
				}
			} catch (IOException ignored) {
				// Fall through to button fallback.
			}
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

		String themeName = theme.getName();
		if (themeName != null && !themeName.isBlank()) {
			String resourcePath = "/ui/" + themeName + TAB_ACTIVE_RESOURCE_SUFFIX;
			try (InputStream input = theme.getClass().getResourceAsStream(resourcePath)) {
				if (input != null) {
					BufferedImage loaded = ImageIO.read(input);
					if (loaded != null) {
						tabActiveImage = loaded;
						tabActiveFromAsset = true;
						return tabActiveImage;
					}
				}
			} catch (IOException ignored) {
				// Fall through to base fallback.
			}
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
			case HOVER -> applyOverlay(base, UiColorPalette.BASE_WHITE, inferredHoverAlpha);
			case PRESSED -> applyOverlay(base, UiColorPalette.BASE_BLACK, inferredPressedAlpha);
			case ACTIVE -> applyActiveTreatment(activeBase != null ? activeBase : base);
			case DISABLED -> toDisabled(base, inferredDisabledAlpha);
		};

		stateBaseCache.put(state, derived);
		return derived;
	}

	private NineSliceSpec resolveTabSpec(Theme theme, TabVisualState state) {
		if (state == TabVisualState.ACTIVE && tabActiveFromAsset) {
			return TAB_NINE_SLICE_SPEC;
		}
		if (state != TabVisualState.ACTIVE && tabBaseFromAsset) {
			return TAB_NINE_SLICE_SPEC;
		}
		return theme.getButtonSpec(TAB_STYLE);
	}

	private BufferedImage applyActiveTreatment(BufferedImage source) {
		if (tabActiveFromAsset) {
			return source;
		}
		float alpha = Math.max(ACTIVE_LIGHTEN_ALPHA_FLOOR, inferredHoverAlpha);
		return applyOverlay(source, UiColorPalette.BASE_WHITE, alpha);
	}

	private int computeMinimumTabWidth() {
		if (tabBaseFromAsset || tabActiveFromAsset) {
			// Ensure we never compress a tab below the sum of the non-stretchable 9-slice borders.
			// Otherwise, the slanted top edge/border gets clipped or distorted (especially for short titles).
			return TAB_NINE_SLICE_SPEC.left() + TAB_NINE_SLICE_SPEC.right() + 1;
		}
		return 0;
	}

	private void inferStateTreatmentFromButtons(Theme theme) {
		if (theme == null) {
			return;
		}

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
			return;
		}

		if (buttonBase == null) {
			return;
		}
		if (buttonHover != null && sameDimensions(buttonBase, buttonHover)) {
			Float alpha = inferOverlayAlpha(buttonBase, buttonHover, UiColorPalette.BASE_WHITE);
			if (alpha != null) {
				inferredHoverAlpha = alpha;
			}
		}
		if (buttonPressed != null && sameDimensions(buttonBase, buttonPressed)) {
			Float alpha = inferOverlayAlpha(buttonBase, buttonPressed, UiColorPalette.BASE_BLACK);
			if (alpha != null) {
				inferredPressedAlpha = alpha;
			}
		}
		if (buttonDisabled != null && sameDimensions(buttonBase, buttonDisabled)) {
			Float alpha = inferDisabledAlpha(buttonBase, buttonDisabled);
			if (alpha != null) {
				inferredDisabledAlpha = alpha;
			}
		}

		// If the treatment changed, invalidate derived base images.
		stateBaseCache.clear();
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

	private static BufferedImage applyOverlay(BufferedImage source, Color overlay, float alpha) {
		if (source == null || overlay == null) {
			return source;
		}
		if (alpha <= 0f) {
			return source;
		}
		float clampedAlpha = Math.min(1f, alpha);

		int width = source.getWidth();
		int height = source.getHeight();
		BufferedImage out = new BufferedImage(width, height, BufferedImage.TYPE_INT_ARGB);

		int overlayRgb = overlay.getRGB();
		int or = (overlayRgb >> 16) & 0xFF;
		int og = (overlayRgb >> 8) & 0xFF;
		int ob = overlayRgb & 0xFF;

		for (int y = 0; y < height; y++) {
			for (int x = 0; x < width; x++) {
				int argb = source.getRGB(x, y);
				int a = (argb >> 24) & 0xFF;
				if (a == 0) {
					out.setRGB(x, y, 0);
					continue;
				}

				int r = (argb >> 16) & 0xFF;
				int g = (argb >> 8) & 0xFF;
				int b = argb & 0xFF;

				int nr = blendChannel(r, or, clampedAlpha);
				int ng = blendChannel(g, og, clampedAlpha);
				int nb = blendChannel(b, ob, clampedAlpha);
				out.setRGB(x, y, (a << 24) | (nr << 16) | (ng << 8) | nb);
			}
		}

		return out;
	}

	private static BufferedImage toDisabled(BufferedImage source, float disabledAlpha) {
		if (source == null) {
			return null;
		}
		float alphaMultiplier = Math.max(0f, Math.min(1f, disabledAlpha));

		int width = source.getWidth();
		int height = source.getHeight();
		BufferedImage out = new BufferedImage(width, height, BufferedImage.TYPE_INT_ARGB);

		for (int y = 0; y < height; y++) {
			for (int x = 0; x < width; x++) {
				int argb = source.getRGB(x, y);
				int a = (argb >> 24) & 0xFF;
				if (a == 0) {
					out.setRGB(x, y, 0);
					continue;
				}

				int r = (argb >> 16) & 0xFF;
				int g = (argb >> 8) & 0xFF;
				int b = argb & 0xFF;

				int gray = (int) (0.299f * r + 0.587f * g + 0.114f * b);
				int na = (int) Math.round(a * alphaMultiplier);
				out.setRGB(x, y, (na << 24) | (gray << 16) | (gray << 8) | gray);
			}
		}

		return out;
	}

	private static int blendChannel(int base, int overlay, float alpha) {
		return clampToByte(Math.round(base * (1f - alpha) + overlay * alpha));
	}

	private static int clampToByte(int value) {
		return Math.max(0, Math.min(255, value));
	}

	private void clearCache() {
		for (Map<Dimension, BufferedImage> sizeMap : backgroundCache.values()) {
			sizeMap.clear();
		}
	}

	private void installMouseHandlers() {
		addMouseListener(new MouseAdapter() {
			@Override
			public void mouseEntered(MouseEvent e) {
				if (!isEnabled()) {
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
				if (!isEnabled()) {
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

				boolean shouldSelect = isEnabled() && pressed && contains(e.getPoint()) && !selected;
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
				setCursor(isEnabled() ? Cursor.getPredefinedCursor(Cursor.HAND_CURSOR) : Cursor.getDefaultCursor());
			}
		});
	}

	/**
	 * Local 9-slice painter equivalent to the theme package's internal painter.
	 * This avoids depending on package-private theme internals while keeping rendering consistent.
	 */
	private static void paintNineSlice(Graphics2D g, BufferedImage src, NineSliceSpec spec, int x, int y, int width, int height) {
		if (g == null || src == null || spec == null) {
			return;
		}
		if (width <= 0 || height <= 0) {
			return;
		}

		int w = src.getWidth();
		int h = src.getHeight();

		int L = spec.left();
		int R = spec.right();
		int T = spec.top();
		int B = spec.bottom();

		int left = Math.min(L, width);
		int top = Math.min(T, height);
		int right = Math.min(R, Math.max(0, width - left));
		int bottom = Math.min(B, Math.max(0, height - top));

		int centerW = Math.max(0, width - left - right);
		int centerH = Math.max(0, height - top - bottom);

		int dx0 = x;
		int dx1 = x + left;
		int dx2 = dx1 + centerW;
		int dx3 = dx2 + right;

		int dy0 = y;
		int dy1 = y + top;
		int dy2 = dy1 + centerH;
		int dy3 = dy2 + bottom;

		int sx0 = 0;
		int sx1 = Math.min(L, w);
		int sx2 = Math.max(sx1, w - R);
		int sx3 = w;

		int sy0 = 0;
		int sy1 = Math.min(T, h);
		int sy2 = Math.max(sy1, h - B);
		int sy3 = h;

		// Corners
		if (left > 0 && top > 0) {
			g.drawImage(src, dx0, dy0, dx1, dy1, sx0, sy0, sx1, sy1, null);
		}
		if (right > 0 && top > 0) {
			g.drawImage(src, dx2, dy0, dx3, dy1, sx2, sy0, sx3, sy1, null);
		}
		if (left > 0 && bottom > 0) {
			g.drawImage(src, dx0, dy2, dx1, dy3, sx0, sy2, sx1, sy3, null);
		}
		if (right > 0 && bottom > 0) {
			g.drawImage(src, dx2, dy2, dx3, dy3, sx2, sy2, sx3, sy3, null);
		}

		// Edges
		if (centerW > 0 && top > 0) {
			g.drawImage(src, dx1, dy0, dx2, dy1, sx1, sy0, sx2, sy1, null);
		}
		if (centerW > 0 && bottom > 0) {
			g.drawImage(src, dx1, dy2, dx2, dy3, sx1, sy2, sx2, sy3, null);
		}
		if (left > 0 && centerH > 0) {
			g.drawImage(src, dx0, dy1, dx1, dy2, sx0, sy1, sx1, sy2, null);
		}
		if (right > 0 && centerH > 0) {
			g.drawImage(src, dx2, dy1, dx3, dy2, sx2, sy1, sx3, sy2, null);
		}

		// Center
		if (centerW > 0 && centerH > 0) {
			g.drawImage(src, dx1, dy1, dx2, dy2, sx1, sy1, sx2, sy2, null);
		}
	}
}