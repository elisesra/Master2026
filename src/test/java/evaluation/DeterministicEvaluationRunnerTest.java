package evaluation;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class DeterministicEvaluationRunnerTest {

    @TempDir
    Path temporaryDirectory;

    @Test
    void evaluatesAllocationGoldSimilarityAndRequirementQuality() throws Exception {
        Path generated = temporaryDirectory.resolve("generated.json");
        Files.writeString(generated, """
                [{
                  "high_level_requirement":"The system shall process observations.",
                  "target_allocation":"CAS",
                  "target_subsystem":"Configuration & Administration Service",
                  "prompt_style":"fs2",
                  "response":{
                    "low_level_requirements":[
                      {"allocation":"CAS","requirement":"The CAS shall process observations."}
                    ]
                  }
                }]
                """);
        Path dataset = temporaryDirectory.resolve("allocation_requirements_cleaned.json");
        Files.writeString(dataset, """
                [{
                  "high_level_requirement":"The system shall process observations.",
                  "Allocation":["CAS"],
                  "low level requirement":["The CAS shall process observations."],
                  "allocation_requirements":{
                    "CAS":{
                      "low_level_requirements":[
                        {"id":"LLR-1","text":"The CAS shall process observations."}
                      ]
                    }
                  }
                }]
                """);
        Path output = temporaryDirectory.resolve("deterministic.json");

        var report = new DeterministicEvaluationRunner().run(generated, dataset, output).get(0);

        assertTrue(Files.isRegularFile(output));
        assertEquals("deterministic_correctness_evaluation", report.path("experiment_type").asText());
        assertEquals(1, report.path("cases_count").asInt());
        var evaluation = report.path("evaluations").get(0);
        assertEquals(100, evaluation.path("allocation_correctness").path("percentage_score").asInt());
        assertEquals(100, evaluation.path("gold_truth_text_similarity").path("percentage_score").asInt());
        assertEquals(100, evaluation.path("gold_truth_text_similarity").path("cosine_similarity_percentage").asInt());
        assertEquals(100, evaluation.path("gold_truth_text_similarity").path("rouge_l_percentage").asInt());
        assertEquals(100, evaluation.path("gold_truth_text_similarity").path("bleu_percentage").asInt());
        assertEquals(100, report.path("summary").path("average_cosine_similarity_percentage").asInt());
        assertEquals(100, report.path("summary").path("average_rouge_l_percentage").asInt());
        assertEquals(100, report.path("summary").path("average_bleu_percentage").asInt());
        assertTrue(evaluation.path("requirement_quality_rules").path("percentage_score").asInt() > 80);
    }
}
