package com.lansoftprogramming.runeSequence.ui.shared.util;

import java.awt.HeadlessException;
import java.awt.Toolkit;
import java.awt.datatransfer.Clipboard;
import java.awt.datatransfer.DataFlavor;
import java.awt.datatransfer.StringSelection;
import java.awt.datatransfer.UnsupportedFlavorException;
import java.io.IOException;
import java.util.Objects;

public final class ClipboardStrings {
	private ClipboardStrings() {
	}

	public enum ReadStatus {
		SUCCESS,
		NO_STRING,
		UNAVAILABLE
	}

	public record ReadResult(ReadStatus status, String text) {
		public ReadResult {
			Objects.requireNonNull(status, "status");
		}

		public static ReadResult success(String text) {
			return new ReadResult(ReadStatus.SUCCESS, Objects.requireNonNull(text, "text"));
		}

		public static ReadResult noString() {
			return new ReadResult(ReadStatus.NO_STRING, null);
		}

		public static ReadResult unavailable() {
			return new ReadResult(ReadStatus.UNAVAILABLE, null);
		}
	}

	public enum WriteStatus {
		SUCCESS,
		UNAVAILABLE
	}

	public record WriteResult(WriteStatus status) {
		public WriteResult {
			Objects.requireNonNull(status, "status");
		}

		public static WriteResult success() {
			return new WriteResult(WriteStatus.SUCCESS);
		}

		public static WriteResult unavailable() {
			return new WriteResult(WriteStatus.UNAVAILABLE);
		}
	}

	public static ReadResult readSystemClipboardString() {
		try {
			Clipboard clipboard = Toolkit.getDefaultToolkit().getSystemClipboard();
			if (!clipboard.isDataFlavorAvailable(DataFlavor.stringFlavor)) {
				return ReadResult.noString();
			}

			Object data = clipboard.getData(DataFlavor.stringFlavor);
			if (data == null) {
				return ReadResult.success("");
			}
			if (data instanceof String s) {
				return ReadResult.success(s);
			}
			return ReadResult.unavailable();
		} catch (UnsupportedFlavorException | IOException | IllegalStateException | HeadlessException | SecurityException e) {
			return ReadResult.unavailable();
		}
	}

	public static WriteResult writeSystemClipboardString(String text) {
		Objects.requireNonNull(text, "text");
		try {
			StringSelection selection = new StringSelection(text);
			Toolkit.getDefaultToolkit().getSystemClipboard().setContents(selection, null);
			return WriteResult.success();
		} catch (IllegalStateException | HeadlessException | SecurityException e) {
			return WriteResult.unavailable();
		}
	}
}
