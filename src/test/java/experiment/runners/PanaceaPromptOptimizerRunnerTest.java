package experiment.runners;

import com.fasterxml.jackson.databind.JsonNode;
import experiment.core.DataHandler;
import experiment.core.PanaceaAllocationRequirementInput;
import experiment.core.PanaceaRequirementInput;
import experiment.llm.LlmClient;
import experiment.prompts.Panacea1PromptTemplate;
import experiment.prompts.Panacea2PromptTemplate;
import experiment.prompts.PanaceaRun1PromptTemplate;
import experiment.prompts.PanaceaRun2PromptTemplate;
import experiment.validation.FewShot2ResponseValidator;
import experiment.validation.FewShotResponseValidator;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class PanaceaPromptOptimizerRunnerTest {

    @TempDir
    Path temporaryDirectory;

    @Test
    void optimizesPromptWithoutLeakingGroundTruthIntoGenerationPromptOrFinalPrompt() {
        String groundTruth = "The CS shall process quality observations exactly as archived reference truth.";
        PanaceaRequirementInput example = new PanaceaRequirementInput(
                "The system shall process observations.",
                List.of(groundTruth)
        );
        AtomicInteger calls = new AtomicInteger();
        AtomicReference<String> generationPrompt = new AtomicReference<>();
        LlmClient client = prompt -> {
            calls.incrementAndGet();
            generationPrompt.set(prompt);
            return """
                    {"low_level_requirements":[
                      {"allocation":"CS","requirement":"The CS shall process observations."}
                    ]}
                    """;
        };

        JsonNode report = panacea1(client)
                .runHlr("panacea1", new Panacea1PromptTemplate().initialPrompt(),
                        new Panacea1PromptTemplate()::buildGenerationPrompt,
                        panacea1Validator(), List.of(example), temporaryDirectory.resolve("panacea.json"))
                .get(0);

        assertEquals(1, calls.get());
        assertEquals("panacea1", report.path("prompt_style").asText());
        assertEquals("prompt", report.path("optimization_goal").asText());
        assertFalse(generationPrompt.get().contains(groundTruth));
        assertFalse(report.path("final_prompt").asText().contains(groundTruth));
        assertFalse(report.toString().contains(groundTruth));
        assertTrue(report.path("iterations").get(0)
                .path("ground_truth_used_for_evaluation_only").asBoolean());
        assertTrue(report.path("iterations").get(0)
                .path("ground_truth_fingerprint").asText().matches("[a-f0-9]{64}"));
    }

    @Test
    void clarusProducesOneGenerationCallPerHlrAndOneFinalPromptReadOnly() {
        List<PanaceaRequirementInput> examples = new DataHandler().readPanaceaRequirements(
                Path.of("datasets", "clarus", "allocation_requirements_cleaned.json")
        );
        AtomicInteger calls = new AtomicInteger();

        JsonNode report = panacea1(prompt -> {
            calls.incrementAndGet();
            return """
                    {"low_level_requirements":[
                      {"allocation":"CAS","requirement":"The CAS shall process observations."}
                    ]}
                    """;
        }).runHlr("panacea1", new Panacea1PromptTemplate().initialPrompt(),
                new Panacea1PromptTemplate()::buildGenerationPrompt,
                panacea1Validator(), examples, temporaryDirectory.resolve("clarus-panacea.json")).get(0);

        assertEquals(77, examples.size());
        assertEquals(77, calls.get());
        assertEquals(77, report.path("iterations_count").asInt());
        assertTrue(report.path("final_prompt").asText().contains("{HLR}"));
    }

    @Test
    void panacea2OptimizesOneTargetAllocationWithoutLeakingTargetGroundTruth() {
        String casGroundTruth = "The CAS shall record the quality checking methods used.";
        PanaceaAllocationRequirementInput example = new PanaceaAllocationRequirementInput(
                "The Clarus system shall record the methods applied when deriving quality checking information.",
                List.of("CAS", "QChS"),
                "CAS",
                List.of(casGroundTruth)
        );
        AtomicInteger calls = new AtomicInteger();
        AtomicReference<String> generationPrompt = new AtomicReference<>();
        LlmClient client = prompt -> {
            calls.incrementAndGet();
            generationPrompt.set(prompt);
            return """
                    {"low_level_requirements":[
                      {"allocation":"CAS","requirement":"The CAS shall record quality checking methods."}
                    ]}
                    """;
        };

        Panacea2PromptTemplate p2 = new Panacea2PromptTemplate();
        JsonNode report = panacea2(client)
                .runAllocation("panacea2", p2.initialPrompt(), p2::buildGenerationPrompt,
                        p2::allocationDescription, panacea2Validator(), List.of(example),
                        temporaryDirectory.resolve("panacea2.json"))
                .get(0);
        JsonNode iteration = report.path("iterations").get(0);

        assertEquals(1, calls.get());
        assertEquals("panacea2", report.path("prompt_style").asText());
        assertEquals("allocation_specific_prompt", report.path("optimization_goal").asText());
        assertEquals("CAS", iteration.path("target_allocation").asText());
        assertEquals("target_allocation_only", iteration.path("ground_truth_scope").asText());
        assertEquals(1, iteration.path("ground_truth_llr_count").asInt());
        assertFalse(generationPrompt.get().contains(casGroundTruth));
        assertFalse(report.path("final_prompt").asText().contains(casGroundTruth));
        assertFalse(report.toString().contains(casGroundTruth));
    }

    @Test
    void panacea2RejectsResponsesForAnotherAllocation() {
        PanaceaAllocationRequirementInput example = new PanaceaAllocationRequirementInput(
                "The system shall process observations.",
                List.of("CAS"),
                "CAS",
                List.of("The CAS shall process observations.")
        );

        Panacea2PromptTemplate invalidTemplate = new Panacea2PromptTemplate();
        JsonNode report = panacea2(prompt -> """
                {"low_level_requirements":[
                  {"allocation":"QChS","requirement":"QChS shall process observations."}
                ]}
                """).runAllocation("panacea2", invalidTemplate.initialPrompt(), invalidTemplate::buildGenerationPrompt,
                invalidTemplate::allocationDescription, panacea2Validator(), List.of(example),
                temporaryDirectory.resolve("panacea2-invalid.json")).get(0);

        assertEquals(1, report.path("iterations_count").asInt());
        assertEquals(0, report.path("successful_iterations_count").asInt());
        assertEquals(0, report.path("iterations").size());
    }

    @Test
    void clarusProducesOnePanacea2CasePerHlrAllocationPairReadOnly() {
        List<PanaceaAllocationRequirementInput> examples = new DataHandler().readPanaceaAllocationRequirements(
                Path.of("datasets", "clarus", "allocation_requirements_cleaned.json")
        );
        AtomicInteger calls = new AtomicInteger();

        Panacea2PromptTemplate clarusTemplate = new Panacea2PromptTemplate();
        JsonNode report = panacea2(prompt -> {
            calls.incrementAndGet();
            return responseFor(targetAllocationFrom(prompt));
        }).runAllocation("panacea2", clarusTemplate.initialPrompt(), clarusTemplate::buildGenerationPrompt,
                clarusTemplate::allocationDescription, panacea2Validator(), examples,
                temporaryDirectory.resolve("clarus-panacea2.json")).get(0);

        assertEquals(131, examples.size());
        assertEquals(131, calls.get());
        assertEquals(131, report.path("iterations_count").asInt());
        assertEquals(131, report.path("successful_iterations_count").asInt());
        assertTrue(report.path("final_prompt").asText().contains("{HLR}"));
        assertTrue(report.path("final_prompt").asText().contains("{TARGET_ALLOCATION}"));
    }

    @Test
    void panaceaRun1UsesFinalPromptFromPanacea1ReportWithoutAllocationScope() throws Exception {
        Path promptReport = temporaryDirectory.resolve("panacea1-report.json");
        java.nio.file.Files.writeString(promptReport, """
                {
                  "prompt_style": "panacea1",
                  "final_prompt": "Optimized final prompt. High-level requirement: {HLR}"
                }
                """);
        AtomicReference<String> promptSeen = new AtomicReference<>();

        PanaceaRun1PromptTemplate run1Template = new PanaceaRun1PromptTemplate();
        JsonNode report = new SimpleRunner("panacea_run1", SimpleRunner.Scope.HLR,
                input -> run1Template.buildPrompt(input, "Optimized final prompt. High-level requirement: {HLR}"),
                null, panacea1Validator(),
                (result, c) -> result.put("optimized_prompt_source", promptReport.toString()),
                prompt -> {
            promptSeen.set(prompt);
            return """
                    {"low_level_requirements":[
                      {"allocation":"CAS","requirement":"The CAS shall process observations."}
                    ]}
                    """;
        }).run(
                List.of(new experiment.core.RequirementInput(
                        "The system shall process observations.",
                        List.of("CAS")
                )),
                temporaryDirectory.resolve("panacea-run1.json")
        ).get(0);

        assertEquals("panacea_run1", report.path("prompt_style").asText());
        assertEquals(promptReport.toString(), report.path("optimized_prompt_source").asText());
        assertEquals(
                "Optimized final prompt. High-level requirement: The system shall process observations.",
                promptSeen.get()
        );
        assertTrue(!report.has("target_allocation"));
    }

    @Test
    void panaceaRun2UsesFinalPromptFromPanacea2ReportForEachTargetAllocation() throws Exception {
        Path promptReport = temporaryDirectory.resolve("panacea2-report.json");
        java.nio.file.Files.writeString(promptReport, """
                {
                  "prompt_style": "panacea2",
                  "final_prompt": "Optimized {HLR} for {TARGET_ALLOCATION} named {TARGET_SUBSYSTEM}\\nTarget allocation code: {TARGET_ALLOCATION}\\n"
                }
                """);
        AtomicInteger calls = new AtomicInteger();
        java.util.List<String> promptsSeen = new java.util.ArrayList<>();

        PanaceaRun2PromptTemplate run2Template = new PanaceaRun2PromptTemplate();
        String optimized = "Optimized {HLR} for {TARGET_ALLOCATION} named {TARGET_SUBSYSTEM}\nTarget allocation code: {TARGET_ALLOCATION}\n";
        List<JsonNode> report = new SimpleRunner("panacea_run2", SimpleRunner.Scope.ALLOCATION,
                input -> run2Template.buildPrompt(input, optimized),
                run2Template::allocationDescription, panacea2Validator(),
                (result, c) -> result.put("optimized_prompt_source", promptReport.toString()),
                prompt -> {
            calls.incrementAndGet();
            promptsSeen.add(prompt);
            return responseFor(targetAllocationFrom(prompt));
        }).run(
                List.of(new experiment.core.RequirementInput(
                        "The system shall process observations.",
                        List.of("CAS", "QChS")
                )),
                temporaryDirectory.resolve("panacea-run2.json")
        );

        assertEquals(2, calls.get());
        assertEquals(2, report.size());
        assertEquals("panacea_run2", report.get(0).path("prompt_style").asText());
        assertEquals("CAS", report.get(0).path("target_allocation").asText());
        assertEquals("QChS", report.get(1).path("target_allocation").asText());
        assertEquals(promptReport.toString(), report.get(0).path("optimized_prompt_source").asText());
        assertTrue(promptsSeen.get(0).contains("for CAS named Configuration & Administration Service"));
        assertTrue(promptsSeen.get(1).contains("for QChS named Quality Checking Services"));
    }

    private static String targetAllocationFrom(String prompt) {
        String marker = "Target allocation code: ";
        int start = prompt.lastIndexOf(marker) + marker.length();
        int end = prompt.indexOf('\n', start);
        return prompt.substring(start, end).trim();
    }

    private static String responseFor(String allocation) {
        return "{\"low_level_requirements\":[{\"allocation\":\"" + allocation
                + "\",\"requirement\":\"The " + allocation + " shall process observations.\"}]}";
    }

    private static PanaceaRunner panacea1(LlmClient client) {
        return new PanaceaRunner(client);
    }

    private static PanaceaRunner panacea2(LlmClient client) {
        return new PanaceaRunner(client);
    }

    private static SimpleRunner.Validator panacea1Validator() {
        FewShotResponseValidator validator = new FewShotResponseValidator();
        return (raw, target) -> validator.validate(raw);
    }

    private static SimpleRunner.Validator panacea2Validator() {
        FewShot2ResponseValidator validator = new FewShot2ResponseValidator();
        return validator::validate;
    }
}
