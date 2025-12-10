package com.lansoftprogramming.runeSequence.application;

import com.lansoftprogramming.runeSequence.core.sequence.parser.TooltipMarkupParser;
import com.lansoftprogramming.runeSequence.core.sequence.runtime.SequenceTooltip;
import com.lansoftprogramming.runeSequence.core.sequence.runtime.TooltipSchedule;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

class TooltipScheduleBuilderTest {

	@Test
	void shouldAttachTooltipBetweenAbilityAndArrowToLeftStep() {
		String expression = "A (Left) → B";

		TooltipScheduleBuilder builder = new TooltipScheduleBuilder();
		TooltipScheduleBuilder.BuildResult result = builder.build(expression);

		TooltipSchedule schedule = result.schedule();
		List<SequenceTooltip> step0 = schedule.getTooltipsForStep(0);
		List<SequenceTooltip> step1 = schedule.getTooltipsForStep(1);

		assertEquals(1, step0.size());
		assertEquals("Left", step0.get(0).message());
		assertTrue(step1.isEmpty());
	}

	@Test
	void shouldAttachTooltipBetweenArrowAndAbilityToRightStep() {
		String expression = "A → (Right) B";

		TooltipScheduleBuilder builder = new TooltipScheduleBuilder();
		TooltipScheduleBuilder.BuildResult result = builder.build(expression);

		TooltipSchedule schedule = result.schedule();
		List<SequenceTooltip> step0 = schedule.getTooltipsForStep(0);
		List<SequenceTooltip> step1 = schedule.getTooltipsForStep(1);

		assertTrue(step0.isEmpty());
		assertEquals(1, step1.size());
		assertEquals("Right", step1.get(0).message());
	}

	@Test
	void shouldCollectMultipleTooltipsOnSameStep() {
		String expression = "(First) A (Second) → B";

		TooltipScheduleBuilder builder = new TooltipScheduleBuilder();
		TooltipScheduleBuilder.BuildResult result = builder.build(expression);

		TooltipSchedule schedule = result.schedule();
		List<SequenceTooltip> step0 = schedule.getTooltipsForStep(0);

		assertEquals(2, step0.size());
		List<String> messages = step0.stream().map(SequenceTooltip::message).toList();
		assertTrue(messages.contains("First"));
		assertTrue(messages.contains("Second"));
	}

	@Test
	void shouldAttachTooltipInsideNestedGroupToOuterStep() {
		String expression = "(A (Inner) → B) → C";

		TooltipScheduleBuilder builder = new TooltipScheduleBuilder();
		TooltipScheduleBuilder.BuildResult result = builder.build(expression);

		TooltipSchedule schedule = result.schedule();
		List<SequenceTooltip> step0 = schedule.getTooltipsForStep(0);
		List<SequenceTooltip> step1 = schedule.getTooltipsForStep(1);

		assertEquals(1, step0.size());
		assertEquals("Inner", step0.get(0).message());
		assertTrue(step1.isEmpty());
	}

	@Test
	void shouldAttachTooltipInAndGroupToContainingStep() {
		String expression = "A + (Stack together) B → C";

		TooltipScheduleBuilder builder = new TooltipScheduleBuilder();
		TooltipScheduleBuilder.BuildResult result = builder.build(expression);

		TooltipSchedule schedule = result.schedule();
		List<SequenceTooltip> step0 = schedule.getTooltipsForStep(0);
		List<SequenceTooltip> step1 = schedule.getTooltipsForStep(1);

		assertEquals(1, step0.size());
		assertEquals("Stack together", step0.get(0).message());
		assertTrue(step1.isEmpty());
	}

	@Test
	void shouldAttachTooltipInOrGroupToContainingStep() {
		String expression = "A / (Spec note) B → C";

		TooltipScheduleBuilder builder = new TooltipScheduleBuilder();
		TooltipScheduleBuilder.BuildResult result = builder.build(expression);

		TooltipSchedule schedule = result.schedule();
		List<SequenceTooltip> step0 = schedule.getTooltipsForStep(0);
		List<SequenceTooltip> step1 = schedule.getTooltipsForStep(1);

		assertEquals(1, step0.size());
		assertEquals("Spec note", step0.get(0).message());
		assertTrue(step1.isEmpty());
	}

	@Test
	void shouldFallbackToEmptyScheduleWhenTooltipParsingFails() {
		TooltipMarkupParser failingParser = new TooltipMarkupParser() {
			@Override
			public TooltipMarkupParser.ParseResult parse(String expression) {
				throw new RuntimeException("boom");
			}
		};

		TooltipScheduleBuilder builder = new TooltipScheduleBuilder(failingParser);
		TooltipScheduleBuilder.BuildResult result = builder.build("A→B");

		assertNotNull(result.definition());
		TooltipSchedule schedule = result.schedule();
		assertTrue(schedule.getTooltipsForStep(0).isEmpty());
		assertTrue(schedule.getTooltipsForStep(1).isEmpty());
	}

	@Test
	void shouldNotProduceTooltipsForAbilityParens() {
		TooltipScheduleBuilder builder = new TooltipScheduleBuilder(Set.of("A", "B"));

		TooltipScheduleBuilder.BuildResult leftResult = builder.build("(A) → B");
		TooltipSchedule leftSchedule = leftResult.schedule();
		assertTrue(leftSchedule.getTooltipsForStep(0).isEmpty());
		assertTrue(leftSchedule.getTooltipsForStep(1).isEmpty());

		TooltipScheduleBuilder.BuildResult rightResult = builder.build("A → (B)");
		TooltipSchedule rightSchedule = rightResult.schedule();
		assertTrue(rightSchedule.getTooltipsForStep(0).isEmpty());
		assertTrue(rightSchedule.getTooltipsForStep(1).isEmpty());
	}

	@Test
	void shouldNotProduceTooltipsForKnownAbilityKeyParens() {
		TooltipScheduleBuilder builder = new TooltipScheduleBuilder(Set.of("trueNorth", "trickAttack"));

		TooltipScheduleBuilder.BuildResult result = builder.build("(trueNorth) → trickAttack");
		TooltipSchedule schedule = result.schedule();

		assertTrue(schedule.getTooltipsForStep(0).isEmpty());
		assertTrue(schedule.getTooltipsForStep(1).isEmpty());
	}

	@Test
	void shouldKeepTooltipInsideExtraGroupingParensInSchedule() {
		TooltipScheduleBuilder builder = new TooltipScheduleBuilder(Set.of("A"));

		String expression = "((Stand here)) A";
		TooltipScheduleBuilder.BuildResult result = builder.build(expression);
		TooltipSchedule schedule = result.schedule();

		List<SequenceTooltip> step0 = schedule.getTooltipsForStep(0);
		assertEquals(1, step0.size());
		assertEquals("Stand here", step0.get(0).message());
	}

	@Test
	void shouldAttachTooltipInDeathskullsExampleToTouchOfDeathStep() {
		String expression = "deathskulls→(Hello) touchofdeath/soulsap→necroauto→necroauto";
		Set<String> abilityNames = Set.of("deathskulls", "touchofdeath", "soulsap", "necroauto");

		TooltipScheduleBuilder builder = new TooltipScheduleBuilder(abilityNames);
		TooltipScheduleBuilder.BuildResult result = builder.build(expression);
		TooltipSchedule schedule = result.schedule();

		List<SequenceTooltip> step0 = schedule.getTooltipsForStep(0);
		List<SequenceTooltip> step1 = schedule.getTooltipsForStep(1);
		List<SequenceTooltip> step2 = schedule.getTooltipsForStep(2);
		List<SequenceTooltip> step3 = schedule.getTooltipsForStep(3);

		assertTrue(step0.isEmpty(), "Tooltip should not be on the first step");
		assertEquals(1, step1.size(), "Tooltip should attach to the touchofdeath/soulsap step");
		assertEquals("Hello", step1.get(0).message());
		assertTrue(step2.isEmpty(), "Tooltip should not attach to later steps");
		assertTrue(step3.isEmpty(), "Tooltip should not attach beyond the final step");
	}
}