package experiment.panacea;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class PanaceaPromptRefinementToolTest {

    private final PanaceaPromptRefinementTool tool = new PanaceaPromptRefinementTool();
    private final ObjectMapper objectMapper = new ObjectMapper();

    @Test
    void refinementUsesGenericGuidanceNotGroundTruthText() throws Exception {
        String truth = "The CS shall store calibrated pavement sensor observations.";
        var prediction = objectMapper.readTree("""
                {"low_level_requirements":[
                  {"allocation":"CS","requirement":"The CS shall store observations."}
                ]}
                """);

        PanaceaPromptEvaluation evaluation = tool.evaluateAndRefine(
                "Create LLRs for {HLR}",
                prediction,
                List.of(truth)
        );

        assertTrue(evaluation.updatedPrompt().contains("{HLR}"));
        assertFalse(evaluation.updatedPrompt().contains(truth));
        assertTrue(evaluation.issueCodes().contains("coverage_gap")
                || evaluation.issueCodes().contains("llr_count_mismatch"));
    }

    @Test
    void leakageGuardRejectsExactGroundTruthInGenerationPrompt() {
        String truth = "The CS shall store calibrated pavement sensor observations.";

        assertThrows(IllegalStateException.class, () ->
                tool.guardGenerationPrompt("Create LLR. " + truth, List.of(truth))
        );
        assertThrows(IllegalStateException.class, () ->
                tool.guardGenerationPrompt("Create LLR from ground truth.", List.of(truth))
        );
    }
}
