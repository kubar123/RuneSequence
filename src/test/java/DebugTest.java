// DebugTest.java - Standalone test

import com.lansoftprogramming.runeSequence.sequence.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;

public class DebugTest {
	private static final Logger logger = LoggerFactory.getLogger(DebugTest.class);

	public static void main(String[] args) {
		// Test the specific failing case
		String expr = "Limitless";
		logger.debug("=== Testing: '{}' ===", expr);

		try {
			// Test parsing directly
			SequenceDefinition sequence = SequenceParser.parse(expr);
			logger.debug("Parsed sequence: {}", sequence);
			logger.debug("Steps count: {}", sequence.getSteps().size());

			if (!sequence.getSteps().isEmpty()) {
				Step firstStep = sequence.getStep(0);
				logger.debug("First step terms: {}", firstStep.getTerms().size());

				for (int i = 0; i < firstStep.getTerms().size(); i++) {
					Term term = firstStep.getTerms().get(i);
					logger.debug("  Term[{}] alternatives: {}", i, term.getAlternatives().size());

					for (int j = 0; j < term.getAlternatives().size(); j++) {
						Alternative alt = term.getAlternatives().get(j);
						logger.debug("    Alt[{}] isToken: {}", j, alt.isToken());
						if (alt.isToken()) {
							logger.debug("      Token: '{}'", alt.getToken());
						}
					}
				}
			}

		} catch (Exception e) {
			logger.error("ERROR: {}", e.getMessage(), e);
		}

		// Test more cases
		String[] moreTests = {"ability1", "ability1 + ability2"};
		for (String test : moreTests) {
			logger.debug("\n=== Testing: '{}' ===", test);
			try {
				SequenceDefinition seq = SequenceParser.parse(test);
				logger.debug("Success: {} steps", seq.getSteps().size());
			} catch (Exception e) {
				logger.error("Failed: {}", e.getMessage());
			}
		}
	}
}