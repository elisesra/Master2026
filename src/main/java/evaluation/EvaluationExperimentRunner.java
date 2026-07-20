package evaluation;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import experiment.llm.LlmClientFactory;
import experiment.llm.LlmModelConfig;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.List;

public final class EvaluationExperimentRunner {

    private final GeneratedOutputLoader generatedOutputLoader;
    private final EvaluationPromptTemplate promptTemplate;
    private final EvaluationResponseValidator responseValidator;
    private final ObjectMapper objectMapper;

    public EvaluationExperimentRunner() {
        this(
                new GeneratedOutputLoader(),
                new EvaluationPromptTemplate(),
                new EvaluationResponseValidator(),
                new ObjectMapper()
        );
    }

    EvaluationExperimentRunner(
            GeneratedOutputLoader generatedOutputLoader,
            EvaluationPromptTemplate promptTemplate,
            EvaluationResponseValidator responseValidator,
            ObjectMapper objectMapper
    ) {
        this.generatedOutputLoader = generatedOutputLoader;
        this.promptTemplate = promptTemplate;
        this.responseValidator = responseValidator;
        this.objectMapper = objectMapper;
    }

    public List<JsonNode> run(
            Path generatedOutputFile,
            List<LlmModelConfig> judgeModelConfigs,
            Path outputFile
    ) {
        List<EvaluationJudgeModel> judges = judgeModelConfigs.stream()
                .map(config -> EvaluationJudgeModel.fromConfig(config, LlmClientFactory.create(config)))
                .toList();
        return runWithJudges(generatedOutputFile, judges, outputFile);
    }

    public List<JsonNode> runWithJudges(
            Path generatedOutputFile,
            List<EvaluationJudgeModel> judges,
            Path outputFile
    ) {
        if (judges == null || judges.isEmpty()) {
            throw new IllegalArgumentException("At least one judge model is required.");
        }
        if (outputFile == null) {
            throw new IllegalArgumentException("Evaluation output file cannot be null.");
        }

        List<EvaluationInputCase> cases = generatedOutputLoader.load(generatedOutputFile);
        if (cases.isEmpty()) {
            throw new IllegalArgumentException("Generated output file contains no evaluable LLRs.");
        }
        return runCases(generatedOutputFile, cases, judges, outputFile);
    }

    List<JsonNode> runCases(
            Path generatedOutputFile,
            List<EvaluationInputCase> cases,
            List<EvaluationJudgeModel> judges,
            Path outputFile
    ) {
        resetEventLog(outputFile);
        ArrayNode evaluations = objectMapper.createArrayNode();
        int evaluationIndex = 1;

        for (EvaluationInputCase inputCase : cases) {
            String prompt = promptTemplate.buildPrompt(inputCase);
            for (EvaluationJudgeModel judge : judges) {
                appendRequest(outputFile, generatedOutputFile, evaluationIndex, inputCase, judge, prompt);
                String rawResponse = null;
                JsonNode validatedResponse;
                try {
                    rawResponse = judge.client().generate(prompt);
                    validatedResponse = responseValidator.validate(rawResponse);
                    appendResponse(
                            outputFile,
                            generatedOutputFile,
                            evaluationIndex,
                            inputCase,
                            judge,
                            rawResponse,
                            validatedResponse
                    );
                } catch (RuntimeException exception) {
                    appendError(
                            outputFile,
                            generatedOutputFile,
                            evaluationIndex,
                            inputCase,
                            judge,
                            prompt,
                            rawResponse,
                            exception
                    );
                    evaluationIndex++;
                    continue;
                }

                ObjectNode result = objectMapper.createObjectNode();
                result.put("evaluation_index", evaluationIndex);
                result.put("source_output_file", generatedOutputFile.toString());
                result.set("input_case", inputCaseNode(inputCase));
                result.set("judge_model", judgeNode(judge));
                result.put("prompt", prompt);
                result.put("raw_response", rawResponse);
                result.set("evaluation", validatedResponse);
                evaluations.add(result);
                evaluationIndex++;
            }
        }

        ObjectNode report = objectMapper.createObjectNode();
        report.put("experiment_type", "llm_judge_correctness_evaluation");
        report.put("source_output_file", generatedOutputFile.toString());
        report.put("cases_count", cases.size());
        report.put("judge_models_count", judges.size());
        report.put("successful_evaluations_count", evaluations.size());
        report.put("event_log_file", eventLogFileFor(outputFile).toString());
        report.set("evaluations", evaluations);
        writeReport(outputFile, report);
        return List.of(report);
    }

    private void appendRequest(
            Path outputFile,
            Path generatedOutputFile,
            int evaluationIndex,
            EvaluationInputCase inputCase,
            EvaluationJudgeModel judge,
            String prompt
    ) {
        ObjectNode event = baseEvent("request", generatedOutputFile, evaluationIndex, inputCase, judge);
        event.put("prompt", prompt);
        appendEvent(outputFile, event);
    }

    private void appendResponse(
            Path outputFile,
            Path generatedOutputFile,
            int evaluationIndex,
            EvaluationInputCase inputCase,
            EvaluationJudgeModel judge,
            String rawResponse,
            JsonNode validatedResponse
    ) {
        ObjectNode event = baseEvent("response", generatedOutputFile, evaluationIndex, inputCase, judge);
        event.put("raw_response", rawResponse);
        event.set("validated_response", validatedResponse);
        appendEvent(outputFile, event);
    }

    private void appendError(
            Path outputFile,
            Path generatedOutputFile,
            int evaluationIndex,
            EvaluationInputCase inputCase,
            EvaluationJudgeModel judge,
            String prompt,
            String rawResponse,
            RuntimeException exception
    ) {
        ObjectNode event = baseEvent("error", generatedOutputFile, evaluationIndex, inputCase, judge);
        event.put("prompt", prompt);
        if (rawResponse != null) {
            event.put("raw_response", rawResponse);
        }
        event.put("error_type", exception.getClass().getSimpleName());
        event.put("message", exception.getMessage());
        appendEvent(outputFile, event);
    }

    private ObjectNode baseEvent(
            String eventType,
            Path generatedOutputFile,
            int evaluationIndex,
            EvaluationInputCase inputCase,
            EvaluationJudgeModel judge
    ) {
        ObjectNode event = objectMapper.createObjectNode();
        event.put("event", eventType);
        event.put("experiment_type", "llm_judge_correctness_evaluation");
        event.put("source_output_file", generatedOutputFile.toString());
        event.put("evaluation_index", evaluationIndex);
        event.set("input_case", inputCaseNode(inputCase));
        event.set("judge_model", judgeNode(judge));
        return event;
    }

    private ObjectNode inputCaseNode(EvaluationInputCase inputCase) {
        ObjectNode node = objectMapper.createObjectNode();
        node.put("case_index", inputCase.caseIndex());
        node.put("high_level_requirement", inputCase.highLevelRequirement());
        node.put("source_prompt_style", inputCase.sourcePromptStyle());
        putIfPresent(node, "source_target_allocation", inputCase.sourceTargetAllocation());
        putIfPresent(node, "source_target_subsystem", inputCase.sourceTargetSubsystem());
        node.put("generated_allocation", inputCase.generatedAllocation());
        node.put("generated_low_level_requirement", inputCase.generatedLowLevelRequirement());
        return node;
    }

    private ObjectNode judgeNode(EvaluationJudgeModel judge) {
        ObjectNode node = objectMapper.createObjectNode();
        node.put("display_name", judge.displayName());
        node.put("model_name", judge.modelName());
        node.put("provider", judge.provider().name());
        return node;
    }

    private void resetEventLog(Path outputFile) {
        try {
            Path eventLogFile = eventLogFileFor(outputFile);
            Path parent = eventLogFile.getParent();
            if (parent != null) {
                Files.createDirectories(parent);
            }
            Files.deleteIfExists(eventLogFile);
            Files.createFile(eventLogFile);
        } catch (IOException exception) {
            throw new IllegalStateException("Failed to reset evaluation event log.", exception);
        }
    }

    private void appendEvent(Path outputFile, ObjectNode event) {
        try {
            Files.writeString(
                    eventLogFileFor(outputFile),
                    objectMapper.writeValueAsString(event) + System.lineSeparator(),
                    StandardOpenOption.CREATE,
                    StandardOpenOption.APPEND
            );
        } catch (IOException exception) {
            throw new IllegalStateException("Failed to append evaluation event.", exception);
        }
    }

    private void writeReport(Path outputFile, ObjectNode report) {
        try {
            Path parent = outputFile.toAbsolutePath().getParent();
            if (parent != null) {
                Files.createDirectories(parent);
            }
            objectMapper.writerWithDefaultPrettyPrinter().writeValue(outputFile.toFile(), report);
        } catch (IOException exception) {
            throw new IllegalStateException("Failed to write evaluation report: " + outputFile, exception);
        }
    }

    private static Path eventLogFileFor(Path outputFile) {
        Path absoluteReport = outputFile.toAbsolutePath();
        Path parent = absoluteReport.getParent();
        String fileName = absoluteReport.getFileName().toString();
        String eventFileName = fileName.endsWith(".json")
                ? fileName.substring(0, fileName.length() - ".json".length()) + ".jsonl"
                : fileName + ".jsonl";
        return parent == null ? Path.of(eventFileName) : parent.resolve(eventFileName);
    }

    private static void putIfPresent(ObjectNode node, String fieldName, String value) {
        if (value != null) {
            node.put(fieldName, value);
        }
    }
}
