package com.lansoftprogramming.runeSequence.ui.presetManager.service;

import com.lansoftprogramming.runeSequence.core.sequence.parser.TooltipGrammar;
import com.lansoftprogramming.runeSequence.ui.presetManager.drag.model.DropSide;
import com.lansoftprogramming.runeSequence.ui.presetManager.drag.model.DropZoneType;
import com.lansoftprogramming.runeSequence.ui.presetManager.model.SequenceElement;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.List;

import static com.lansoftprogramming.runeSequence.ui.presetManager.model.SequenceElementNavigation.nextNonTooltipIndex;
import static com.lansoftprogramming.runeSequence.ui.presetManager.model.SequenceElementNavigation.previousNonTooltipIndex;

/**
 * Builds expression strings from sequence elements and handles element manipulation.
 */
public class ExpressionBuilder {
    private static final Logger logger = LoggerFactory.getLogger(ExpressionBuilder.class);

    /**
     * Converts sequence elements to expression string.
     */
    public String buildExpression(List<SequenceElement> elements) {
        if (elements == null || elements.isEmpty()) {
            return "";
        }

        StringBuilder sb = new StringBuilder();
	        for (SequenceElement element : elements) {
	            if (element.isTooltip()) {
	                sb.append(TooltipGrammar.LEFT_PAREN)
	                    .append(TooltipGrammar.escapeTooltipText(element.getValue()))
	                    .append(TooltipGrammar.RIGHT_PAREN);
	            } else if (element.isAbility()) {
	                sb.append(element.formatAbilityToken());
	            } else {
                sb.append(element.getValue());
            }
        }
        return sb.toString();
    }

	/**
	 * Removes the ability at the specified element index (if valid).
	 */
	public List<SequenceElement> removeAbilityAt(List<SequenceElement> elements, int abilityElementIndex) {
        List<SequenceElement> result = new ArrayList<>(elements);
        if (abilityElementIndex < 0 || abilityElementIndex >= result.size()) {
            return result;
        }
        SequenceElement candidate = result.get(abilityElementIndex);
        if (!candidate.isAbility()) {
            return result;
        }
		removeAbilityAtIndexInternal(result, abilityElementIndex);
		return result;
	}

	/**
	 * Removes a tooltip element at the specified index without touching separators.
	 */
	public List<SequenceElement> removeTooltipAt(List<SequenceElement> elements, int tooltipElementIndex) {
		List<SequenceElement> result = new ArrayList<>(elements);
		if (tooltipElementIndex < 0 || tooltipElementIndex >= result.size()) {
			return result;
		}
		SequenceElement candidate = result.get(tooltipElementIndex);
		if (!candidate.isTooltip()) {
			return result;
		}
		result.remove(tooltipElementIndex);
		return result;
	}

	/**
	 * Applies an edit to an existing tooltip element. Returns the updated elements and the applied status
	 * so callers can surface validation feedback without duplicating insertion/removal rules.
	 */
	public TooltipEditResult editTooltipAt(List<SequenceElement> elements, int tooltipElementIndex, String newMessage) {
		List<SequenceElement> base = elements != null ? new ArrayList<>(elements) : new ArrayList<>();
		if (tooltipElementIndex < 0 || tooltipElementIndex >= base.size()) {
			return new TooltipEditResult(base, TooltipEditStatus.SKIPPED);
		}
		SequenceElement candidate = base.get(tooltipElementIndex);
		if (!candidate.isTooltip()) {
			return new TooltipEditResult(base, TooltipEditStatus.SKIPPED);
		}

		String normalized = newMessage != null ? newMessage.trim() : "";
		if (normalized.isEmpty()) {
			base.remove(tooltipElementIndex);
			return new TooltipEditResult(base, TooltipEditStatus.REMOVED);
		}

		if (!TooltipGrammar.isValidTooltipMessage(normalized)) {
			return new TooltipEditResult(new ArrayList<>(elements != null ? elements : List.of()), TooltipEditStatus.INVALID);
		}

		base.set(tooltipElementIndex, SequenceElement.tooltip(normalized));
		return new TooltipEditResult(base, TooltipEditStatus.UPDATED);
	}
    // remove ability and normalize adjacent separators/arrows to keep sequence parseable
    private void removeAbilityAtIndexInternal(List<SequenceElement> result, int abilityIndex) {
        SequenceElement left = abilityIndex > 0 ? result.get(abilityIndex - 1) : null;
        SequenceElement right = abilityIndex + 1 < result.size() ? result.get(abilityIndex + 1) : null;

        boolean leftIsGroup = isGroupSeparator(left);
        boolean rightIsGroup = isGroupSeparator(right);
        boolean rightIsArrow = right != null && right.getType() == SequenceElement.Type.ARROW;

        result.remove(abilityIndex);
        int cursor = abilityIndex;

        // case 1: ability sat between two group separators → drop the right one to prevent "||"
        if (rightIsGroup && cursor < result.size() && isGroupSeparator(result.get(cursor))) {
            result.remove(cursor);

        // case 2: ability bridged arrows or arrow+group → collapse duplicate arrow or stray arrow
        } else if (rightIsArrow && cursor < result.size() && result.get(cursor).getType() == SequenceElement.Type.ARROW) {
            if (leftIsGroup) {
                int leftIndex = cursor - 1;

                // if left neighbor is also a group separator, prefer removing the group to preserve arrow flow
                if (leftIndex >= 0 && isGroupSeparator(result.get(leftIndex))) {
                    result.remove(leftIndex);
                    cursor--;
                }
            } else {
                //otherwise drop the right arrow to avoid "-> ->" gaps
                result.remove(cursor);
            }
        }

        // trim a leading group separator with no ability to its right → prevents dangling group at boundary
        int leftIndex = cursor - 1;
        if (leftIndex >= 0 && leftIndex < result.size() && isGroupSeparator(result.get(leftIndex))) {
            boolean hasRightAbility = cursor < result.size() && result.get(cursor).isAbility();
            if (!hasRightAbility) {
                result.remove(leftIndex);
                // keep cursor aligned after deletion
                cursor--;
            }
        }

        // compute safe cleanup start: clamp to [0, size] so downstream fixups can scan locally
        int cleanupIndex = result.isEmpty() ? 0 : Math.max(0, Math.min(cursor, result.size()));
        cleanupAfterRemoval(result, cleanupIndex);
    }

	/**
	 * Inserts ability with proper grouping based on drop zone.
	 */
	public List<SequenceElement> insertAbility(List<SequenceElement> elements, String abilityKey,
                                                int insertIndex, DropZoneType zoneType, DropSide dropSide) {
		return insertAbility(elements, SequenceElement.ability(abilityKey), insertIndex, zoneType, dropSide);
	}

	/**
	 * Inserts ability with proper grouping based on drop zone, preserving metadata such as instance labels and overrides.
	 */
	public List<SequenceElement> insertAbility(List<SequenceElement> elements, SequenceElement abilityElement,
                                                int insertIndex, DropZoneType zoneType, DropSide dropSide) {
        List<SequenceElement> result = new ArrayList<>(elements);

        if (abilityElement == null || !abilityElement.isAbility()) {
            return result;
        }

        if (result.isEmpty()) {
            result.add(abilityElement);
            return result;
        }

        logger.debug("insertAbility: key={}, insertIndex={}, zoneType={}, dropSide={}, elements.size={}",
            abilityElement.getAbilityKey(), insertIndex, zoneType, dropSide, result.size());

        switch (zoneType) {
            case AND:
                insertIntoGroup(result, abilityElement, insertIndex, SequenceElement.Type.PLUS, dropSide);
                break;
            case OR:
                insertIntoGroup(result, abilityElement, insertIndex, SequenceElement.Type.SLASH, dropSide);
                break;
            case NEXT:
                insertAsNextStep(result, abilityElement, insertIndex);
                break;
        }

        return result;
    }

	/**
	 * Inserts a tooltip element at the given structural index.
	 * Tooltip placement does not alter separators or grouping semantics.
	 */
	public List<SequenceElement> insertTooltip(List<SequenceElement> elements,
	                                           String message,
	                                           int insertIndex,
	                                           DropZoneType zoneType,
	                                           DropSide dropSide) {
		List<SequenceElement> result = new ArrayList<>(elements);
		if (message == null) {
			return result;
		}
		if (!TooltipGrammar.isValidTooltipMessage(message)) {
			return result;
		}
		if (result.isEmpty()) {
			result.add(SequenceElement.tooltip(message));
			return result;
		}

		int clampedIndex = Math.max(0, Math.min(insertIndex, result.size()));
		result.add(clampedIndex, SequenceElement.tooltip(message));
		return result;
	}

    /**
     * Inserts an entire pre-parsed sequence (clipboard payload) at the given drop preview location.
     * - NEXT: inserts the snippet as a new step block, splitting groups if necessary.
     * - AND/OR: only supported when the snippet is a single ability; otherwise the insertion is skipped.
     */
    public List<SequenceElement> insertSequence(List<SequenceElement> elements,
                                                List<SequenceElement> snippet,
                                                int insertIndex,
                                                DropZoneType zoneType,
                                                DropSide dropSide) {
        if (snippet == null || snippet.isEmpty()) {
            return new ArrayList<>(elements);
        }
        List<SequenceElement> base = new ArrayList<>(elements);
        List<SequenceElement> payload = trimSeparators(snippet);

        if (payload.isEmpty()) {
            return base;
        }

        if (base.isEmpty()) {
            return new ArrayList<>(payload);
        }

        if (zoneType == DropZoneType.NEXT) {
            // Handle group splitting logic similar to insertAsNextStep
            if (insertIndex > 0 && insertIndex < base.size()) {
                SequenceElement before = base.get(insertIndex - 1);
                SequenceElement at = base.get(insertIndex);

                // If inserting between Ability and GroupSeparator, we are splitting a group
                if (before.isAbility() && (at.isPlus() || at.isSlash())) {
                    // Replace the group separator with an arrow
                    base.set(insertIndex, SequenceElement.arrow());
                    // Shift insert index to be after the new arrow
                    insertIndex++;
                }
            }

            // Insert the payload
            base.addAll(insertIndex, payload);
            
            // Ensure boundaries around the insertion have separators (default to Arrow if missing)
            ensureBoundaries(base, insertIndex, payload.size());
            
            normalizeSeparators(base);
            return base;
        }

        if ((zoneType == DropZoneType.AND || zoneType == DropZoneType.OR)
                && payload.size() == 1
                && payload.get(0).isAbility()) {
            return insertAbility(base, payload.get(0), insertIndex, zoneType, dropSide);
        }

        logger.warn("insertSequence: unsupported combination zone={} payloadSize={}", zoneType, payload.size());
        return base;
    }

    private void ensureBoundaries(List<SequenceElement> elements, int startIndex, int length) {
        // Check after the inserted block
        int endIndex = startIndex + length;
        if (endIndex < elements.size()) {
            int leftStructural = previousNonTooltipIndex(elements, endIndex - 1);
            int rightStructural = nextNonTooltipIndex(elements, endIndex);
            if (leftStructural != -1
                    && rightStructural != -1
                    && elements.get(leftStructural).isAbility()
                    && elements.get(rightStructural).isAbility()
                    && onlyTooltipsBetween(elements, leftStructural, rightStructural)) {
                elements.add(rightStructural, SequenceElement.arrow());
            }
        }

        // Check before the inserted block
        if (startIndex > 0) {
            int leftStructural = previousNonTooltipIndex(elements, startIndex - 1);
            int rightStructural = nextNonTooltipIndex(elements, startIndex);
            if (leftStructural != -1
                    && rightStructural != -1
                    && elements.get(leftStructural).isAbility()
                    && elements.get(rightStructural).isAbility()
                    && onlyTooltipsBetween(elements, leftStructural, rightStructural)) {
                elements.add(rightStructural, SequenceElement.arrow());
            }
        }
    }

    /**
     * Inserts ability into an existing group or creates a new group.
     * Uses dropSide to determine insertion position consistently.
     *
     * Key principle: insertIndex and dropSide together define where to insert.
     * - dropSide LEFT: insert BEFORE the ability at insertIndex
     * - dropSide RIGHT: insert AFTER the ability at insertIndex
     */
    private void insertIntoGroup(List<SequenceElement> elements,
                              SequenceElement abilityElement,
                              int insertIndex,
                              SequenceElement.Type requestedGroupType,
                              DropSide dropSide) {
        if (elements.isEmpty()) {
            elements.add(abilityElement);
            return;
        }

        // Clamp insertIndex to valid range
        insertIndex = Math.max(0, Math.min(insertIndex, elements.size()));

        if (dropSide == DropSide.RIGHT) {
            int leftStructural = previousNonTooltipIndex(elements, insertIndex - 1);
            if (leftStructural != -1
                    && elements.get(leftStructural).isAbility()
                    && onlyTooltipsBetween(elements, leftStructural, insertIndex)) {
                while (insertIndex < elements.size() && elements.get(insertIndex).isTooltip()) {
                    insertIndex++;
                }
            }
        } else if (dropSide == DropSide.LEFT) {
            int rightStructural = nextNonTooltipIndex(elements, insertIndex);
            if (rightStructural != -1
                    && elements.get(rightStructural).isAbility()
                    && onlyTooltipsBetween(elements, insertIndex, rightStructural)) {
                while (insertIndex > 0 && elements.get(insertIndex - 1).isTooltip()) {
                    insertIndex--;
                }
            }
        }

        // If insertIndex is at the end, just append
        if (insertIndex >= elements.size()) {
            int lastStructural = previousNonTooltipIndex(elements, elements.size() - 1);
            if (lastStructural != -1 && elements.get(lastStructural).isAbility()) {
                SequenceElement separator = requestedGroupType == SequenceElement.Type.PLUS
                    ? SequenceElement.plus()
                    : SequenceElement.slash();
                elements.add(separator);
            }
            elements.add(abilityElement);
            logger.debug("Inserted at end");
            return;
        }

        // Find the target ability at or near insertIndex
        int targetAbilityIndex = insertIndex;
        if (!elements.get(insertIndex).isAbility()) {
            // Find nearest ability
            for (int i = insertIndex; i >= 0; i--) {
                if (elements.get(i).isAbility()) {
                    targetAbilityIndex = i;
                    break;
                }
            }

            if (!elements.get(targetAbilityIndex).isAbility()) {
                // Fallback to insertAsNextStep if no ability found
                insertAsNextStep(elements, abilityElement, insertIndex);
                return;
            }
        }

        // Check if target is in an existing group
        GroupInfo groupInfo = analyzeGroup(elements, targetAbilityIndex);

        logger.debug("insertIntoGroup: targetIndex={}, isInGroup={}, groupType={}, groupStart={}, groupEnd={}, dropSide={}",
            targetAbilityIndex, groupInfo.isInGroup, groupInfo.groupType, groupInfo.startIndex, groupInfo.endIndex, dropSide);

        SequenceElement separator = requestedGroupType == SequenceElement.Type.PLUS
            ? SequenceElement.plus()
            : SequenceElement.slash();

        if (groupInfo.isInGroup) {
            // Target is in a group - must use the group's separator type
            if (groupInfo.groupType != requestedGroupType) {
                logger.debug("Group type mismatch: requested={}, existing={} - using group type",
                    requestedGroupType, groupInfo.groupType);
                separator = groupInfo.groupType == SequenceElement.Type.PLUS
                    ? SequenceElement.plus()
                    : SequenceElement.slash();
            }
        }

        // Insert based on drop side - CONSISTENT for both grouped and standalone
        int insertPosition;
        if (dropSide == DropSide.LEFT) {
            // Insert BEFORE target ability
            insertPosition = insertIndex;
            elements.add(insertPosition, abilityElement);
            elements.add(insertPosition + 1, separator);
            logger.debug("LEFT insertion at {} (before target)", insertPosition);
        } else {
            // Insert AFTER target ability
            insertPosition = insertIndex;
            elements.add(insertPosition, separator);
            elements.add(insertPosition + 1, abilityElement);
            logger.debug("RIGHT insertion at {} (after target)", insertPosition);
        }
    }

    /**
     * Analyzes if an ability is part of a group and returns group boundaries.
     */
    private GroupInfo analyzeGroup(List<SequenceElement> elements, int abilityIndex) {
        SequenceElement.Type groupType = null;
        int startIndex = abilityIndex;
        int endIndex = abilityIndex;

        // Scan backwards to find group start
        int checkIndex = abilityIndex - 1;
        while (checkIndex >= 0) {
            SequenceElement elem = elements.get(checkIndex);

            if (elem.isTooltip()) {
                checkIndex--;
                continue;
            }
            if (elem.isPlus() || elem.isSlash()) {
                if (groupType == null) {
                    groupType = elem.getType();
                } else if (elem.getType() != groupType) {
                    // Different separator type - not part of same group
                    break;
                }
                checkIndex--;
            } else if (elem.isAbility()) {
                startIndex = checkIndex;
                checkIndex--;
            } else {
                // Arrow separator - end of group
                break;
            }
        }

        // Scan forwards to find group end
        checkIndex = abilityIndex + 1;
        while (checkIndex < elements.size()) {
            SequenceElement elem = elements.get(checkIndex);

            if (elem.isTooltip()) {
                checkIndex++;
                continue;
            }
            if (elem.isPlus() || elem.isSlash()) {
                if (groupType == null) {
                    groupType = elem.getType();
                } else if (elem.getType() != groupType) {
                    // Different separator type - not part of same group
                    break;
                }
                checkIndex++;
            } else if (elem.isAbility()) {
                endIndex = checkIndex;
                checkIndex++;
            } else {
                // Arrow separator - end of group
                break;
            }
        }

        boolean isInGroup = groupType != null;
        return new GroupInfo(isInGroup, groupType, startIndex, endIndex);
    }

    /**
     * Inserts ability as a new sequential step (with arrow separator).
     * When inserting after an ability that's in a group, this splits the group.
     */
    private void insertAsNextStep(List<SequenceElement> elements, SequenceElement abilityElement, int insertIndex) {
        insertIndex = Math.max(0, Math.min(insertIndex, elements.size()));

        logger.debug("insertAsNextStep: key={}, insertIndex={}, elements={}",
            abilityElement.getAbilityKey(), insertIndex, buildExpression(elements));

        if (insertIndex == 0) {
            elements.add(0, abilityElement);
            int nextStructural = nextNonTooltipIndex(elements, 1);
            if (nextStructural != -1 && elements.get(nextStructural).isAbility()) {
                elements.add(1, SequenceElement.arrow());
            }
            return;
        }

        if (insertIndex >= elements.size()) {
            int lastStructural = previousNonTooltipIndex(elements, elements.size() - 1);
            if (lastStructural != -1 && elements.get(lastStructural).isAbility()) {
                elements.add(SequenceElement.arrow());
            }
            elements.add(abilityElement);
            return;
        }

        int leftStructuralIndex = previousNonTooltipIndex(elements, insertIndex - 1);
        if (leftStructuralIndex != -1
                && elements.get(leftStructuralIndex).isAbility()
                && onlyTooltipsBetween(elements, leftStructuralIndex, insertIndex)) {
            while (insertIndex < elements.size() && elements.get(insertIndex).isTooltip()) {
                insertIndex++;
            }
        }

        if (insertIndex >= elements.size()) {
            int lastStructural = previousNonTooltipIndex(elements, elements.size() - 1);
            if (lastStructural != -1 && elements.get(lastStructural).isAbility()) {
                elements.add(SequenceElement.arrow());
            }
            elements.add(abilityElement);
            return;
        }

        leftStructuralIndex = previousNonTooltipIndex(elements, insertIndex - 1);
        int rightStructuralIndex = nextNonTooltipIndex(elements, insertIndex);

        SequenceElement leftStructural = leftStructuralIndex != -1 ? elements.get(leftStructuralIndex) : null;
        SequenceElement rightStructural = rightStructuralIndex != -1 ? elements.get(rightStructuralIndex) : null;

        logger.debug("  leftStructural[{}]: {}", leftStructuralIndex, leftStructural);
        logger.debug("  rightStructural[{}]: {}", rightStructuralIndex, rightStructural);

        if (leftStructural != null && leftStructural.isAbility()) {
            GroupInfo groupInfo = analyzeGroup(elements, leftStructuralIndex);
            logger.debug("  groupInfo: isInGroup={}, groupType={}", groupInfo.isInGroup, groupInfo.groupType);

            if (groupInfo.isInGroup && rightStructural != null && (rightStructural.isPlus() || rightStructural.isSlash())) {
                logger.debug("  Splitting group: replacing separator at {} with arrow", rightStructuralIndex);
                elements.set(rightStructuralIndex, SequenceElement.arrow());
                elements.add(rightStructuralIndex + 1, abilityElement);

                int nextStructural = nextNonTooltipIndex(elements, rightStructuralIndex + 2);
                if (nextStructural != -1 && elements.get(nextStructural).isAbility()
                        && onlyTooltipsBetween(elements, rightStructuralIndex + 1, nextStructural)) {
                    elements.add(rightStructuralIndex + 2, SequenceElement.arrow());
                }
                return;
            }

            logger.debug("  Standard NEXT insertion after ability");
            elements.add(insertIndex, SequenceElement.arrow());
            elements.add(insertIndex + 1, abilityElement);

            int nextStructural = nextNonTooltipIndex(elements, insertIndex + 2);
            if (nextStructural != -1 && elements.get(nextStructural).isAbility()
                    && onlyTooltipsBetween(elements, insertIndex + 1, nextStructural)) {
                elements.add(insertIndex + 2, SequenceElement.arrow());
            }
            return;
        }

        logger.debug("  Inserting NEXT after separator/boundary");
        elements.add(insertIndex, abilityElement);

        int nextStructural = nextNonTooltipIndex(elements, insertIndex + 1);
	        if (nextStructural != -1 && elements.get(nextStructural).isAbility()
	                && onlyTooltipsBetween(elements, insertIndex, nextStructural)) {
	            elements.add(insertIndex + 1, SequenceElement.arrow());
	        }
	    }

	    private boolean onlyTooltipsBetween(List<SequenceElement> elements, int leftIndex, int rightIndex) {
	        if (elements == null || leftIndex < 0 || rightIndex < 0) {
	            return false;
        }
        if (rightIndex <= leftIndex + 1) {
            return true;
        }
        for (int i = leftIndex + 1; i < rightIndex; i++) {
            if (!elements.get(i).isTooltip()) {
                return false;
            }
        }
	        return true;
	    }

    /**
     * Determines if the element represents a logical group separator.
     */
    private boolean isGroupSeparator(SequenceElement element) {
        return element != null && (element.isPlus() || element.isSlash());
    }

    /**
     * Cleans up orphaned or duplicate separators after removal.
     */
    private void cleanupAfterRemoval(List<SequenceElement> elements, int removalPoint) {
        if (elements.isEmpty()) return;

        normalizeSeparatorsWithTooltips(elements);

        // Remove orphaned separator at removal point
        if (removalPoint < elements.size() && elements.get(removalPoint).isSeparator()) {
            boolean orphanedLeft = (removalPoint == 0) || elements.get(removalPoint - 1).isSeparator();
            boolean orphanedRight = (removalPoint >= elements.size() - 1) ||
                                   (removalPoint + 1 < elements.size() && elements.get(removalPoint + 1).isSeparator());

            if (orphanedLeft || orphanedRight) {
                elements.remove(removalPoint);
            }
        }

        // Clean up consecutive separators
        for (int i = elements.size() - 1; i > 0; i--) {
            if (elements.get(i).isSeparator() && elements.get(i - 1).isSeparator()) {
                elements.remove(i);
            }
        }

        // Clean up leading/trailing separators
        while (!elements.isEmpty() && elements.get(0).isSeparator()) {
            elements.remove(0);
        }
        while (!elements.isEmpty() && elements.get(elements.size() - 1).isSeparator()) {
            elements.remove(elements.size() - 1);
        }

        normalizeSeparatorsWithTooltips(elements);
    }

    private List<SequenceElement> trimSeparators(List<SequenceElement> snippet) {
        List<SequenceElement> out = new ArrayList<>(snippet);
        while (!out.isEmpty() && out.get(0).isSeparator()) {
            out.remove(0);
        }
        while (!out.isEmpty() && out.get(out.size() - 1).isSeparator()) {
            out.remove(out.size() - 1);
        }
        return out;
    }

    private void normalizeSeparators(List<SequenceElement> elements) {
        if (elements.isEmpty()) {
            return;
        }

        normalizeSeparatorsWithTooltips(elements);

        // Clean up consecutive separators
        for (int i = elements.size() - 1; i > 0; i--) {
            if (elements.get(i).isSeparator() && elements.get(i - 1).isSeparator()) {
                elements.remove(i);
            }
        }

        // Remove leading/trailing separators
        while (!elements.isEmpty() && elements.get(0).isSeparator()) {
            elements.remove(0);
        }
        while (!elements.isEmpty() && elements.get(elements.size() - 1).isSeparator()) {
            elements.remove(elements.size() - 1);
        }

        normalizeSeparatorsWithTooltips(elements);
    }

    private void normalizeSeparatorsWithTooltips(List<SequenceElement> elements) {
        if (elements == null || elements.isEmpty()) {
            return;
        }

        boolean changed;
        do {
            changed = false;
            for (int i = 0; i < elements.size(); i++) {
                SequenceElement element = elements.get(i);
                if (element == null || !element.isSeparator()) {
                    continue;
                }

                if (element.getType() == SequenceElement.Type.PLUS || element.getType() == SequenceElement.Type.SLASH) {
                    boolean hasLeftAbility = hasAbilityToLeftInSameStep(elements, i);
                    boolean hasRightAbility = hasAbilityToRightInSameStep(elements, i);
                    if (!hasLeftAbility || !hasRightAbility) {
                        elements.remove(i);
                        changed = true;
                        break;
                    }
                } else if (element.getType() == SequenceElement.Type.ARROW) {
                    boolean hasLeftAbility = hasAbilityToLeftInSameStep(elements, i);
                    boolean hasRightAbility = hasAbilityToRightInSameStep(elements, i);
                    if (!hasLeftAbility || !hasRightAbility) {
                        elements.remove(i);
                        changed = true;
                        break;
                    }
                } else {
                    elements.remove(i);
                    changed = true;
                    break;
                }
            }
        } while (changed);
    }

    private boolean hasAbilityToLeftInSameStep(List<SequenceElement> elements, int fromIndexExclusive) {
        int cursor = previousNonTooltipIndex(elements, fromIndexExclusive - 1);
        while (cursor != -1) {
            SequenceElement candidate = elements.get(cursor);
            if (candidate.getType() == SequenceElement.Type.ARROW) {
                return false;
            }
            if (candidate.isAbility()) {
                return true;
            }
            // Skip group separators when searching left; they don't satisfy "ability exists".
            cursor = previousNonTooltipIndex(elements, cursor - 1);
        }
        return false;
    }

    private boolean hasAbilityToRightInSameStep(List<SequenceElement> elements, int fromIndexExclusive) {
        int cursor = nextNonTooltipIndex(elements, fromIndexExclusive + 1);
        while (cursor != -1) {
            SequenceElement candidate = elements.get(cursor);
            if (candidate.getType() == SequenceElement.Type.ARROW) {
                return false;
            }
            if (candidate.isAbility()) {
                return true;
            }
            // Skip group separators when searching right; they don't satisfy "ability exists".
            cursor = nextNonTooltipIndex(elements, cursor + 1);
        }
        return false;
    }

    /**
     * Internal class holding group analysis results.
     */
    private static class GroupInfo {
        final boolean isInGroup;
        final SequenceElement.Type groupType;
        final int startIndex;
        final int endIndex;

        GroupInfo(boolean isInGroup, SequenceElement.Type groupType, int startIndex, int endIndex) {
            this.isInGroup = isInGroup;
            this.groupType = groupType;
            this.startIndex = startIndex;
            this.endIndex = endIndex;
        }
    }

	public enum TooltipEditStatus {
		UPDATED,
		REMOVED,
		INVALID,
		SKIPPED
	}

	public record TooltipEditResult(List<SequenceElement> elements, TooltipEditStatus status) {
	}
}
