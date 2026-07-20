package experiment.runners;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import experiment.core.ExperimentEventLogger;
import experiment.core.ExperimentReportWriter;
import experiment.core.PanaceaAllocationRequirementInput;
import experiment.core.PanaceaRequirementInput;
import experiment.llm.LlmClient;
import experiment.panacea.PanaceaPromptEvaluation;
import experiment.panacea.PanaceaPromptRefinementTool;

import java.nio.file.Path;
import java.util.List;
import java.util.function.BiFunction;
import java.util.function.Function;

public final class PanaceaRunner {

    private final LlmClient client;
    private final PanaceaPromptRefinementTool refiner = new PanaceaPromptRefinementTool();
    private final ObjectMapper mapper = new ObjectMapper();

    public PanaceaRunner(LlmClient client) {
        this.client = client;
    }

    public List<JsonNode> runHlr(String style, String initialPrompt,
            BiFunction<String, String, String> prompt,
            SimpleRunner.Validator validator,
            List<PanaceaRequirementInput> examples,
            Path outputFile) {
        if (examples == null || examples.isEmpty()) {
            throw new IllegalArgumentException(style + " examples cannot be empty.");
        }
        List<Example> all = examples.stream()
                .map(e -> new Example(e.highLevelRequirement(), List.of(), null, null,
                        e.groundTruthLowLevelRequirements()))
                .toList();
        return run(style, "prompt", initialPrompt, (current, e) -> prompt.apply(current, e.hlr()),
                validator, all, outputFile);
    }

    public List<JsonNode> runAllocation(String style, String initialPrompt,
            AllocationPrompt prompt,
            Function<String, String> allocationName,
            SimpleRunner.Validator validator,
            List<PanaceaAllocationRequirementInput> examples,
            Path outputFile) {
        if (examples == null || examples.isEmpty()) {
            throw new IllegalArgumentException(style + " examples cannot be empty.");
        }
        Function<String, String> name = allocationName == null ? code -> null : allocationName;
        List<Example> all = examples.stream()
                .map(e -> new Example(e.highLevelRequirement(), e.sourceAllocations(), e.targetAllocation(),
                        name.apply(e.targetAllocation()), e.groundTruthLowLevelRequirements()))
                .toList();
        return run(style, "allocation_specific_prompt", initialPrompt,
                (current, e) -> prompt.build(current, e.hlr(), e.target()), validator, all, outputFile);
    }

    private List<JsonNode> run(String style, String goal, String initialPrompt,
            Prompt prompt, SimpleRunner.Validator validator, List<Example> examples, Path outputFile) {
        if (examples == null || examples.isEmpty()) {
            throw new IllegalArgumentException(style + " examples cannot be empty.");
        }
        if (outputFile == null) {
            throw new IllegalArgumentException("Output file cannot be null.");
        }
        if (prompt == null) {
            throw new IllegalArgumentException("Prompt builder cannot be null.");
        }
        if (validator == null) {
            throw new IllegalArgumentException("Response validator cannot be null.");
        }
        ExperimentEventLogger log = new ExperimentEventLogger(outputFile);
        log.reset();
        ArrayNode iterations = mapper.createArrayNode();
        String current = initialPrompt;
        double coverage = 0;
        double precision = 0;
        int successes = 0;

        for (int i = 0; i < examples.size(); i++) {
            Example e = examples.get(i);
            String before = current;
            String builtPrompt = prompt.build(before, e);
            refiner.guardGenerationPrompt(builtPrompt, e.truth());
            String raw = null;
            try {
                log.request(style, i + 1, "generate", e.hlr(), e.allocations(), e.target(), e.subsystem(), builtPrompt);
                raw = client.generate(builtPrompt);
                JsonNode prediction = validator.validate(raw, e.target());
                log.response(style, i + 1, "generate", e.hlr(), e.target(), e.subsystem(), raw, prediction);
                PanaceaPromptEvaluation eval = refiner.evaluateAndRefine(before, prediction, e.truth());
                current = eval.updatedPrompt();
                coverage += eval.coverageScore();
                precision += eval.precisionScore();
                successes++;
                iterations.add(iteration(i + 1, e, builtPrompt, prediction, eval, before, current));
            } catch (RuntimeException ex) {
                log.error(style, i + 1, "generate", e.hlr(), e.target(), e.subsystem(), builtPrompt, raw, ex);
            }
        }

        ObjectNode report = mapper.createObjectNode();
        report.put("prompt_style", style);
        report.put("optimization_goal", goal);
        report.put("data_leakage_policy", leakagePolicy(goal));
        report.put("initial_prompt", initialPrompt);
        report.put("final_prompt", current);
        report.put("iterations_count", examples.size());
        report.put("successful_iterations_count", successes);
        report.put("average_coverage_score", successes == 0 ? 0 : coverage / successes);
        report.put("average_precision_score", successes == 0 ? 0 : precision / successes);
        report.set("iterations", iterations);
        ExperimentReportWriter.writeReport(mapper, outputFile, report, style);
        return List.of(report);
    }

    private ObjectNode iteration(int index, Example e, String prompt, JsonNode prediction,
            PanaceaPromptEvaluation eval, String before, String after) {
        ObjectNode node = mapper.createObjectNode();
        node.put("iteration", index);
        node.put("high_level_requirement", e.hlr());
        if (e.target() != null) {
            node.set("source_allocations", mapper.valueToTree(e.allocations()));
            node.put("target_allocation", e.target());
            node.put("target_subsystem", e.subsystem());
            node.put("ground_truth_scope", "target_allocation_only");
        }
        node.put("generation_prompt", prompt);
        node.set("predicted_response", prediction);
        node.put("ground_truth_used_for_evaluation_only", true);
        node.put("ground_truth_llr_count", eval.groundTruthLlrCount());
        node.put("ground_truth_fingerprint", eval.groundTruthFingerprint());
        node.set("evaluation", evaluation(eval));
        node.put("prompt_before", before);
        node.put("prompt_after", after);
        return node;
    }

    private ObjectNode evaluation(PanaceaPromptEvaluation eval) {
        ObjectNode node = mapper.createObjectNode();
        node.put("predicted_llr_count", eval.predictedLlrCount());
        node.put("ground_truth_llr_count", eval.groundTruthLlrCount());
        node.put("coverage_score", eval.coverageScore());
        node.put("precision_score", eval.precisionScore());
        node.put("missing_ground_truth_count", eval.missingGroundTruthCount());
        node.put("extra_prediction_count", eval.extraPredictionCount());
        node.set("issue_codes", mapper.valueToTree(eval.issueCodes()));
        return node;
    }

    private static String leakagePolicy(String goal) {
        if ("allocation_specific_prompt".equals(goal)) {
            return "Ground-truth LLR text is filtered to the target allocation, used only by local evaluation/refinement, and is never inserted into generation prompts or the final prompt.";
        }
        return "Ground-truth LLR text is used only by local evaluation/refinement and is never inserted into generation prompts or the final prompt.";
    }

    @FunctionalInterface
    public interface AllocationPrompt {
        String build(String currentPrompt, String highLevelRequirement, String targetAllocation);
    }

    @FunctionalInterface
    private interface Prompt {
        String build(String currentPrompt, Example example);
    }

    private record Example(String hlr, List<String> allocations, String target, String subsystem, List<String> truth) {
    }
}
