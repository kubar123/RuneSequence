package com.lansoftprogramming.runeSequence.ui.presetManager.drag.model;

import java.awt.*;
import java.util.Objects;

/**
 * Lightweight, UI-facing snapshot of the current drag preview.
 * Derived once per timer tick and fanned out to paint code.
 */
public class DragPreviewModel {
	private final DropPreview dropPreview;
	private final PreviewSide previewSide;
	private final boolean showTopButton;
	private final boolean showBottomButton;
	private final Point cursorInFlowPanel;

	public DragPreviewModel(DropPreview dropPreview,
	                        PreviewSide previewSide,
	                        boolean showTopButton,
	                        boolean showBottomButton,
	                        Point cursorInFlowPanel) {
		this.dropPreview = dropPreview;
		this.previewSide = previewSide;
		this.showTopButton = showTopButton;
		this.showBottomButton = showBottomButton;
		this.cursorInFlowPanel = cursorInFlowPanel;
	}

	public DropPreview getDropPreview() {
		return dropPreview;
	}

	public PreviewSide getPreviewSide() {
		return previewSide;
	}

	public boolean isShowTopButton() {
		return showTopButton;
	}

	public boolean isShowBottomButton() {
		return showBottomButton;
	}

	public Point getCursorInFlowPanel() {
		return cursorInFlowPanel;
	}

	public boolean isValid() {
		return dropPreview != null && dropPreview.isValid();
	}

	@Override
	public boolean equals(Object o) {
		if (this == o) return true;
		if (!(o instanceof DragPreviewModel that)) return false;
		return showTopButton == that.showTopButton
			&& showBottomButton == that.showBottomButton
			&& Objects.equals(dropPreview, that.dropPreview)
			&& previewSide == that.previewSide
			&& Objects.equals(cursorInFlowPanel, that.cursorInFlowPanel);
	}

	@Override
	public int hashCode() {
		return Objects.hash(dropPreview, previewSide, showTopButton, showBottomButton, cursorInFlowPanel);
	}
}
