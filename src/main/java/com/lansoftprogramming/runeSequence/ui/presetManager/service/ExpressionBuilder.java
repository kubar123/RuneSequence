package com.lansoftprogramming.runeSequence.ui.presetManager.service;

import com.lansoftprogramming.runeSequence.ui.presetManager.drag.model.DropZoneType;
import com.lansoftprogramming.runeSequence.ui.presetManager.model.SequenceElement;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.List;

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
            sb.append(element.getValue());
        }
        return sb.toString();
    }

    /**
     * Removes an ability and its associated RIGHT operator.
     * Operators belong to the ability on their LEFT.
     */
    public List<SequenceElement> removeAbility(List<SequenceElement> elements, String abilityKey) {
        List<SequenceElement> result = new ArrayList<>(elements);

        for (int i = 0; i < result.size(); i++) {
            if (result.get(i).isAbility() && result.get(i).getValue().equals(abilityKey)) {
                result.remove(i);

                // Remove operator to the RIGHT if it exists
                if (i < result.size() && result.get(i).isSeparator()) {
                    result.remove(i);
                }

                cleanupAfterRemoval(result, i);
                break;
            }
        }

        return result;
    }

    /**
     * Inserts ability with proper grouping based on drop zone.
     */
    public List<SequenceElement> insertAbility(List<SequenceElement> elements, String abilityKey,
                                                int insertIndex, DropZoneType zoneType) {
        List<SequenceElement> result = new ArrayList<>(elements);

        if (result.isEmpty()) {
            result.add(SequenceElement.ability(abilityKey));
            return result;
        }

        switch (zoneType) {
            case AND:
                insertWithGrouping(result, abilityKey, insertIndex, SequenceElement.Type.PLUS);
                break;
            case OR:
                insertWithGrouping(result, abilityKey, insertIndex, SequenceElement.Type.SLASH);
                break;
            case NEXT:
                insertAsNextStep(result, abilityKey, insertIndex);
                break;
        }

        return result;
    }

    /**
     * Inserts ability into an AND or OR group.
     * Key rule: If target is already in a group, extend that group (prevents mixing separators).
     */
    private void insertWithGrouping(List<SequenceElement> elements,
                                    String abilityKey,
                                    int insertIndex,
                                    SequenceElement.Type groupType) {
        if (elements.isEmpty()) {
            elements.add(SequenceElement.ability(abilityKey));
            return;
        }

        insertIndex = Math.max(0, Math.min(insertIndex, elements.size()));

        SequenceElement separator =
            groupType == SequenceElement.Type.PLUS ? SequenceElement.plus() : SequenceElement.slash();

        // Ensure we're targeting an ability
        if (insertIndex >= elements.size() || !elements.get(insertIndex).isAbility()) {
            insertAsNextStep(elements, abilityKey, insertIndex);
            return;
        }

        // Check if target is already in a group - if so, use that group's separator type
        SequenceElement.Type existingGroupType = detectExistingGroupType(elements, insertIndex);

        if (existingGroupType != null) {
            separator = existingGroupType == SequenceElement.Type.PLUS ?
                SequenceElement.plus() : SequenceElement.slash();
        }

        // Insert separator + new ability right after target
        int insertPosition = insertIndex + 1;
        elements.add(insertPosition, separator);
        elements.add(insertPosition + 1, SequenceElement.ability(abilityKey));
    }

    /**
     * Detects if an ability is already part of a group (has + or / separator adjacent).
     */
    private SequenceElement.Type detectExistingGroupType(List<SequenceElement> elements, int abilityIndex) {
        // Check separator after target
        int checkAfter = abilityIndex + 1;
        if (checkAfter < elements.size() && elements.get(checkAfter).isSeparator()) {
            SequenceElement sep = elements.get(checkAfter);
            if (sep.isPlus() || sep.isSlash()) {
                return sep.getType();
            }
        }

        // Check separator before target
        int checkBefore = abilityIndex - 1;
        if (checkBefore >= 0 && elements.get(checkBefore).isSeparator()) {
            SequenceElement sep = elements.get(checkBefore);
            if (sep.isPlus() || sep.isSlash()) {
                return sep.getType();
            }
        }

        return null;
    }

    /**
     * Inserts ability as a new sequential step (with arrow separator).
     */
    private void insertAsNextStep(List<SequenceElement> elements, String abilityKey, int insertIndex) {
        insertIndex = Math.max(0, Math.min(insertIndex, elements.size()));

        if (insertIndex == 0) {
            elements.add(0, SequenceElement.ability(abilityKey));
            if (elements.size() > 1 && elements.get(1).isAbility()) {
                elements.add(1, SequenceElement.arrow());
            }
        } else if (insertIndex >= elements.size()) {
            if (!elements.isEmpty() && elements.get(elements.size() - 1).isAbility()) {
                elements.add(SequenceElement.arrow());
            }
            elements.add(SequenceElement.ability(abilityKey));
        } else {
            SequenceElement beforeElement = elements.get(insertIndex - 1);
            SequenceElement atElement = elements.get(insertIndex);

            if (beforeElement.isAbility()) {
                elements.add(insertIndex, SequenceElement.arrow());
                insertIndex++;
            }

            elements.add(insertIndex, SequenceElement.ability(abilityKey));

            if (atElement.isAbility()) {
                elements.add(insertIndex + 1, SequenceElement.arrow());
            }
        }
    }

    /**
     * Cleans up orphaned or duplicate separators after removal.
     */
    private void cleanupAfterRemoval(List<SequenceElement> elements, int removalPoint) {
        if (elements.isEmpty()) return;

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
    }
}