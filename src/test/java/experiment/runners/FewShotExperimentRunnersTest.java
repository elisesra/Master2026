package experiment.runners;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import experiment.core.DataHandler;
import experiment.core.ExperimentEventLogger;
import experiment.core.RequirementInput;
import experiment.llm.LlmClient;
import experiment.prompts.FewShot1PromptTemplate;
import experiment.prompts.FewShot2PromptTemplate;
import experiment.validation.FewShot2ResponseValidator;
import experiment.validation.FewShotResponseValidator;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class FewShotExperimentRunnersTest {

    @TempDir
    Path temporaryDirectory;

    @Test
    void fs1CallsOnceWhileFs2CallsOncePerAllocation() throws Exception {
        RequirementInput requirement = new RequirementInput(
                "The system shall process observations.",
                List.of("CAS", "QChS", "SS")
        );
        AtomicInteger fs1Calls = new AtomicInteger();
        AtomicInteger fs2Calls = new AtomicInteger();
        LlmClient fs1Client = prompt -> {
            fs1Calls.incrementAndGet();
            return responseFor("CAS");
        };
        LlmClient fs2Client = prompt -> {
            fs2Calls.incrementAndGet();
            return responseFor(targetAllocationFrom(prompt));
        };
        Path fs1Output = temporaryDirectory.resolve("fs1.json");
        Path fs2Output = temporaryDirectory.resolve("fs2.json");

        List<JsonNode> fs1Results = fs1(fs1Client).run(List.of(requirement), fs1Output);
        List<JsonNode> fs2Results = fs2(fs2Client).run(List.of(requirement), fs2Output);

        assertEquals(1, fs1Calls.get());
        assertEquals(3, fs2Calls.get());
        assertEquals(1, fs1Results.size());
        assertEquals(3, fs2Results.size());
        assertTrue(Files.isRegularFile(fs1Output));
        assertTrue(Files.isRegularFile(fs2Output));
        assertTrue(Files.isRegularFile(ExperimentEventLogger.eventLogFileFor(fs1Output)));
        assertTrue(Files.isRegularFile(ExperimentEventLogger.eventLogFileFor(fs2Output)));

        JsonNode fs2Report = new ObjectMapper().readTree(fs2Output.toFile());
        assertEquals("CAS", fs2Report.get(0).path("target_allocation").asText());
        assertEquals("Configuration & Administration Service", fs2Report.get(0)
                .path("target_subsystem").asText());
        assertEquals("QChS", fs2Report.get(1).path("target_allocation").asText());
        assertEquals("SS", fs2Report.get(2).path("target_allocation").asText());
    }

    @Test
    void clarusExpandsTo131Fs2CallsReadOnly() {
        List<RequirementInput> requirements = new DataHandler().readRequirements(
                Path.of("datasets", "clarus", "allocation_requirements_cleaned.json")
        );
        AtomicInteger calls = new AtomicInteger();
        LlmClient fakeClient = prompt -> {
            calls.incrementAndGet();
            return responseFor(targetAllocationFrom(prompt));
        };

        List<JsonNode> results = fs2(fakeClient).run(
                requirements,
                temporaryDirectory.resolve("clarus-fs2.json")
        );

        assertEquals(131, calls.get());
        assertEquals(131, results.size());
    }

    @Test
    void eventLogRecordsRequestsResponsesAndErrorsWithoutAbortingWholeRun() throws Exception {
        Path output = temporaryDirectory.resolve("fs2-errors.json");
        AtomicInteger calls = new AtomicInteger();
        LlmClient fakeClient = prompt -> {
            int call = calls.incrementAndGet();
            if (call == 2) {
                return "not json";
            }
            return responseFor(targetAllocationFrom(prompt));
        };
        RequirementInput requirement = new RequirementInput(
                "The system shall process observations.",
                List.of("CAS", "QChS", "SS")
        );

        List<JsonNode> results = fs2(fakeClient).run(List.of(requirement), output);

        Path eventLog = ExperimentEventLogger.eventLogFileFor(output);
        List<String> lines = Files.readAllLines(eventLog);
        assertEquals(3, calls.get());
        assertEquals(2, results.size());
        assertEquals(6, lines.size());
        assertTrue(lines.get(0).contains("\"event\":\"request\""));
        assertTrue(lines.get(1).contains("\"event\":\"response\""));
        assertTrue(lines.get(2).contains("\"event\":\"request\""));
        assertTrue(lines.get(3).contains("\"event\":\"error\""));
        assertTrue(lines.get(4).contains("\"event\":\"request\""));
        assertTrue(lines.get(5).contains("\"event\":\"response\""));
        assertFalse(lines.get(3).contains("\"validated_response\""));
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

    private static SimpleRunner fs1(LlmClient client) {
        FewShot1PromptTemplate template = new FewShot1PromptTemplate();
        FewShotResponseValidator validator = new FewShotResponseValidator();
        return new SimpleRunner(template.getPromptStyle(), SimpleRunner.Scope.SOURCE,
                template::buildPrompt, null, (raw, target) -> validator.validate(raw), null, client);
    }

    private static SimpleRunner fs2(LlmClient client) {
        FewShot2PromptTemplate template = new FewShot2PromptTemplate();
        FewShot2ResponseValidator validator = new FewShot2ResponseValidator();
        return new SimpleRunner(template.getPromptStyle(), SimpleRunner.Scope.ALLOCATION,
                template::buildPrompt, template::allocationDescription, validator::validate, null, client);
    }
}
