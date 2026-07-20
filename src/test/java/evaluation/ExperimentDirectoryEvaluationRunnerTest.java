package evaluation;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ExperimentDirectoryEvaluationRunnerTest {

    @TempDir
    Path temporaryDirectory;

    @Test
    void writesMinimalPerFileAndGroupedSummaryCsvFiles() throws Exception {
        Path experiments = temporaryDirectory.resolve("experiment/fs1");
        Files.createDirectories(experiments);
        Path generated = experiments.resolve("claude_fs1_clarus.json");
        Files.writeString(generated, """
                [{
                  "high_level_requirement":"The system shall process observations.",
                  "prompt_style":"fs1",
                  "response":{"low_level_requirements":[
                    {"allocation":"CAS","requirement":"The CAS shall process observations."},
                    {"allocation":"CAS","requirement":"The CAS shall store processed observations."}
                  ]}
                }]
                """);
        Path optimizer = temporaryDirectory.resolve("experiment/panacea1/claude_panacea1_clarus.json");
        Files.createDirectories(optimizer.getParent());
        Files.writeString(optimizer, """
                {"prompt_style":"panacea1","iterations":[]}
                """);
        Path dataset = temporaryDirectory.resolve("dataset.json");
        Files.writeString(dataset, """
                [{
                  "high_level_requirement":"The system shall process observations.",
                  "Allocation":["CAS"],
                  "allocation_requirements":{"CAS":{"low_level_requirements":[
                    {"text":"The CAS shall process observations."}
                  ]}}
                }]
                """);
        Path output = temporaryDirectory.resolve("evaluation");

        BertScorer testBertScorer = (candidate, references) ->
                new BertScoreResult(0.8, 0.6, 0.6857);
        var rows = new ExperimentDirectoryEvaluationRunner(
                new GeneratedOutputLoader(),
                new DeterministicEvaluationMethods(),
                testBertScorer,
                new com.fasterxml.jackson.databind.ObjectMapper()
        ).run(
                temporaryDirectory.resolve("experiment"), dataset, "clarus", output
        );

        assertEquals(1, rows.size());
        assertEquals("claude-sonnet-5", rows.get(0).model());
        Path detail = output.resolve("fs1/claude_fs1_clarus_evaluation.csv");
        assertEquals(3, Files.readAllLines(detail).size());
        String detailText = Files.readString(detail);
        assertTrue(detailText.contains("prompt_type,model,dataset"));
        assertTrue(detailText.contains("high_level_requirement"));
        assertTrue(detailText.contains("generated_low_level_requirement"));
        assertTrue(detailText.contains("bert_score_f1"));
        assertTrue(detailText.contains("0.6857"));
        assertTrue(detailText.contains(ExperimentDirectoryEvaluationRunner.METRIC_NAMES));
        String summary = Files.readString(output.resolve("evaluation_summary.csv"));
        assertTrue(summary.contains("file,"));
        assertTrue(summary.contains("prompt_average,"));
        assertTrue(summary.contains("model_average,"));
        assertTrue(summary.contains("overall_average,"));
    }
}
