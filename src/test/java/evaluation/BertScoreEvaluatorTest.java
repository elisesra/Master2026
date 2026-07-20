package evaluation;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class BertScoreEvaluatorTest {

    @Test
    void calculatesTokenLevelPrecisionRecallAndF1FromNormalizedContextualEmbeddings() {
        float[][] candidate = {
                {1, 0},
                {0, 1}
        };
        float[][] reference = {
                {1, 0}
        };

        BertScoreResult score = BertScoreEvaluator.calculate(candidate, reference);

        assertEquals(0.5, score.precision());
        assertEquals(1.0, score.recall());
        assertEquals(0.6667, score.f1());
    }

    @Test
    void runsActualBertInferenceFromTheBundledOnnxAssets() {
        try (BertScoreEvaluator evaluator = new BertScoreEvaluator()) {
            BertScoreResult identical = evaluator.score(
                    "The CAS shall process observations.",
                    List.of("The CAS shall process observations.")
            );
            BertScoreResult different = evaluator.score(
                    "The CAS shall process observations.",
                    List.of("The weather is sunny today.")
            );

            assertTrue(identical.f1() > 0.999);
            assertTrue(identical.f1() > different.f1());
        }
    }
}
