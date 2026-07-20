package evaluation;

import com.fasterxml.jackson.databind.JsonNode;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class GeneralEvaluationRunnerTest {

    @TempDir
    Path temporaryDirectory;

    @Test
    void ranksGenerationReportsAndSkipsPanaceaOptimizers() throws Exception {
        Path dataset = temporaryDirectory.resolve("allocation_requirements_cleaned.json");
        Files.writeString(dataset, """
                [{
                  "high_level_requirement":"The system shall process observations.",
                  "Allocation":["CAS"],
                  "allocation_requirements":{
                    "CAS":{
                      "low_level_requirements":[
                        {"text":"The CAS shall process observations."}
                      ]
                    }
                  }
                }]
                """);
        Path generated = temporaryDirectory.resolve("panacea-run1.json");
        Files.writeString(generated, """
                [{
                  "high_level_requirement":"The system shall process observations.",
                  "prompt_style":"panacea_run1",
                  "response":{
                    "low_level_requirements":[
                      {"allocation":"CAS","requirement":"The CAS shall process observations."}
                    ]
                  }
                }]
                """);
        Path optimizer = temporaryDirectory.resolve("panacea1.json");
        Files.writeString(optimizer, """
                {
                  "prompt_style":"panacea1",
                  "final_prompt":"Create LLRs for {HLR}",
                  "iterations":[]
                }
                """);

        JsonNode report = new GeneralEvaluationRunner()
                .run(List.of(generated, optimizer), dataset, temporaryDirectory.resolve("eval.json"))
                .get(0);

        assertEquals("eval_general", report.path("evaluation_style").asText());
        assertEquals(1, report.path("evaluated_llr_count").asInt());
        assertEquals(1, report.path("skipped_files").size());
        assertEquals("panacea1", report.path("skipped_files").get(0).path("prompt_style").asText());
        assertEquals("panacea_run1", report.path("ranking").get(0).path("prompt_style").asText());
        assertEquals(100, report.path("ranking").get(0).path("cosine_similarity").asInt());
        assertEquals(100, report.path("ranking").get(0).path("rouge_l").asInt());
        assertEquals(100, report.path("ranking").get(0).path("bleu").asInt());
        assertTrue(report.path("ranking").get(0).path("combined_score").asDouble() > 0);
    }
}
