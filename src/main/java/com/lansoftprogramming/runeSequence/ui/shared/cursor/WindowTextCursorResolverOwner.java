package com.lansoftprogramming.runeSequence.ui.shared.cursor;

import org.slf4j.Logger;

import javax.swing.*;

public final class WindowTextCursorResolverOwner {
	private transient TextCursorSupport.WindowTextCursorResolver resolver;

	public void install(JComponent root, Logger logger) {
		if (resolver != null) {
			return;
		}
		resolver = TextCursorSupport.installWindowTextCursorResolver(root, logger);
	}

	public void uninstall() {
		if (resolver == null) {
			return;
		}
		resolver.uninstall();
		resolver = null;
	}
}
