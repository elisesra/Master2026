package experiment.crossvalidating;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import evaluation.EvaluationInputCase;
import experiment.llm.LlmModelConfig;
import experiment.llm.LlmProvider;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class CrossValidationRunnerTest {

    @TempDir
    Path temporaryDirectory;

    @Test
    void writesTwoResumableFilesPerJudgeWithBlindAndGroundTruthPrompts() throws Exception {
        Path experiment = temporaryDirectory.resolve("experiment/fs1");
        Files.createDirectories(experiment);
        Files.writeString(experiment.resolve("claude_fs1_clarus.json"), """
                [{
                  "high_level_requirement":"The system shall process observations.",
                  "prompt_style":"fs1",
                  "response":{"low_level_requirements":[
                    {"allocation":"CAS","requirement":"The CAS shall process observations."}
                  ]}
                }]
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
        AtomicInteger calls = new AtomicInteger();
        StringBuilder prompts = new StringBuilder();
        LlmModelConfig config = LlmModelConfig.builder()
                .provider(LlmProvider.OPENAI_COMPATIBLE)
                .displayName("Judge")
                .modelName("judge-model")
                .baseUrl("http://localhost")
                .build();
        CrossValidationRunner.Judge judge = new CrossValidationRunner.Judge(config, prompt -> {
            calls.incrementAndGet();
            prompts.append(prompt).append("\n---\n");
            return "{\"score\":1.0,\"rationale\":\"Fully aligned.\"}";
        });
        Path output = temporaryDirectory.resolve("crossvalidation");
        CrossValidationRunner runner = new CrossValidationRunner();

        runner.runWithJudges(temporaryDirectory.resolve("experiment"), dataset, "clarus",
                List.of(judge), output, 0, "fs1", 1);
        runner.runWithJudges(temporaryDirectory.resolve("experiment"), dataset, "clarus",
                List.of(judge), output, 0, "fs1", 1);

        assertEquals(2, calls.get());
        Path blind = output.resolve("judge-model_blind.jsonl");
        Path truth = output.resolve("judge-model_ground_truth.jsonl");
        assertEquals(1, Files.readAllLines(blind).size());
        assertEquals(1, Files.readAllLines(truth).size());
        JsonNode blindRow = new ObjectMapper().readTree(Files.readString(blind));
        JsonNode truthRow = new ObjectMapper().readTree(Files.readString(truth));
        assertEquals("claude-sonnet-5", blindRow.path("source_model").asText());
        assertEquals(0, blindRow.path("ground_truth_llr_count").asInt());
        assertEquals(1, truthRow.path("ground_truth_llr_count").asInt());
        assertEquals(1.0, truthRow.path("score").asDouble());
        assertTrue(prompts.toString().contains("No reference low-level requirements are supplied"));
        assertTrue(prompts.toString().contains("Ground-truth LLRs for this HLR"));
    }

    @Test
    void selectsExactlyTheSameNumberOfLlrsFromEverySourceFile() {
        List<CrossValidationRunner.SourceCase> inputs = new java.util.ArrayList<>();
        for (int fileIndex = 1; fileIndex <= 4; fileIndex++) {
            int currentFileIndex = fileIndex;
            Path file = Path.of("results/experiment/fs2/model" + currentFileIndex + "_fs2_clarus.json");
            int count = 100 + currentFileIndex * 10;
            IntStream.range(0, count).forEach(index -> inputs.add(new CrossValidationRunner.SourceCase(
                    file,
                    "model-" + currentFileIndex,
                    new EvaluationInputCase(index + 1, "HLR " + index, "fs2", "CAS", "Service",
                            "CAS", "The CAS shall process item " + index + ".")
            )));
        }

        List<CrossValidationRunner.SourceCase> sampled =
                CrossValidationRunner.equalPerFileSample(inputs, 10);
        Map<Path, Long> counts = sampled.stream().collect(Collectors.groupingBy(
                CrossValidationRunner.SourceCase::sourceFile, Collectors.counting()
        ));

        assertEquals(40, sampled.size());
        assertEquals(4, counts.size());
        assertTrue(counts.values().stream().allMatch(count -> count == 10));
        assertEquals(sampled, CrossValidationRunner.equalPerFileSample(inputs, 10));
    }
}
