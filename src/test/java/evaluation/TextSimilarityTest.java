package evaluation;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class TextSimilarityTest {

    @Test
    void exactMatchesScoreOneAcrossMetrics() {
        String text = "The CAS shall process observations.";

        assertEquals(1.0, TextSimilarity.tokenF1(text, text));
        assertEquals(1.0, TextSimilarity.cosineSimilarity(text, text));
        assertEquals(1.0, TextSimilarity.rougeL(text, text));
        assertEquals(1.0, TextSimilarity.bleu(text, text));
    }

    @Test
    void partialMatchesScoreBetweenZeroAndOne() {
        String generated = "The CAS shall process environmental observations.";
        String reference = "The CAS shall process observations.";

        assertTrue(TextSimilarity.cosineSimilarity(generated, reference) > 0);
        assertTrue(TextSimilarity.rougeL(generated, reference) > 0);
        assertTrue(TextSimilarity.bleu(generated, reference) > 0);
    }
}
