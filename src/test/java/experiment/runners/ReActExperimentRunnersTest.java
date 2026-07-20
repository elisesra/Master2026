package experiment.runners;

import com.fasterxml.jackson.databind.JsonNode;
import experiment.core.DataHandler;
import experiment.core.RequirementInput;
import experiment.llm.LlmClient;
import experiment.prompts.ReAct1PromptTemplate;
import experiment.prompts.ReAct2PromptTemplate;
import experiment.validation.FewShot2ResponseValidator;
import experiment.validation.FewShotResponseValidator;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;

class ReActExperimentRunnersTest {

    @TempDir
    Path temporaryDirectory;

    @Test
    void react1CallsTwiceWithoutAllocationAndReact2CallsTwicePerAllocation() {
        RequirementInput requirement = new RequirementInput(
                "The system shall process observations.",
                List.of("CAS", "QChS", "SS")
        );
        AtomicInteger react1Calls = new AtomicInteger();
        AtomicInteger react2Calls = new AtomicInteger();
        LlmClient react1Client = prompt -> {
            react1Calls.incrementAndGet();
            return responseFor("CAS");
        };
        LlmClient react2Client = prompt -> {
            react2Calls.incrementAndGet();
            return responseFor(targetAllocationFrom(prompt));
        };

        List<JsonNode> react1Results = react1(react1Client).run(
                List.of(requirement),
                temporaryDirectory.resolve("react1.json")
        );
        List<JsonNode> react2Results = react2(react2Client).run(
                List.of(requirement),
                temporaryDirectory.resolve("react2.json")
        );

        assertEquals(2, react1Calls.get());
        assertEquals(6, react2Calls.get());
        assertEquals(1, react1Results.size());
        assertEquals(3, react2Results.size());
        assertFalse(react1Results.get(0).has("allocation"));
        assertFalse(react1Results.get(0).has("source_allocations"));
        assertEquals(2, react1Results.get(0).path("react_trace").size());
        assertEquals("ISO/IEC/IEEE 29148-inspired", react1Results.get(0)
                .path("quality_standard").asText());
        assertEquals("draft_llrs", react2Results.get(0).path("react_trace").get(0)
                .path("action").asText());
        assertEquals("assess_iso_quality_and_revise", react2Results.get(0)
                .path("react_trace").get(1).path("action").asText());
        assertEquals("CAS", react2Results.get(0).path("target_allocation").asText());
        assertEquals("QChS", react2Results.get(1).path("target_allocation").asText());
        assertEquals("SS", react2Results.get(2).path("target_allocation").asText());
    }

    @Test
    void clarusProduces154React1And262React2CallsReadOnly() {
        List<RequirementInput> requirements = new DataHandler().readRequirements(
                Path.of("datasets", "clarus", "allocation_requirements_cleaned.json")
        );
        AtomicInteger react1Calls = new AtomicInteger();
        AtomicInteger react2Calls = new AtomicInteger();

        List<JsonNode> react1Results = react1(prompt -> {
            react1Calls.incrementAndGet();
            return responseFor("CAS");
        }).run(requirements, temporaryDirectory.resolve("clarus-react1.json"));

        List<JsonNode> react2Results = react2(prompt -> {
            react2Calls.incrementAndGet();
            return responseFor(targetAllocationFrom(prompt));
        }).run(requirements, temporaryDirectory.resolve("clarus-react2.json"));

        assertEquals(154, react1Calls.get());
        assertEquals(77, react1Results.size());
        assertEquals(262, react2Calls.get());
        assertEquals(131, react2Results.size());
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

    private static ReActRunner react1(LlmClient client) {
        ReAct1PromptTemplate template = new ReAct1PromptTemplate();
        FewShotResponseValidator validator = new FewShotResponseValidator();
        return new ReActRunner(template.getPromptStyle(), SimpleRunner.Scope.HLR,
                template::buildPrompt, template::buildAssessmentPrompt, null, template::isoCriteria,
                (raw, target) -> validator.validate(raw), client);
    }

    private static ReActRunner react2(LlmClient client) {
        ReAct2PromptTemplate template = new ReAct2PromptTemplate();
        FewShot2ResponseValidator validator = new FewShot2ResponseValidator();
        return new ReActRunner(template.getPromptStyle(), SimpleRunner.Scope.ALLOCATION,
                template::buildPrompt, template::buildAssessmentPrompt, template::allocationDescription,
                template::isoCriteria, validator::validate, client);
    }
}
