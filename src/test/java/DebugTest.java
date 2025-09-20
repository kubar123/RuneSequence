// DebugTest.java - Standalone test

import com.lansoftprogramming.runeSequence.sequence.*;

import java.util.List;

public class DebugTest {
	public static void main(String[] args) {
		// Test the specific failing case
		String expr = "Limitless";
		System.out.println("=== Testing: '" + expr + "' ===");

		try {
			// Test parsing directly
			SequenceDefinition sequence = SequenceParser.parse(expr);
			System.out.println("Parsed sequence: " + sequence);
			System.out.println("Steps count: " + sequence.getSteps().size());

			if (!sequence.getSteps().isEmpty()) {
				Step firstStep = sequence.getStep(0);
				System.out.println("First step terms: " + firstStep.getTerms().size());

				for (int i = 0; i < firstStep.getTerms().size(); i++) {
					Term term = firstStep.getTerms().get(i);
					System.out.println("  Term[" + i + "] alternatives: " + term.getAlternatives().size());

					for (int j = 0; j < term.getAlternatives().size(); j++) {
						Alternative alt = term.getAlternatives().get(j);
						System.out.println("    Alt[" + j + "] isToken: " + alt.isToken());
						if (alt.isToken()) {
							System.out.println("      Token: '" + alt.getToken() + "'");
						}
					}
				}
			}

		} catch (Exception e) {
			System.err.println("ERROR: " + e.getMessage());
			e.printStackTrace();
		}

		// Test more cases
		String[] moreTests = {"ability1", "ability1 + ability2"};
		for (String test : moreTests) {
			System.out.println("\n=== Testing: '" + test + "' ===");
			try {
				SequenceDefinition seq = SequenceParser.parse(test);
				System.out.println("Success: " + seq.getSteps().size() + " steps");
			} catch (Exception e) {
				System.err.println("Failed: " + e.getMessage());
			}
		}
	}
}