package com.lansoftprogramming.runeSequence.ui.shared.cursor;

import org.slf4j.Logger;

import javax.swing.*;
import javax.swing.text.JTextComponent;
import java.awt.*;
import java.awt.event.AWTEventListener;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.awt.event.MouseMotionAdapter;
import java.util.Objects;

public final class TextCursorSupport {
	private static final String CLIENT_PROPERTY_TEXT_CURSOR_INSTALLED =
			TextCursorSupport.class.getName() + ".textCursorInstalled";

	private TextCursorSupport() {
	}

	public static void installTextCursor(JComponent component) {
		if (component == null) {
			return;
		}
		if (Boolean.TRUE.equals(component.getClientProperty(CLIENT_PROPERTY_TEXT_CURSOR_INSTALLED))) {
			component.setCursor(Cursor.getPredefinedCursor(Cursor.TEXT_CURSOR));
			return;
		}
		component.putClientProperty(CLIENT_PROPERTY_TEXT_CURSOR_INSTALLED, Boolean.TRUE);

		Cursor textCursor = Cursor.getPredefinedCursor(Cursor.TEXT_CURSOR);
		component.setCursor(textCursor);
		component.addMouseListener(new MouseAdapter() {
			@Override
			public void mouseEntered(MouseEvent e) {
				component.setCursor(textCursor);
			}
		});
		component.addMouseMotionListener(new MouseMotionAdapter() {
			@Override
			public void mouseMoved(MouseEvent e) {
				component.setCursor(textCursor);
			}
		});
	}

	public static WindowTextCursorResolver installWindowTextCursorResolver(JComponent root, Logger logger) {
		WindowTextCursorResolver resolver = new WindowTextCursorResolver(root, logger);
		resolver.install();
		return resolver;
	}

	public static final class WindowTextCursorResolver {
		private final JComponent root;
		private final Logger logger;
		private AWTEventListener listener;
		private Boolean lastCursorOverText;
		private long lastCursorLogAtMs;
		private Window cursorWindow;

		private WindowTextCursorResolver(JComponent root, Logger logger) {
			this.root = Objects.requireNonNull(root, "root");
			this.logger = Objects.requireNonNull(logger, "logger");
		}

		public void install() {
			if (listener != null) {
				return;
			}

			Cursor textCursor = Cursor.getPredefinedCursor(Cursor.TEXT_CURSOR);
			Cursor defaultCursor = Cursor.getDefaultCursor();
			cursorWindow = SwingUtilities.getWindowAncestor(root);

			listener = event -> {
				if (!(event instanceof MouseEvent mouseEvent)) {
					return;
				}
				int id = mouseEvent.getID();
				if (id != MouseEvent.MOUSE_MOVED && id != MouseEvent.MOUSE_DRAGGED && id != MouseEvent.MOUSE_EXITED) {
					return;
				}
				Object src = mouseEvent.getSource();
				if (!(src instanceof Component sourceComponent)) {
					return;
				}
				if (!SwingUtilities.isDescendingFrom(sourceComponent, root)) {
					return;
				}

				Point panelPoint = SwingUtilities.convertPoint(sourceComponent, mouseEvent.getPoint(), root);
				Component deepest = SwingUtilities.getDeepestComponentAt(root, panelPoint.x, panelPoint.y);
				boolean overText = deepest instanceof JTextComponent;

				Window owner = cursorWindow != null ? cursorWindow : SwingUtilities.getWindowAncestor(root);
				if (owner != null) {
					cursorWindow = owner;
					owner.setCursor(overText ? textCursor : defaultCursor);
				} else {
					root.setCursor(overText ? textCursor : defaultCursor);
				}

				long now = System.currentTimeMillis();
				boolean shouldLog = lastCursorOverText == null
						|| lastCursorOverText != overText
						|| (now - lastCursorLogAtMs) > 500;
				if (shouldLog && logger.isDebugEnabled()) {
					lastCursorOverText = overText;
					lastCursorLogAtMs = now;
					String srcName = sourceComponent.getName();
					String deepestName = deepest != null ? deepest.getName() : null;
					String deepestClass = deepest != null ? deepest.getClass().getName() : "null";
					Cursor deepestCursor = deepest != null ? deepest.getCursor() : null;
					boolean deepestEnabled = deepest == null || deepest.isEnabled();
					boolean deepestEditable = !(deepest instanceof JTextComponent tc) || tc.isEditable();
					Cursor ownerCursor = owner != null ? owner.getCursor() : null;
					logger.debug(
							"Cursor debug: overText={}, eventId={}, src={}, srcName={}, panelPoint=({},{}), deepest={}, deepestName={}, deepestEnabled={}, deepestEditable={}, deepestCursor={}, owner={}, ownerCursor={}",
							overText,
							id,
							sourceComponent.getClass().getName(),
							srcName,
							panelPoint.x,
							panelPoint.y,
							deepestClass,
							deepestName,
							deepestEnabled,
							deepestEditable,
							deepestCursor,
							owner != null ? owner.getClass().getName() : "null",
							ownerCursor
					);
				}
			};

			Toolkit.getDefaultToolkit().addAWTEventListener(
					listener,
					AWTEvent.MOUSE_MOTION_EVENT_MASK
			);
		}

		public void uninstall() {
			if (listener == null) {
				return;
			}
			Toolkit.getDefaultToolkit().removeAWTEventListener(listener);
			listener = null;
			lastCursorOverText = null;
			lastCursorLogAtMs = 0L;
			cursorWindow = null;
		}
	}
}

