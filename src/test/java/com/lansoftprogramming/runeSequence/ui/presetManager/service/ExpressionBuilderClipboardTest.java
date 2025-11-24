package com.lansoftprogramming.runeSequence.ui.presetManager.service;

import com.lansoftprogramming.runeSequence.ui.presetManager.drag.model.DropSide;
import com.lansoftprogramming.runeSequence.ui.presetManager.drag.model.DropZoneType;
import com.lansoftprogramming.runeSequence.ui.presetManager.model.SequenceElement;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

class ExpressionBuilderClipboardTest {

    private final ExpressionBuilder builder = new ExpressionBuilder();

    @Test
    void insertSequence_Next_ShouldSplitGroup_WhenInsertingSequence() {
        // Initial: A + B
        List<SequenceElement> elements = new ArrayList<>(List.of(
                SequenceElement.ability("A"),
                SequenceElement.plus(),
                SequenceElement.ability("B")
        ));

        // Payload: X -> Y
        List<SequenceElement> payload = List.of(
                SequenceElement.ability("X"),
                SequenceElement.arrow(),
                SequenceElement.ability("Y")
        );

        // Insert at index 1 (between A and +)
        // This simulates dropping "X->Y" onto "A" (DropSide LEFT of "+")
        List<SequenceElement> result = builder.insertSequence(elements, payload, 1, DropZoneType.NEXT, DropSide.LEFT);

        // Expected behavior after refactor:
        // The group A+B is split. 
        // A -> X -> Y -> B
        assertEquals("A→X→Y→B", builder.buildExpression(result));
    }

    @Test
    void insertSequence_Next_ShouldMaintainPayloadStructure() {
        // Initial: A -> B
        List<SequenceElement> elements = new ArrayList<>(List.of(
                SequenceElement.ability("A"),
                SequenceElement.arrow(),
                SequenceElement.ability("B")
        ));

        // Payload: C + D (Grouped)
        List<SequenceElement> payload = List.of(
                SequenceElement.ability("C"),
                SequenceElement.plus(),
                SequenceElement.ability("D")
        );
        
        // Insert between A and B (index 2 is B)
        List<SequenceElement> result = builder.insertSequence(elements, payload, 2, DropZoneType.NEXT, DropSide.LEFT);
        
        // Expected: A -> C + D -> B
        assertEquals("A→C+D→B", builder.buildExpression(result));
    }

    @Test
    void insertSequence_And_ShouldInsertSingleAbility() {
         // Initial: A + B
        List<SequenceElement> elements = new ArrayList<>(List.of(
                SequenceElement.ability("A"),
                SequenceElement.plus(),
                SequenceElement.ability("B")
        ));

        // Payload: C
        List<SequenceElement> payload = List.of(SequenceElement.ability("C"));
        
        // Insert into AND group at index 2 (B)
        List<SequenceElement> result = builder.insertSequence(elements, payload, 2, DropZoneType.AND, DropSide.LEFT);
        
        // Expected: A + C + B
        assertEquals("A+C+B", builder.buildExpression(result));
    }
    
    @Test
    void insertSequence_ShouldHandleEmptyPayload() {
        List<SequenceElement> elements = new ArrayList<>(List.of(SequenceElement.ability("A")));
        List<SequenceElement> payload = new ArrayList<>();
        
        List<SequenceElement> result = builder.insertSequence(elements, payload, 0, DropZoneType.NEXT, DropSide.LEFT);
        
        assertEquals("A", builder.buildExpression(result));
    }
    
    @Test
    void insertSequence_ShouldHandleEmptyBase() {
        List<SequenceElement> elements = new ArrayList<>();
        List<SequenceElement> payload = List.of(SequenceElement.ability("A"));
        
        List<SequenceElement> result = builder.insertSequence(elements, payload, 0, DropZoneType.NEXT, DropSide.LEFT);
        
        assertEquals("A", builder.buildExpression(result));
    }
}
