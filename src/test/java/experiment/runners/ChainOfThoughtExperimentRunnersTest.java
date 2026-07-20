package experiment.runners;

import com.fasterxml.jackson.databind.JsonNode;
import experiment.core.DataHandler;
import experiment.core.RequirementInput;
import experiment.llm.LlmClient;
import experiment.prompts.ChainOfThought1PromptTemplate;
import experiment.prompts.ChainOfThought2PromptTemplate;
import experiment.validation.ChainOfThought2ResponseValidator;
import experiment.validation.ChainOfThoughtResponseValidator;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;

class ChainOfThoughtExperimentRunnersTest {

    @TempDir
    Path temporaryDirectory;

    @Test
    void cot1CallsOnceWithoutAllocationAndCot2CallsOncePerAllocation() {
        RequirementInput requirement = new RequirementInput(
                "The system shall process observations.",
                List.of("CAS", "QChS", "SS")
        );
        AtomicInteger cot1Calls = new AtomicInteger();
        AtomicInteger cot2Calls = new AtomicInteger();
        LlmClient cot1Client = prompt -> {
            cot1Calls.incrementAndGet();
            return responseFor("CAS");
        };
        LlmClient cot2Client = prompt -> {
            cot2Calls.incrementAndGet();
            return responseFor(targetAllocationFrom(prompt));
        };

        List<JsonNode> cot1Results = cot1(cot1Client).run(
                List.of(requirement),
                temporaryDirectory.resolve("cot1.json")
        );
        List<JsonNode> cot2Results = cot2(cot2Client).run(
                List.of(requirement),
                temporaryDirectory.resolve("cot2.json")
        );

        assertEquals(1, cot1Calls.get());
        assertEquals(3, cot2Calls.get());
        assertEquals(1, cot1Results.size());
        assertEquals(3, cot2Results.size());
        assertFalse(cot1Results.get(0).has("allocation"));
        assertFalse(cot1Results.get(0).has("source_allocations"));
        assertEquals("CAS", cot2Results.get(0).path("target_allocation").asText());
        assertEquals("QChS", cot2Results.get(1).path("target_allocation").asText());
        assertEquals("SS", cot2Results.get(2).path("target_allocation").asText());
    }

    @Test
    void clarusProduces77Cot1And131Cot2CallsReadOnly() {
        List<RequirementInput> requirements = new DataHandler().readRequirements(
                Path.of("datasets", "clarus", "allocation_requirements_cleaned.json")
        );
        AtomicInteger cot1Calls = new AtomicInteger();
        AtomicInteger cot2Calls = new AtomicInteger();

        List<JsonNode> cot1Results = cot1(prompt -> {
            cot1Calls.incrementAndGet();
            return responseFor("CAS");
        }).run(requirements, temporaryDirectory.resolve("clarus-cot1.json"));

        List<JsonNode> cot2Results = cot2(prompt -> {
            cot2Calls.incrementAndGet();
            return responseFor(targetAllocationFrom(prompt));
        }).run(requirements, temporaryDirectory.resolve("clarus-cot2.json"));

        assertEquals(77, cot1Calls.get());
        assertEquals(77, cot1Results.size());
        assertEquals(131, cot2Calls.get());
        assertEquals(131, cot2Results.size());
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

    private static SimpleRunner cot1(LlmClient client) {
        ChainOfThought1PromptTemplate template = new ChainOfThought1PromptTemplate();
        ChainOfThoughtResponseValidator validator = new ChainOfThoughtResponseValidator();
        return new SimpleRunner(template.getPromptStyle(), SimpleRunner.Scope.HLR,
                template::buildPrompt, null, (raw, target) -> validator.validate(raw), null, client);
    }

    private static SimpleRunner cot2(LlmClient client) {
        ChainOfThought2PromptTemplate template = new ChainOfThought2PromptTemplate();
        ChainOfThought2ResponseValidator validator = new ChainOfThought2ResponseValidator();
        return new SimpleRunner(template.getPromptStyle(), SimpleRunner.Scope.ALLOCATION,
                template::buildPrompt, template::allocationDescription, validator::validate, null, client);
    }
}
