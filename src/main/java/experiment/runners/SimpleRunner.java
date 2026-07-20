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
import java.util.ArrayList;
import java.util.List;
import java.util.function.BiConsumer;
import java.util.function.Function;

public final class SimpleRunner {

    public enum Scope { SOURCE, HLR, ALLOCATION }

    @FunctionalInterface
    public interface Validator {
        JsonNode validate(String rawResponse, String targetAllocation);
    }

    public record Case(
            int index,
            RequirementInput source,
            RequirementInput input,
            String targetAllocation,
            String targetSubsystem,
            List<String> eventAllocations
    ) {
    }

    private final String style;
    private final Scope scope;
    private final Function<RequirementInput, String> prompt;
    private final Function<String, String> allocationName;
    private final Validator validator;
    private final BiConsumer<ObjectNode, Case> extraResultFields;
    private final LlmClient client;
    private final ObjectMapper mapper = new ObjectMapper();

    public SimpleRunner(
            String style,
            Scope scope,
            Function<RequirementInput, String> prompt,
            Function<String, String> allocationName,
            Validator validator,
            BiConsumer<ObjectNode, Case> extraResultFields,
            LlmClient client
    ) {
        this.style = style;
        this.scope = scope;
        this.prompt = prompt;
        this.allocationName = allocationName == null ? code -> null : allocationName;
        this.validator = validator;
        this.extraResultFields = extraResultFields == null ? (node, c) -> { } : extraResultFields;
        this.client = client;
    }

    public List<JsonNode> run(List<RequirementInput> requirements, Path outputFile) {
        if (outputFile == null) {
            throw new IllegalArgumentException("Output file cannot be null.");
        }
        if (prompt == null) {
            throw new IllegalArgumentException("Prompt builder cannot be null.");
        }
        List<Case> expandedCases = cases(requirements, scope, allocationName, style, scope != Scope.HLR);
        ExperimentEventLogger log = new ExperimentEventLogger(outputFile);
        log.reset();
        ArrayNode report = mapper.createArrayNode();

        for (Case c : expandedCases) {
            String builtPrompt = prompt.apply(c.input());
            JsonNode response = call(log, c, "generate", builtPrompt);
            if (response == null) {
                continue;
            }
            ObjectNode result = resultBase(c, scope, mapper);
            result.put("prompt_style", style);
            extraResultFields.accept(result, c);
            result.put("prompt", builtPrompt);
            result.set("response", response);
            report.add(result);
        }

        ExperimentReportWriter.writeReport(mapper, outputFile, report, style);
        return java.util.stream.StreamSupport.stream(report.spliterator(), false).toList();
    }

    JsonNode call(ExperimentEventLogger log, Case c, String step, String builtPrompt) {
        log.request(style, c.index(), step, c.source().highLevelRequirement(), c.eventAllocations(),
                c.targetAllocation(), c.targetSubsystem(), builtPrompt);
        String raw = null;
        try {
            raw = client.generate(builtPrompt);
            JsonNode response = validator.validate(raw, c.targetAllocation());
            log.response(style, c.index(), step, c.source().highLevelRequirement(),
                    c.targetAllocation(), c.targetSubsystem(), raw, response);
            return response;
        } catch (RuntimeException exception) {
            log.error(style, c.index(), step, c.source().highLevelRequirement(),
                    c.targetAllocation(), c.targetSubsystem(), builtPrompt, raw, exception);
            return null;
        }
    }

    static List<Case> cases(
            List<RequirementInput> requirements,
            Scope scope,
            Function<String, String> allocationName,
            String style,
            boolean keepHlrEventAllocations
    ) {
        if (requirements == null || requirements.isEmpty()) {
            throw new IllegalArgumentException("Requirements cannot be empty.");
        }
        if (scope == null) {
            throw new IllegalArgumentException("Scope cannot be null.");
        }
        Function<String, String> name = allocationName == null ? code -> null : allocationName;
        List<Case> out = new ArrayList<>();
        for (RequirementInput source : requirements) {
            if (scope == Scope.ALLOCATION) {
                if (source.allocation().isEmpty()) {
                    throw new IllegalArgumentException(style + " cannot run an HLR without allocations: "
                            + source.highLevelRequirement());
                }
                for (String target : source.allocation()) {
                    out.add(new Case(out.size() + 1, source,
                            new RequirementInput(source.highLevelRequirement(), List.of(target)),
                            target, name.apply(target), source.allocation()));
                }
            } else {
                RequirementInput input = scope == Scope.SOURCE
                        ? source
                        : new RequirementInput(source.highLevelRequirement(), List.of());
                out.add(new Case(out.size() + 1, source, input, null, null,
                        keepHlrEventAllocations ? source.allocation() : List.of()));
            }
        }
        return out;
    }

    static ObjectNode resultBase(Case c, Scope scope, ObjectMapper mapper) {
        ObjectNode result = mapper.createObjectNode();
        result.put("high_level_requirement", c.source().highLevelRequirement());
        if (scope == Scope.SOURCE) {
            result.set("allocation", mapper.valueToTree(c.source().allocation()));
        } else if (c.targetAllocation() != null) {
            result.set("source_allocations", mapper.valueToTree(c.source().allocation()));
            result.put("target_allocation", c.targetAllocation());
            result.put("target_subsystem", c.targetSubsystem());
        }
        return result;
    }
}
