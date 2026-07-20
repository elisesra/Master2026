package evaluation;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertThrows;

class EvaluationResponseValidatorTest {

    private final EvaluationResponseValidator validator = new EvaluationResponseValidator();

    @Test
    void requiresDecimalAndPercentageScores() {
        assertDoesNotThrow(() -> validator.validate("""
                {
                  "correctness_level":"mostly_correct",
                  "score":0.75,
                  "percentage_score":75,
                  "rationale":"Mostly aligned.",
                  "issues":[]
                }
                """));

        assertThrows(IllegalArgumentException.class, () -> validator.validate("""
                {
                  "correctness_level":"mostly_correct",
                  "score":0.75,
                  "rationale":"Mostly aligned.",
                  "issues":[]
                }
                """));

        assertThrows(IllegalArgumentException.class, () -> validator.validate("""
                {
                  "correctness_level":"mostly_correct",
                  "score":0.75,
                  "percentage_score":101,
                  "rationale":"Mostly aligned.",
                  "issues":[]
                }
                """));
    }
}
