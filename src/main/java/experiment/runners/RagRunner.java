package experiment.runners;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import experiment.core.ExperimentEventLogger;
import experiment.core.ExperimentReportWriter;
import experiment.core.RequirementInput;
import experiment.llm.LlmClient;
import experiment.rag.RagContextFormatter;
import experiment.rag.RagRetriever;
import experiment.rag.RetrievedRagChunk;

import java.nio.file.Path;
import java.util.List;
import java.util.function.BiFunction;
import java.util.function.Function;

public final class RagRunner {

    private final String style;
    private final SimpleRunner.Scope scope;
    private final BiFunction<RequirementInput, String, String> prompt;
    private final Function<String, String> allocationName;
    private final SimpleRunner.Validator validator;
    private final RagRetriever retriever;
    private final LlmClient client;
    private final int topK;
    private final String embeddingModel;
    private final ObjectMapper mapper = new ObjectMapper();

    public RagRunner(String style, SimpleRunner.Scope scope,
            BiFunction<RequirementInput, String, String> prompt,
            Function<String, String> allocationName,
            SimpleRunner.Validator validator,
            RagRetriever retriever,
            LlmClient client,
            int topK,
            String embeddingModel) {
        this.style = style;
        this.scope = scope;
        this.prompt = prompt;
        this.allocationName = allocationName == null ? code -> null : allocationName;
        this.validator = validator;
        this.retriever = retriever;
        this.client = client;
        this.topK = topK;
        this.embeddingModel = embeddingModel;
    }

    public List<JsonNode> run(List<RequirementInput> requirements, Path outputFile) {
        if (outputFile == null) {
            throw new IllegalArgumentException("Output file cannot be null.");
        }
        List<SimpleRunner.Case> cases = SimpleRunner.cases(requirements, scope, allocationName, style, false);
        ExperimentEventLogger log = new ExperimentEventLogger(outputFile);
        log.reset();
        List<List<RetrievedRagChunk>> contexts = retriever.retrieveAll(cases.stream().map(this::query).toList(), topK);
        SimpleRunner calls = new SimpleRunner(style, scope, null, allocationName, validator, null, client);
        ArrayNode report = mapper.createArrayNode();

        for (int i = 0; i < cases.size(); i++) {
            SimpleRunner.Case c = cases.get(i);
            List<RetrievedRagChunk> context = contexts.get(i);
            String builtPrompt = prompt.apply(c.input(), RagContextFormatter.format(context));
            JsonNode response = calls.call(log, c, "generate", builtPrompt);
            if (response == null) {
                continue;
            }
            ObjectNode result = SimpleRunner.resultBase(c, scope, mapper);
            result.put("prompt_style", style);
            result.put("embedding_model", embeddingModel);
            result.put("rag_index_fingerprint", retriever.indexInfo().corpusFingerprint());
            result.put("rag_chunker", retriever.indexInfo().chunker());
            result.set("retrieved_context", mapper.valueToTree(context));
            result.put("prompt", builtPrompt);
            result.set("response", response);
            report.add(result);
        }

        ExperimentReportWriter.writeReport(mapper, outputFile, report, style);
        return java.util.stream.StreamSupport.stream(report.spliterator(), false).toList();
    }

    private String query(SimpleRunner.Case c) {
        if (c.targetAllocation() == null) {
            return c.input().highLevelRequirement();
        }
        return c.source().highLevelRequirement() + "\nTarget subsystem: "
                + c.targetSubsystem() + " (" + c.targetAllocation() + ")";
    }
}
