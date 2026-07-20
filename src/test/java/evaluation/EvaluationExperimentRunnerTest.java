package evaluation;

import com.fasterxml.jackson.databind.ObjectMapper;
import experiment.llm.LlmProvider;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class EvaluationExperimentRunnerTest {

    @TempDir
    Path temporaryDirectory;

    @Test
    void evaluatesEveryInputCaseWithEveryJudgeAndWritesEventLog() throws Exception {
        Path generated = generatedReport();
        Path output = temporaryDirectory.resolve("evaluation_report.json");
        AtomicInteger calls = new AtomicInteger();
        List<EvaluationJudgeModel> judges = List.of(
                new EvaluationJudgeModel("Judge A", "judge-a", LlmProvider.OPENAI, prompt -> {
                    calls.incrementAndGet();
                    return evaluationResponse(1.0, "correct");
                }),
                new EvaluationJudgeModel("Judge B", "judge-b", LlmProvider.MISTRAL, prompt -> {
                    calls.incrementAndGet();
                    return evaluationResponse(0.75, "mostly_correct");
                })
        );

        var report = new EvaluationExperimentRunner().runWithJudges(generated, judges, output).get(0);

        assertEquals(4, calls.get());
        assertEquals(2, report.path("cases_count").asInt());
        assertEquals(2, report.path("judge_models_count").asInt());
        assertEquals(4, report.path("successful_evaluations_count").asInt());
        assertEquals(4, report.path("evaluations").size());
        assertTrue(Files.isRegularFile(output));
        assertEquals(8, Files.readAllLines(temporaryDirectory.resolve("evaluation_report.jsonl")).size());
    }

    @Test
    void recordsJudgeErrorsWithoutAbortingWholeEvaluation() throws Exception {
        Path generated = generatedReport();
        Path output = temporaryDirectory.resolve("evaluation_errors.json");
        AtomicInteger calls = new AtomicInteger();
        List<EvaluationJudgeModel> judges = List.of(
                new EvaluationJudgeModel("Judge A", "judge-a", LlmProvider.OPENAI, prompt -> {
                    int call = calls.incrementAndGet();
                    return call == 2 ? "not json" : evaluationResponse(1.0, "correct");
                })
        );

        var report = new EvaluationExperimentRunner().runWithJudges(generated, judges, output).get(0);

        assertEquals(2, calls.get());
        assertEquals(1, report.path("successful_evaluations_count").asInt());
        List<String> events = Files.readAllLines(temporaryDirectory.resolve("evaluation_errors.jsonl"));
        assertEquals(4, events.size());
        assertTrue(events.get(0).contains("\"event\":\"request\""));
        assertTrue(events.get(1).contains("\"event\":\"response\""));
        assertTrue(events.get(2).contains("\"event\":\"request\""));
        assertTrue(events.get(3).contains("\"event\":\"error\""));
    }

    private Path generatedReport() throws Exception {
        Path generated = temporaryDirectory.resolve("generated.json");
        Files.writeString(generated, """
                [{
                  "high_level_requirement":"The system shall process observations.",
                  "target_allocation":"CAS",
                  "target_subsystem":"Configuration & Administration Service",
                  "prompt_style":"fs2",
                  "response":{
                    "low_level_requirements":[
                      {"allocation":"CAS","requirement":"The CAS shall configure processing."},
                      {"allocation":"CAS","requirement":"The CAS shall store configuration."}
                    ]
                  }
                }]
                """);
        return generated;
    }

    private static String evaluationResponse(double score, String level) {
        return new ObjectMapper().createObjectNode()
                .put("correctness_level", level)
                .put("score", score)
                .put("percentage_score", Math.round(score * 100))
                .put("rationale", "The generated requirement is aligned with the HLR.")
                .set("issues", new ObjectMapper().createArrayNode())
                .toString();
    }
}
