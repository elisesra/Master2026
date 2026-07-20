package experiment.runners;

import com.fasterxml.jackson.databind.JsonNode;
import experiment.core.RequirementInput;
import experiment.rag.EmbeddingClient;
import experiment.rag.RagChunker;
import experiment.rag.RagIndexer;
import experiment.rag.RagRetriever;
import experiment.llm.LlmClient;
import experiment.prompts.Rag1PromptTemplate;
import experiment.prompts.Rag2PromptTemplate;
import experiment.validation.FewShot2ResponseValidator;
import experiment.validation.FewShotResponseValidator;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class RagExperimentRunnersTest {

    @TempDir
    Path temporaryDirectory;

    @Test
    void rag1CallsOnceAndRag2CallsOncePerAllocation() throws Exception {
        Path documents = temporaryDirectory.resolve("documents");
        Files.createDirectories(documents);
        Files.writeString(documents.resolve("reference.txt"), "Subsystem operational reference context.");
        Path index = temporaryDirectory.resolve("rag.db");
        TestEmbeddingClient embeddingClient = new TestEmbeddingClient();
        new RagIndexer(new RagChunker(300, 30), embeddingClient).index(documents, index);
        RequirementInput requirement = new RequirementInput(
                "The system shall process observations.",
                List.of("CAS", "QChS")
        );
        AtomicInteger rag1Calls = new AtomicInteger();
        AtomicInteger rag2Calls = new AtomicInteger();
        LlmClient rag1Client = prompt -> {
            rag1Calls.incrementAndGet();
            return responseFor("CAS");
        };
        LlmClient rag2Client = prompt -> {
            rag2Calls.incrementAndGet();
            return responseFor(targetAllocationFrom(prompt));
        };

        Rag1PromptTemplate rag1Template = new Rag1PromptTemplate();
        FewShotResponseValidator rag1Validator = new FewShotResponseValidator();
        List<JsonNode> rag1 = new RagRunner(
                rag1Template.getPromptStyle(),
                SimpleRunner.Scope.HLR,
                rag1Template::buildPrompt,
                null,
                (raw, target) -> rag1Validator.validate(raw),
                new RagRetriever(index, embeddingClient),
                rag1Client,
                1,
                embeddingClient.modelName()
        ).run(List.of(requirement), temporaryDirectory.resolve("rag1.json"));
        Rag2PromptTemplate rag2Template = new Rag2PromptTemplate();
        FewShot2ResponseValidator rag2Validator = new FewShot2ResponseValidator();
        List<JsonNode> rag2 = new RagRunner(
                rag2Template.getPromptStyle(),
                SimpleRunner.Scope.ALLOCATION,
                rag2Template::buildPrompt,
                rag2Template::allocationDescription,
                rag2Validator::validate,
                new RagRetriever(index, embeddingClient),
                rag2Client,
                1,
                embeddingClient.modelName()
        ).run(List.of(requirement), temporaryDirectory.resolve("rag2.json"));

        assertEquals(1, rag1Calls.get());
        assertEquals(2, rag2Calls.get());
        assertEquals(1, rag1.size());
        assertEquals(2, rag2.size());
        assertFalse(rag1.get(0).has("allocation"));
        assertTrue(rag1.get(0).path("retrieved_context").isArray());
        assertEquals("CAS", rag2.get(0).path("target_allocation").asText());
        assertEquals("QChS", rag2.get(1).path("target_allocation").asText());
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

    private static final class TestEmbeddingClient implements EmbeddingClient {
        @Override
        public String modelName() {
            return "test-embedding";
        }

        @Override
        public List<double[]> embed(List<String> texts) {
            List<double[]> vectors = new ArrayList<>();
            texts.forEach(text -> vectors.add(new double[]{1, 0.5, 0.25}));
            return vectors;
        }
    }
}
