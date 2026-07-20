package experiment.runners;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import experiment.core.ExperimentEventLogger;
import experiment.core.ExperimentReportWriter;
import experiment.core.RequirementInput;
import experiment.llm.LlmClient;

import java.nio.file.Path;
import java.util.List;
import java.util.function.BiFunction;
import java.util.function.Function;
import java.util.function.Supplier;

public final class ReActRunner {

    private final String style;
    private final SimpleRunner.Scope scope;
    private final Function<RequirementInput, String> draftPrompt;
    private final BiFunction<RequirementInput, String, String> revisePrompt;
    private final Function<String, String> allocationName;
    private final Supplier<List<String>> criteria;
    private final SimpleRunner.Validator validator;
    private final LlmClient client;
    private final ObjectMapper mapper = new ObjectMapper();

    public ReActRunner(String style, SimpleRunner.Scope scope,
            Function<RequirementInput, String> draftPrompt,
            BiFunction<RequirementInput, String, String> revisePrompt,
            Function<String, String> allocationName,
            Supplier<List<String>> criteria,
            SimpleRunner.Validator validator,
            LlmClient client) {
        this.style = style;
        this.scope = scope;
        this.draftPrompt = draftPrompt;
        this.revisePrompt = revisePrompt;
        this.allocationName = allocationName == null ? code -> null : allocationName;
        this.criteria = criteria;
        this.validator = validator;
        this.client = client;
    }

    public List<JsonNode> run(List<RequirementInput> requirements, Path outputFile) {
        if (outputFile == null) {
            throw new IllegalArgumentException("Output file cannot be null.");
        }
        List<SimpleRunner.Case> cases = SimpleRunner.cases(requirements, scope, allocationName, style, true);
        ExperimentEventLogger log = new ExperimentEventLogger(outputFile);
        log.reset();
        SimpleRunner calls = new SimpleRunner(style, scope, null, allocationName, validator, null, client);
        ArrayNode report = mapper.createArrayNode();

        for (SimpleRunner.Case c : cases) {
            String draft = draftPrompt.apply(c.input());
            JsonNode draftResponse = calls.call(log, c, "draft_llrs", draft);
            if (draftResponse == null) {
                continue;
            }
            String revise = revisePrompt.apply(c.input(), draftResponse.toString());
            JsonNode finalResponse = calls.call(log, c, "assess_iso_quality_and_revise", revise);
            if (finalResponse == null) {
                continue;
            }
            ObjectNode result = SimpleRunner.resultBase(c, scope, mapper);
            result.put("prompt_style", style);
            result.put("quality_standard", "ISO/IEC/IEEE 29148-inspired");
            result.set("iso_quality_criteria", mapper.valueToTree(criteria.get()));
            result.set("react_trace", trace(draft, draftResponse, revise, finalResponse));
            result.set("response", finalResponse);
            report.add(result);
        }

        ExperimentReportWriter.writeReport(mapper, outputFile, report, style);
        return java.util.stream.StreamSupport.stream(report.spliterator(), false).toList();
    }

    private ArrayNode trace(String draftPrompt, JsonNode draft, String revisePrompt, JsonNode revised) {
        ArrayNode trace = mapper.createArrayNode();
        ObjectNode first = trace.addObject();
        first.put("step", 1);
        first.put("action", "draft_llrs");
        first.put("prompt", draftPrompt);
        first.set("observation", draft);
        ObjectNode second = trace.addObject();
        second.put("step", 2);
        second.put("action", "assess_iso_quality_and_revise");
        second.put("prompt", revisePrompt);
        second.set("observation", revised);
        return trace;
    }
}
