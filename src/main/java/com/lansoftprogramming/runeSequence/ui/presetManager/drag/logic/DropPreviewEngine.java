package com.lansoftprogramming.runeSequence.ui.presetManager.drag.logic;

import com.lansoftprogramming.runeSequence.core.sequence.parser.TooltipGrammar;
import com.lansoftprogramming.runeSequence.ui.presetManager.drag.model.*;
import com.lansoftprogramming.runeSequence.ui.presetManager.model.SequenceElement;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.awt.*;
import java.util.List;
import java.util.Objects;

import static com.lansoftprogramming.runeSequence.ui.presetManager.model.SequenceElementNavigation.nextNonTooltipIndex;
import static com.lansoftprogramming.runeSequence.ui.presetManager.model.SequenceElementNavigation.previousNonTooltipIndex;

/**
 * Pure geometry and grouping engine for ability drag previews.
 * Decides where a drop would land given card snapshots and cursor position.
 */
public class DropPreviewEngine {

	private static final Logger logger = LoggerFactory.getLogger(DropPreviewEngine.class);
	private static final int ZONE_HEIGHT = 20;
	private static final int MAX_DROP_DISTANCE = 100;
	private static final int GROUP_EDGE_TOLERANCE = 8;

	public DragPreviewModel calculatePreview(Point dragPoint,
	                                         GeometryCache geometryCache,
	                                         List<SequenceElement> elements,
	                                         List<SequenceElement> originalElements,
	                                         boolean releasePhaseLogging) {
		if (dragPoint == null) {
			return null;
		}

		List<SequenceElement> elementSnapshot = elements != null ? elements : List.of();

		if (geometryCache == null || geometryCache.isEmpty()) {
			return new DragPreviewModel(
					new DropPreview(0, DropZoneType.NEXT, -1, DropSide.RIGHT),
					PreviewSide.RIGHT,
					false,
					false,
					snapshotPoint(dragPoint)
			);
		}

		NearestCard nearest = geometryCache.findNearest(dragPoint);

		if (nearest.snapshot == null || nearest.distance > MAX_DROP_DISTANCE) {
			int lastVisualIdx = geometryCache.getLastVisualIndex();
			return new DragPreviewModel(
					new DropPreview(elementSnapshot.size(), DropZoneType.NEXT, lastVisualIdx, DropSide.RIGHT),
					PreviewSide.RIGHT,
					false,
					false,
					snapshotPoint(dragPoint)
			);
		}

		CardSnapshot target = nearest.snapshot;
		Rectangle bounds = target.boundsInFlowPanel;

		int cardCenterX = target.center.x;
		DropSide dropSide = dragPoint.x < cardCenterX ? DropSide.LEFT : DropSide.RIGHT;
		boolean beyondLeftEdge = dragPoint.x < bounds.x - GROUP_EDGE_TOLERANCE;
		boolean beyondRightEdge = dragPoint.x > bounds.x + bounds.width + GROUP_EDGE_TOLERANCE;

		int topLimit = target.topLimit;
		int bottomLimit = target.bottomLimit;
		int targetElementIndex = target.elementIndex;
		int targetVisualIndex = target.visualIndex;

		// Important: all indices (CardSnapshot.elementIndex, insertIndex) are based on the current
		// rendered element list (the "preview elements" during a drag). Using the original list here
		// can shift indices after the dragged element is removed and leads to incorrect zone/insert decisions.
		List<SequenceElement> elementsToCheck = elementSnapshot;
		DropZoneType existingGroupZone = target.groupZone;

		String targetAbilityKey = target.abilityKey;

		int currentTargetIndex = isAbilityAtIndex(elementSnapshot, targetElementIndex)
				? targetElementIndex
				: findNearestAbilityIndex(elementSnapshot, targetElementIndex);
		if (currentTargetIndex == -1 && targetAbilityKey != null) {
			currentTargetIndex = findAbilityIndexByKey(elementSnapshot, targetAbilityKey, targetElementIndex);
			if (releasePhaseLogging && currentTargetIndex != -1 && currentTargetIndex != targetElementIndex) {
				logger.info("Adjusted target index via key match: preferred={}, resolved={}", targetElementIndex, currentTargetIndex);
			}
		}
		if (currentTargetIndex == -1 && !elementSnapshot.isEmpty()) {
			currentTargetIndex = Math.max(0, Math.min(targetElementIndex, elementSnapshot.size() - 1));
			if (releasePhaseLogging) {
				logger.info("Preview fallback: using clamped index {} (original {}, elementsSize={})",
						currentTargetIndex,
						targetElementIndex,
						elementSnapshot.size()
				);
			}
		}
		if (releasePhaseLogging && currentTargetIndex != -1 && currentTargetIndex != targetElementIndex) {
			logger.info("Aligned target index from {} to {}", targetElementIndex, currentTargetIndex);
		}
		if (currentTargetIndex < 0) {
			currentTargetIndex = 0;
		}

		if (existingGroupZone == null) {
			existingGroupZone = resolveGroupZoneAt(elementsToCheck, currentTargetIndex);
		}

		GroupBoundaries groupBounds = analyzeGroupBoundaries(elementSnapshot, currentTargetIndex, existingGroupZone);

		DropZoneType zoneType;
		if (dragPoint.y < topLimit) {
			zoneType = determineZoneType(
					existingGroupZone,
					currentTargetIndex,
					groupBounds,
					dropSide,
					beyondLeftEdge,
					beyondRightEdge,
					DropZoneType.AND
			);
		} else if (dragPoint.y > bottomLimit) {
			zoneType = determineZoneType(
					existingGroupZone,
					currentTargetIndex,
					groupBounds,
					dropSide,
					beyondLeftEdge,
					beyondRightEdge,
					DropZoneType.OR
			);
		} else {
			zoneType = determineZoneType(
					existingGroupZone,
					currentTargetIndex,
					groupBounds,
					dropSide,
					beyondLeftEdge,
					beyondRightEdge,
					existingGroupZone != null ? existingGroupZone : DropZoneType.NEXT
			);
		}

		if (releasePhaseLogging) {
			logger.info("Zone decision: cursorY={}, topLimit={}, bottomLimit={}, zone={}, groupZone={}, targetElementIndex={}, targetVisualIndex={}",
					dragPoint.y,
					topLimit,
					bottomLimit,
					zoneType,
					existingGroupZone,
					targetElementIndex,
					targetVisualIndex
			);
		}

		int insertIndex = calculateInsertionIndex(
				elementSnapshot,
				currentTargetIndex,
				dropSide,
				zoneType,
				groupBounds
		);

		if (releasePhaseLogging) {
			logger.info("Insert decision: resolvedIndex={}, currentTargetIndex={}, dropSide={}, zone={}, groupBounds=[{},{}], groupZone={}, targetKey={}",
					insertIndex,
					currentTargetIndex,
					dropSide,
					zoneType,
					groupBounds.start,
					groupBounds.end,
					existingGroupZone,
					targetAbilityKey
			);
		}

		PreviewSide previewSide = derivePreviewSide(dragPoint, topLimit, bottomLimit, dropSide);

		return new DragPreviewModel(
				new DropPreview(insertIndex, zoneType, targetVisualIndex, dropSide),
				previewSide,
				previewSide == PreviewSide.TOP,
				previewSide == PreviewSide.BOTTOM,
				snapshotPoint(dragPoint)
		);
	}

	public DropZoneType resolveGroupZoneAt(List<SequenceElement> elements, int abilityIndex) {
		if (elements == null || abilityIndex < 0 || abilityIndex >= elements.size()) {
			return null;
		}
		int nextStructural = nextNonTooltipIndex(elements, abilityIndex + 1);
		if (nextStructural != -1) {
			DropZoneType zone = zoneForSeparator(elements.get(nextStructural).getType());
			if (zone != null) {
				return zone;
			}
		}
		int prevStructural = previousNonTooltipIndex(elements, abilityIndex - 1);
		if (prevStructural != -1) {
			DropZoneType zone = zoneForSeparator(elements.get(prevStructural).getType());
			if (zone != null) {
				return zone;
			}
		}
		return null;
	}

	public int mapAbilityToPreviewIndex(List<SequenceElement> elements, String abilityKey, int occurrence) {
		if (elements == null || abilityKey == null || occurrence < 0) {
			return -1;
		}
		int matchCount = 0;
		for (int i = 0; i < elements.size(); i++) {
			SequenceElement element = elements.get(i);
			if (!element.isAbility()) {
				continue;
			}
			if (abilityKey.equals(element.getResolvedAbilityKey())) {
				if (matchCount == occurrence) {
					return i;
				}
				matchCount++;
			}
		}
		return -1;
	}

	public String symbolForZone(DropZoneType zoneType) {
		if (zoneType == null) {
			return null;
		}
		return switch (zoneType) {
			case AND -> String.valueOf(TooltipGrammar.AND);
			case OR -> String.valueOf(TooltipGrammar.OR);
			default -> null;
		};
	}

	private GroupBoundaries analyzeGroupBoundaries(List<SequenceElement> elements,
	                                               int targetIndex,
	                                               DropZoneType zoneType) {
		SequenceElement.Type separatorType = separatorForZone(zoneType);
		if (separatorType == null || targetIndex < 0 || elements == null || targetIndex >= elements.size()) {
			return new GroupBoundaries(-1, -1);
		}
		int start = targetIndex;
		int end = targetIndex;

		int cursor = previousNonTooltipIndex(elements, targetIndex - 1);
		while (cursor != -1) {
			SequenceElement elem = elements.get(cursor);
			if (elem.getType() != separatorType) {
				break;
			}
			int abilityIndex = previousNonTooltipIndex(elements, cursor - 1);
			if (abilityIndex == -1 || !elements.get(abilityIndex).isAbility()) {
				break;
			}
			start = abilityIndex;
			cursor = previousNonTooltipIndex(elements, abilityIndex - 1);
		}

		cursor = nextNonTooltipIndex(elements, targetIndex + 1);
		while (cursor != -1) {
			SequenceElement elem = elements.get(cursor);
			if (elem.getType() != separatorType) {
				break;
			}
			int abilityIndex = nextNonTooltipIndex(elements, cursor + 1);
			if (abilityIndex == -1 || !elements.get(abilityIndex).isAbility()) {
				break;
			}
			end = abilityIndex;
			cursor = nextNonTooltipIndex(elements, abilityIndex + 1);
		}

		return new GroupBoundaries(start, end);
	}

	private DropZoneType determineZoneType(DropZoneType existingGroupZone,
	                                       int currentTargetIndex,
	                                       GroupBoundaries groupBounds,
	                                       DropSide dropSide,
	                                       boolean beyondLeftEdge,
	                                       boolean beyondRightEdge,
	                                       DropZoneType defaultZone) {
		if (existingGroupZone != null && groupBounds.isValid()) {
			boolean atGroupStart = currentTargetIndex == groupBounds.start;
			boolean atGroupEnd = currentTargetIndex == groupBounds.end;

			boolean leavingLeft = atGroupStart && dropSide == DropSide.LEFT && beyondLeftEdge;
			boolean leavingRight = atGroupEnd && dropSide == DropSide.RIGHT && beyondRightEdge;
			if (leavingLeft || leavingRight) {
				return DropZoneType.NEXT;
			}
			return existingGroupZone;
		}
		return defaultZone;
	}

	private int calculateInsertionIndex(List<SequenceElement> elements,
	                                    int currentTargetIndex,
	                                    DropSide dropSide,
	                                    DropZoneType zoneType,
	                                    GroupBoundaries groupBounds) {
		if (zoneType == DropZoneType.NEXT && groupBounds.isValid()) {
			if (currentTargetIndex == groupBounds.start && dropSide == DropSide.LEFT)
				return currentTargetIndex;
			if (currentTargetIndex == groupBounds.end && dropSide == DropSide.RIGHT)
				return currentTargetIndex + 1;
		}
		return dropSide == DropSide.LEFT ? currentTargetIndex : currentTargetIndex + 1;
	}

	private PreviewSide derivePreviewSide(Point dragPoint, int topLimit, int bottomLimit, DropSide dropSide) {
		if (dragPoint.y < topLimit) {
			return PreviewSide.TOP;
		}
		if (dragPoint.y > bottomLimit) {
			return PreviewSide.BOTTOM;
		}
		return dropSide == DropSide.LEFT ? PreviewSide.LEFT : PreviewSide.RIGHT;
	}

	private Point snapshotPoint(Point point) {
		return point != null ? new Point(point) : null;
	}

	private DropZoneType zoneForSeparator(SequenceElement.Type type) {
		if (type == null) {
			return null;
		}
		return switch (type) {
			case PLUS -> DropZoneType.AND;
			case SLASH -> DropZoneType.OR;
			default -> null;
		};
	}

	private SequenceElement.Type separatorForZone(DropZoneType zoneType) {
		if (zoneType == null) {
			return null;
		}
		return switch (zoneType) {
			case AND -> SequenceElement.Type.PLUS;
			case OR -> SequenceElement.Type.SLASH;
			default -> null;
		};
	}

	private int findAbilityIndexByKey(List<SequenceElement> elements, String abilityKey, int preferredIndex) {
		if (abilityKey == null || elements == null || elements.isEmpty()) {
			return -1;
		}
		int bestIndex = -1;
		int bestDistance = Integer.MAX_VALUE;
		for (int i = 0; i < elements.size(); i++) {
			SequenceElement element = elements.get(i);
			if (!element.isAbility() || !abilityKey.equals(element.getResolvedAbilityKey())) {
				continue;
			}
			int distance = preferredIndex >= 0 ? Math.abs(i - preferredIndex) : 0;
			if (distance < bestDistance) {
				bestDistance = distance;
				bestIndex = i;
				if (distance == 0) {
					break;
				}
			}
		}
		return bestIndex;
	}

	private boolean isAbilityAtIndex(List<SequenceElement> elements, int index) {
		return elements != null && index >= 0 && index < elements.size() && elements.get(index).isAbility();
	}

	private int findNearestAbilityIndex(List<SequenceElement> elements, int hintIndex) {
		if (elements == null || elements.isEmpty()) {
			return -1;
		}
		int clampedIndex = Math.max(0, Math.min(hintIndex, elements.size() - 1));
		if (elements.get(clampedIndex).isAbility()) {
			return clampedIndex;
		}
		int left = clampedIndex - 1;
		int right = clampedIndex + 1;
		while (left >= 0 || right < elements.size()) {
			if (left >= 0 && elements.get(left).isAbility()) {
				return left;
			}
			if (right < elements.size() && elements.get(right).isAbility()) {
				return right;
			}
			left--;
			right++;
		}
		return -1;
	}

	public static class GeometryCache {
		private final List<CardSnapshot> snapshots;
		private final int lastVisualIndex;

		public GeometryCache(List<CardSnapshot> snapshots, int lastVisualIndex) {
			this.snapshots = snapshots;
			this.lastVisualIndex = lastVisualIndex;
		}

		public boolean isEmpty() {
			return snapshots == null || snapshots.isEmpty();
		}

		public int getLastVisualIndex() {
			return lastVisualIndex;
		}

		public NearestCard findNearest(Point dragPoint) {
			CardSnapshot nearest = null;
			double minDistance = Double.MAX_VALUE;

			if (snapshots != null) {
				for (CardSnapshot snapshot : snapshots) {
					double distance = snapshot.distanceTo(dragPoint);
					if (distance < minDistance) {
						minDistance = distance;
						nearest = snapshot;
					}
				}
			}

			return new NearestCard(nearest, minDistance);
		}
	}

	public static class CardSnapshot {
		public final Rectangle boundsInFlowPanel;
		public final Point center;
		public final int elementIndex;
		public final int visualIndex;
		public final DropZoneType groupZone;
		public final String abilityKey;
		public final int topLimit;
		public final int bottomLimit;

		public CardSnapshot(Rectangle boundsInFlowPanel,
		                    int elementIndex,
		                    int visualIndex,
		                    DropZoneType groupZone,
		                    String abilityKey) {
			this.boundsInFlowPanel = boundsInFlowPanel;
			this.elementIndex = elementIndex;
			this.visualIndex = visualIndex;
			this.groupZone = groupZone;
			this.abilityKey = abilityKey;
			this.center = new Point(
					boundsInFlowPanel.x + boundsInFlowPanel.width / 2,
					boundsInFlowPanel.y + boundsInFlowPanel.height / 2
			);
			this.topLimit = boundsInFlowPanel.y + ZONE_HEIGHT;
			this.bottomLimit = boundsInFlowPanel.y + boundsInFlowPanel.height - ZONE_HEIGHT;
		}

		double distanceTo(Point point) {
			return point.distance(center);
		}

		@Override
		public boolean equals(Object o) {
			if (this == o) return true;
			if (!(o instanceof CardSnapshot that)) return false;
			return elementIndex == that.elementIndex
				&& visualIndex == that.visualIndex
				&& Objects.equals(boundsInFlowPanel, that.boundsInFlowPanel)
				&& Objects.equals(center, that.center)
				&& groupZone == that.groupZone
				&& Objects.equals(abilityKey, that.abilityKey);
		}

		@Override
		public int hashCode() {
			return Objects.hash(boundsInFlowPanel, center, elementIndex, visualIndex, groupZone, abilityKey);
		}
	}

	public record NearestCard(CardSnapshot snapshot, double distance) {
	}

	private static class GroupBoundaries {
		final int start;
		final int end;

		GroupBoundaries(int start, int end) {
			this.start = start;
			this.end = end;
		}

		boolean isValid() {
			return start >= 0 && end >= 0;
		}
	}
}
