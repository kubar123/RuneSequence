package com.lansoftprogramming.runeSequence.ui.shared.component;

import javax.swing.*;
import java.awt.*;
import java.awt.event.AWTEventListener;
import java.awt.event.MouseEvent;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;
import java.util.function.Predicate;

/**
 * Paints a temporary gold glow behind hovered "draggable" children without affecting layout.
 *
 * <p>The glow is painted in this container's {@link #paintChildren(Graphics)} before children render,
 * so it appears behind cards/buttons. It may overlap nearby UI elements (intentionally), but is
 * clipped to this container's bounds.
 */
public class HoverGlowContainerPanel extends JPanel {
	private static final int GLOW_PX = 5;
	private static final int FADE_MS = 500;
	private static final int TICK_MS = 25;

	private final Predicate<Component> glowTarget;
	private transient AWTEventListener hoverListener;
	private transient Timer fadeTimer;
	private transient Component currentTarget;
	private final Map<Component, GlowInstance> glowInstances = new LinkedHashMap<>();

	public HoverGlowContainerPanel(Predicate<Component> glowTarget) {
		super();
		this.glowTarget = Objects.requireNonNull(glowTarget, "glowTarget");
	}

	public HoverGlowContainerPanel(LayoutManager layout, Predicate<Component> glowTarget) {
		super(layout);
		this.glowTarget = Objects.requireNonNull(glowTarget, "glowTarget");
	}

	@Override
	public void addNotify() {
		super.addNotify();
		installHoverListener();
	}

	@Override
	public void removeNotify() {
		uninstallHoverListener();
		super.removeNotify();
	}

	@Override
	protected void paintChildren(Graphics g) {
		paintGlow(g);
		super.paintChildren(g);
	}

	private void paintGlow(Graphics g) {
		if (glowInstances.isEmpty()) {
			return;
		}

		Graphics2D g2 = (Graphics2D) g.create();
		try {
			g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);

			for (GlowInstance instance : glowInstances.values()) {
				Component target = instance.target;
				float a = instance.alpha;
				if (target == null || a <= 0f || !target.isShowing()) {
					continue;
				}

				Rectangle targetBounds = SwingUtilities.convertRectangle(target.getParent(), target.getBounds(), this);
				int x = targetBounds.x - GLOW_PX;
				int y = targetBounds.y - GLOW_PX;
				int w = targetBounds.width + (GLOW_PX * 2);
				int h = targetBounds.height + (GLOW_PX * 2);
				if (w <= 2 || h <= 2) {
					continue;
				}

				int arc = Math.min(12, Math.min(w, h) / 3);

				// Bloom-like: brightest nearest the item, fading darker and more transparent outward.
				for (int i = 0; i < GLOW_PX; i++) {
					float t = (GLOW_PX <= 1) ? 0f : (i / (float) (GLOW_PX - 1));
					float strength = 1f - t; // 1 near item -> 0 at outer edge
					float ringAlpha = a * (0.50f * (float) Math.pow(strength, 1.35));

					int r = lerp(255, 160, t);
					int gr = lerp(225, 110, t);
					int b = lerp(110, 20, t);

					g2.setColor(new Color(r, gr, b, Math.round(255 * clamp01(ringAlpha))));
					g2.setStroke(new BasicStroke(1f));

					int inset = i;
					int rx = x + inset;
					int ry = y + inset;
					int rw = w - 1 - (inset * 2);
					int rh = h - 1 - (inset * 2);
					if (rw <= 0 || rh <= 0) {
						break;
					}
					g2.drawRoundRect(rx, ry, rw, rh, arc, arc);
				}
			}
		} finally {
			g2.dispose();
		}
	}

	private void installHoverListener() {
		if (hoverListener != null) {
			return;
		}

		hoverListener = event -> {
			if (!(event instanceof MouseEvent me)) {
				return;
			}
			int id = me.getID();
			if (id != MouseEvent.MOUSE_MOVED
					&& id != MouseEvent.MOUSE_EXITED
					&& id != MouseEvent.MOUSE_DRAGGED
					&& id != MouseEvent.MOUSE_PRESSED
					&& id != MouseEvent.MOUSE_RELEASED) {
				return;
			}
			Object src = me.getSource();
			if (!(src instanceof Component sourceComponent)) {
				return;
			}
			if (!SwingUtilities.isDescendingFrom(sourceComponent, this)) {
				return;
			}

			Point panelPoint = SwingUtilities.convertPoint(sourceComponent, me.getPoint(), this);
			if (!contains(panelPoint)) {
				clearCurrentTarget();
				return;
			}
			Component deepest = SwingUtilities.getDeepestComponentAt(this, panelPoint.x, panelPoint.y);

			Component nextTarget = findGlowTarget(deepest);
			if (nextTarget != currentTarget) {
				setCurrentTarget(nextTarget);
				repaint();
			}

			if (id == MouseEvent.MOUSE_EXITED && nextTarget == null) {
				clearCurrentTarget();
			}
		};

		Toolkit.getDefaultToolkit().addAWTEventListener(
				hoverListener,
				AWTEvent.MOUSE_MOTION_EVENT_MASK | AWTEvent.MOUSE_EVENT_MASK
		);
	}

	private void uninstallHoverListener() {
		if (hoverListener != null) {
			Toolkit.getDefaultToolkit().removeAWTEventListener(hoverListener);
			hoverListener = null;
		}
		stopFade();
		currentTarget = null;
		glowInstances.clear();
	}

	private Component findGlowTarget(Component deepest) {
		Component c = deepest;
		while (c != null && c != this) {
			if (glowTarget.test(c)) {
				return c;
			}
			c = c.getParent();
		}
		return null;
	}

	private void setCurrentTarget(Component nextTarget) {
		if (currentTarget != null && currentTarget != nextTarget) {
			markFading(currentTarget);
		}
		currentTarget = nextTarget;
		if (nextTarget != null) {
			GlowInstance instance = glowInstances.computeIfAbsent(nextTarget, GlowInstance::new);
			instance.alpha = 1f;
			instance.fadeStartAtMs = 0L;
			instance.startAlpha = 1f;
			trimInstances();
			ensureFadeTimer();
		}
	}

	private void clearCurrentTarget() {
		if (currentTarget != null) {
			markFading(currentTarget);
			currentTarget = null;
			ensureFadeTimer();
			repaint();
		}
	}

	private void markFading(Component target) {
		GlowInstance instance = glowInstances.computeIfAbsent(target, GlowInstance::new);
		if (instance.fadeStartAtMs == 0L) {
			instance.fadeStartAtMs = System.currentTimeMillis();
			instance.startAlpha = instance.alpha <= 0f ? 1f : instance.alpha;
		}
	}

	private void ensureFadeTimer() {
		if (fadeTimer != null) {
			return;
		}
		fadeTimer = new Timer(TICK_MS, evt -> tickFade());
		fadeTimer.setRepeats(true);
		fadeTimer.start();
	}

	private void tickFade() {
		long now = System.currentTimeMillis();

		boolean anyActive = currentTarget != null;
		boolean anyVisible = false;

		Iterator<Map.Entry<Component, GlowInstance>> it = glowInstances.entrySet().iterator();
		while (it.hasNext()) {
			GlowInstance instance = it.next().getValue();
			Component target = instance.target;
			if (target == null || !target.isShowing()) {
				it.remove();
				continue;
			}
			if (target == currentTarget) {
				instance.alpha = 1f;
				anyVisible = true;
				continue;
			}
			if (instance.fadeStartAtMs > 0L) {
				float t = Math.min(1f, (now - instance.fadeStartAtMs) / (float) FADE_MS);
				// Linear fade for a longer, more noticeable trail.
				instance.alpha = instance.startAlpha * (1f - t);
				if (instance.alpha <= 0.01f || t >= 1f) {
					it.remove();
				} else {
					anyVisible = true;
				}
			} else if (instance.alpha > 0f) {
				anyVisible = true;
			}
		}

		if (!anyActive && !anyVisible) {
			stopFade();
			repaint();
			return;
		}

		repaint();
	}

	private void stopFade() {
		if (fadeTimer != null) {
			fadeTimer.stop();
			fadeTimer = null;
		}
	}

	private void trimInstances() {
		// Keep a small trail of recent items.
		while (glowInstances.size() > 8) {
			Iterator<Component> it = glowInstances.keySet().iterator();
			if (!it.hasNext()) {
				break;
			}
			Component first = it.next();
			if (first == currentTarget && glowInstances.size() > 1) {
				// Skip current target; remove the next oldest.
				it.next();
			}
			it.remove();
		}
	}

	private static final class GlowInstance {
		private final Component target;
		private float alpha;
		private long fadeStartAtMs;
		private float startAlpha;

		private GlowInstance(Component target) {
			this.target = target;
			this.alpha = 1f;
			this.fadeStartAtMs = 0L;
			this.startAlpha = 1f;
		}
	}

	private static float clamp01(float v) {
		return Math.max(0f, Math.min(1f, v));
	}

	private static int lerp(int a, int b, float t) {
		return Math.round(a + ((b - a) * t));
	}
}
