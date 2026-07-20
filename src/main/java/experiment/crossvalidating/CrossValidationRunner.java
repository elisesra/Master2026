package experiment.crossvalidating;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import evaluation.EvaluationInputCase;
import evaluation.GeneratedOutputLoader;
import evaluation.GoldTruthIndex;
import experiment.llm.LlmClient;
import experiment.llm.LlmClientFactory;
import experiment.llm.LlmModelConfig;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Stream;

/** Runs blind and ground-truth-assisted cross-model judging over final generation reports. */
public final class CrossValidationRunner {

    private final GeneratedOutputLoader loader;
    private final CrossValidationPromptTemplate prompts;
    private final CrossValidationResponseValidator validator;
    private final ObjectMapper objectMapper;

    public CrossValidationRunner() {
        this(new GeneratedOutputLoader(), new CrossValidationPromptTemplate(),
                new CrossValidationResponseValidator(), new ObjectMapper());
    }

    CrossValidationRunner(
            GeneratedOutputLoader loader,
            CrossValidationPromptTemplate prompts,
            CrossValidationResponseValidator validator,
            ObjectMapper objectMapper
    ) {
        this.loader = loader;
        this.prompts = prompts;
        this.validator = validator;
        this.objectMapper = objectMapper;
    }

    public void run(
            Path experimentDirectory,
            Path datasetFile,
            String datasetName,
            List<LlmModelConfig> judgeConfigs,
            Path outputDirectory,
            int maximumCases,
            String promptTypeFilter,
            int casesPerFile
    ) {
        List<Judge> judges = judgeConfigs.stream()
                .map(config -> new Judge(config, LlmClientFactory.create(config)))
                .toList();
        runWithJudges(experimentDirectory, datasetFile, datasetName, judges, outputDirectory,
                maximumCases, promptTypeFilter, casesPerFile);
    }

    void runWithJudges(
            Path experimentDirectory,
            Path datasetFile,
            String datasetName,
            List<Judge> judges,
            Path outputDirectory,
            int maximumCases,
            String promptTypeFilter,
            int casesPerFile
    ) {
        if (!Files.isDirectory(experimentDirectory)) {
            throw new IllegalArgumentException("Experiment directory does not exist: " + experimentDirectory);
        }
        if (judges == null || judges.isEmpty()) {
            throw new IllegalArgumentException("At least one cross-validation judge is required.");
        }
        if (maximumCases < 0) {
            throw new IllegalArgumentException("Maximum cases cannot be negative.");
        }
        if (casesPerFile < 0) {
            throw new IllegalArgumentException("Cases per file cannot be negative.");
        }
        if (maximumCases > 0 && casesPerFile > 0) {
            throw new IllegalArgumentException(
                    "Use either a maximum case cap or equal per-file sampling, not both."
            );
        }

        GoldTruthIndex gold = GoldTruthIndex.load(datasetFile);
        List<SourceCase> allCases = selectPrompt(loadCases(experimentDirectory), promptTypeFilter);
        List<SourceCase> sampledCases = equalPerFileSample(allCases, casesPerFile);
        List<SourceCase> cases = maximumCases == 0 || maximumCases >= sampledCases.size()
                ? sampledCases
                : sampledCases.subList(0, maximumCases);

        System.out.println("Cross-validation selected " + cases.size() + " generated LLRs from "
                + cases.stream().map(SourceCase::sourceFile).distinct().count() + " source files.");

        for (Judge judge : judges) {
            for (CrossValidationMode mode : CrossValidationMode.values()) {
                Path output = outputDirectory.resolve(safeFilePart(judge.config().getModelName())
                        + "_" + mode.filePart() + ".jsonl");
                evaluate(judge, mode, cases, gold, datasetName, output);
            }
        }
    }

    private void evaluate(
            Judge judge,
            CrossValidationMode mode,
            List<SourceCase> cases,
            GoldTruthIndex gold,
            String dataset,
            Path output
    ) {
        Set<String> completed = completedIds(output);
        int processed = 0;
        for (SourceCase source : cases) {
            String id = evaluationId(judge.config().getModelName(), mode, source);
            if (completed.contains(id)) {
                continue;
            }
            EvaluationInputCase input = source.input();
            List<String> truth = gold.goldRequirements(input.highLevelRequirement(), null);
            String prompt = mode == CrossValidationMode.BLIND
                    ? prompts.buildBlind(input)
                    : prompts.buildWithGroundTruth(input, truth);
            ObjectNode row = baseRow(id, judge, mode, source, dataset, truth.size());
            try {
                String raw = judge.client().generate(prompt);
                JsonNode validated = validator.validate(raw);
                row.put("status", "success");
                row.put("score", validated.path("score").asDouble());
                row.put("rationale", validated.path("rationale").asText());
                append(output, row);
                completed.add(id);
            } catch (RuntimeException exception) {
                row.put("status", "error");
                row.put("error_type", exception.getClass().getSimpleName());
                row.put("error_message", String.valueOf(exception.getMessage()));
                append(output, row);
            }
            processed++;
            if (processed % 50 == 0) {
                System.out.println(judge.config().getModelName() + " " + mode.filePart()
                        + ": processed " + processed + " new cases");
            }
        }
    }

    private ObjectNode baseRow(
            String id,
            Judge judge,
            CrossValidationMode mode,
            SourceCase source,
            String dataset,
            int groundTruthCount
    ) {
        EvaluationInputCase input = source.input();
        ObjectNode row = objectMapper.createObjectNode();
        row.put("evaluation_id", id);
        row.put("evaluation_mode", mode.filePart());
        row.put("judge_model", judge.config().getModelName());
        row.put("judge_provider", judge.config().getProvider().name());
        row.put("source_model", source.sourceModel());
        row.put("source_prompt_type", input.sourcePromptStyle());
        row.put("dataset", dataset);
        row.put("source_file", source.sourceFile().toString());
        row.put("source_case_index", input.caseIndex());
        row.put("high_level_requirement", input.highLevelRequirement());
        if (input.sourceTargetAllocation() != null) {
            row.put("source_target_allocation", input.sourceTargetAllocation());
        }
        row.put("generated_allocation", input.generatedAllocation());
        row.put("generated_low_level_requirement", input.generatedLowLevelRequirement());
        row.put("ground_truth_llr_count", mode == CrossValidationMode.GROUND_TRUTH ? groundTruthCount : 0);
        row.put("metric_name", "llm_judge_fit_score_0_to_1");
        return row;
    }

    private List<SourceCase> loadCases(Path directory) {
        List<SourceCase> cases = new ArrayList<>();
        for (Path file : generationFiles(directory)) {
            JsonNode root = read(file);
            String promptStyle = promptStyle(root);
            if (promptStyle.equals("panacea1") || promptStyle.equals("panacea2")) {
                continue;
            }
            String sourceModel = canonicalModel(file);
            for (EvaluationInputCase input : loader.load(file)) {
                cases.add(new SourceCase(file, sourceModel, input));
            }
        }
        return List.copyOf(cases);
    }

    private static List<SourceCase> selectPrompt(List<SourceCase> cases, String promptTypeFilter) {
        if (promptTypeFilter == null || promptTypeFilter.isBlank()) {
            return cases;
        }
        return cases.stream()
                .filter(source -> source.input().sourcePromptStyle().equals(promptTypeFilter.trim()))
                .toList();
    }

    static List<SourceCase> equalPerFileSample(List<SourceCase> cases, int casesPerFile) {
        if (casesPerFile == 0) {
            return cases;
        }
        java.util.Map<Path, List<SourceCase>> byFile = new java.util.LinkedHashMap<>();
        for (SourceCase source : cases) {
            byFile.computeIfAbsent(source.sourceFile(), ignored -> new ArrayList<>()).add(source);
        }
        List<SourceCase> sampled = new ArrayList<>();
        for (List<SourceCase> fileCases : byFile.values()) {
            List<SourceCase> deterministicOrder = fileCases.stream()
                    .sorted(java.util.Comparator.comparing(CrossValidationRunner::samplingKey))
                    .toList();
            sampled.addAll(deterministicOrder.subList(
                    0, Math.min(casesPerFile, deterministicOrder.size())
            ));
        }
        return List.copyOf(sampled);
    }

    private static String samplingKey(SourceCase source) {
        EvaluationInputCase input = source.input();
        String identity = String.join("\n", source.sourceFile().toString(),
                String.valueOf(input.caseIndex()), input.generatedAllocation(), input.generatedLowLevelRequirement());
        return UUID.nameUUIDFromBytes(identity.getBytes(StandardCharsets.UTF_8)).toString();
    }

    private static List<Path> generationFiles(Path directory) {
        try (Stream<Path> paths = Files.walk(directory)) {
            return paths.filter(Files::isRegularFile)
                    .filter(path -> path.getFileName().toString().endsWith(".json"))
                    .sorted()
                    .toList();
        } catch (IOException exception) {
            throw new IllegalStateException("Failed to list generation files.", exception);
        }
    }

    private JsonNode read(Path path) {
        try {
            return objectMapper.readTree(path.toFile());
        } catch (IOException exception) {
            throw new IllegalStateException("Failed to read generation file: " + path, exception);
        }
    }

    private static String promptStyle(JsonNode root) {
        if (root.isObject()) {
            return root.path("prompt_style").asText("unknown");
        }
        return root.isArray() && !root.isEmpty()
                ? root.get(0).path("prompt_style").asText("unknown") : "unknown";
    }

    private Set<String> completedIds(Path output) {
        if (!Files.isRegularFile(output)) {
            return new HashSet<>();
        }
        Set<String> result = new HashSet<>();
        try (Stream<String> lines = Files.lines(output)) {
            lines.filter(line -> !line.isBlank()).forEach(line -> {
                try {
                    JsonNode row = objectMapper.readTree(line);
                    if (row.path("status").asText().equals("success")) {
                        result.add(row.path("evaluation_id").asText());
                    }
                } catch (IOException exception) {
                    throw new IllegalStateException("Invalid existing JSONL row in " + output, exception);
                }
            });
        } catch (IOException exception) {
            throw new IllegalStateException("Failed to read existing cross-validation output: " + output, exception);
        }
        return result;
    }

    private void append(Path output, ObjectNode row) {
        try {
            Files.createDirectories(output.toAbsolutePath().getParent());
            Files.writeString(output, objectMapper.writeValueAsString(row) + System.lineSeparator(),
                    StandardCharsets.UTF_8, StandardOpenOption.CREATE, StandardOpenOption.APPEND);
        } catch (IOException exception) {
            throw new IllegalStateException("Failed to append cross-validation output: " + output, exception);
        }
    }

    private static String evaluationId(String judge, CrossValidationMode mode, SourceCase source) {
        EvaluationInputCase input = source.input();
        String identity = String.join("\n", judge, mode.name(), source.sourceFile().toString(),
                String.valueOf(input.caseIndex()), input.generatedAllocation(), input.generatedLowLevelRequirement());
        return UUID.nameUUIDFromBytes(identity.getBytes(StandardCharsets.UTF_8)).toString();
    }

    private static String canonicalModel(Path file) {
        String name = file.getFileName().toString().toLowerCase(Locale.ROOT);
        if (name.startsWith("mistral_")) return "mistral-medium-3.5";
        if (name.startsWith("claude_")) return "claude-sonnet-5";
        if (name.startsWith("gemini_")) return "gemini-3.5-flash";
        if (name.startsWith("deepseek_")) return "deepseek-v4-flash";
        if (name.startsWith("command_") || name.startsWith("cohere_")) return "command-a-plus-05-2026";
        int separator = name.indexOf('_');
        return separator > 0 ? name.substring(0, separator) : "unknown";
    }

    private static String safeFilePart(String value) {
        return value.replaceAll("[^A-Za-z0-9._-]+", "_");
    }

    record SourceCase(Path sourceFile, String sourceModel, EvaluationInputCase input) { }

    record Judge(LlmModelConfig config, LlmClient client) { }
}
