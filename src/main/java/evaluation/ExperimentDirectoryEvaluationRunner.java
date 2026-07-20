package evaluation;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.stream.Stream;

/** Evaluates a complete experiment directory and writes compact, analysis-ready CSV files. */
public final class ExperimentDirectoryEvaluationRunner {

    public static final String METRIC_NAMES = String.join("|",
            "allocation_correctness",
            "token_f1_similarity",
            "cosine_similarity",
            "rouge_l",
            "bleu",
            "bert_score_precision",
            "bert_score_recall",
            "bert_score_f1",
            "requirement_quality",
            "combined_score"
    );

    private static final String DETAIL_HEADER = String.join(",",
            "prompt_type", "model", "dataset", "source_file", "source_case_index",
            "high_level_requirement", "source_target_allocation", "source_target_subsystem",
            "generated_allocation", "generated_low_level_requirement", "metric_names",
            "allocation_correctness", "token_f1_similarity", "cosine_similarity",
            "rouge_l", "bleu", "bert_score_precision", "bert_score_recall", "bert_score_f1",
            "requirement_quality", "combined_score"
    );
    private static final String FILE_HEADER = String.join(",",
            "prompt_type", "model", "dataset", "source_file", "model_source",
            "metric_names", "expected_cases", "actual_cases", "completion_percentage",
            "generated_llr_count", "allocation_correctness", "token_f1_similarity",
            "cosine_similarity", "rouge_l", "bleu", "bert_score_precision", "bert_score_recall",
            "bert_score_f1", "requirement_quality", "combined_score"
    );
    private static final String SUMMARY_HEADER = "summary_level," + FILE_HEADER;

    private final GeneratedOutputLoader loader;
    private final DeterministicEvaluationMethods methods;
    private final BertScorer bertScorer;
    private final ObjectMapper objectMapper;

    public ExperimentDirectoryEvaluationRunner() {
        this(new GeneratedOutputLoader(), new DeterministicEvaluationMethods(),
                new BertScoreEvaluator(), new ObjectMapper());
    }

    ExperimentDirectoryEvaluationRunner(
            GeneratedOutputLoader loader,
            DeterministicEvaluationMethods methods,
            BertScorer bertScorer,
            ObjectMapper objectMapper
    ) {
        this.loader = loader;
        this.methods = methods;
        this.bertScorer = bertScorer;
        this.objectMapper = objectMapper;
    }

    public List<FileEvaluation> run(
            Path experimentDirectory,
            Path datasetFile,
            String datasetName,
            Path evaluationDirectory
    ) {
        requireDirectory(experimentDirectory, "Experiment directory");
        if (!Files.isRegularFile(datasetFile)) {
            throw new IllegalArgumentException("Dataset file does not exist: " + datasetFile);
        }
        if (datasetName == null || datasetName.isBlank()) {
            throw new IllegalArgumentException("Dataset name cannot be blank.");
        }

        GoldTruthIndex gold = GoldTruthIndex.load(datasetFile);
        List<EvaluatedFile> evaluatedFiles = generationFiles(experimentDirectory).stream()
                .map(path -> evaluate(path, datasetName, gold))
                .flatMap(Stream::ofNullable)
                .sorted(Comparator.comparing((EvaluatedFile file) -> file.summary().promptType())
                        .thenComparing(file -> file.summary().model()))
                .toList();

        for (EvaluatedFile evaluatedFile : evaluatedFiles) {
            FileEvaluation result = evaluatedFile.summary();
            Path output = evaluationDirectory
                    .resolve(result.promptType())
                    .resolve(stripJson(Path.of(result.sourceFile()).getFileName().toString()) + "_evaluation.csv");
            List<String> lines = new ArrayList<>();
            lines.add(DETAIL_HEADER);
            evaluatedFile.cases().forEach(detail -> lines.add(detail.csvRow()));
            write(output, String.join(System.lineSeparator(), lines) + System.lineSeparator());
        }
        List<FileEvaluation> results = evaluatedFiles.stream().map(EvaluatedFile::summary).toList();
        writeSummary(evaluationDirectory.resolve("evaluation_summary.csv"), results);
        return results;
    }

    private EvaluatedFile evaluate(Path file, String dataset, GoldTruthIndex gold) {
        JsonNode root = read(file);
        String prompt = promptStyle(root);
        if (prompt.equals("panacea1") || prompt.equals("panacea2")) {
            return null; // Optimizer artifacts contain intermediate predictions, not final generation output.
        }
        List<EvaluationInputCase> cases = loader.load(file);
        if (cases.isEmpty()) {
            return null;
        }

        Totals totals = new Totals();
        List<CaseEvaluation> caseEvaluations = new ArrayList<>();
        String model = modelFromFileName(file);
        int processedCases = 0;
        for (EvaluationInputCase input : cases) {
            ObjectNode allocation = methods.allocationCorrectness(input, gold);
            ObjectNode similarity = methods.goldTruthSimilarity(input, gold);
            ObjectNode quality = methods.requirementQuality(input);
            double allocationScore = allocation.path("percentage_score").asDouble();
            double tokenF1 = similarity.path("percentage_score").asDouble();
            double cosine = similarity.path("cosine_similarity_percentage").asDouble();
            double rouge = similarity.path("rouge_l_percentage").asDouble();
            double bleu = similarity.path("bleu_percentage").asDouble();
            BertScoreResult bert = bertScorer.score(
                    input.generatedLowLevelRequirement(),
                    gold.goldRequirements(input.highLevelRequirement(), input.generatedAllocation())
            );
            double qualityScore = quality.path("percentage_score").asDouble();
            double combined = round1(allocationScore * 0.35 + tokenF1 * 0.45 + qualityScore * 0.20);
            totals.add(
                    allocationScore, tokenF1, cosine, rouge, bleu,
                    bert.precision(), bert.recall(), bert.f1(), qualityScore
            );
            caseEvaluations.add(new CaseEvaluation(
                    prompt, model, dataset, file.toString(), input.caseIndex(),
                    input.highLevelRequirement(), input.sourceTargetAllocation(), input.sourceTargetSubsystem(),
                    input.generatedAllocation(), input.generatedLowLevelRequirement(), METRIC_NAMES,
                    allocationScore, tokenF1, cosine, rouge, bleu,
                    bert.precision(), bert.recall(), bert.f1(), qualityScore, combined
            ));
            processedCases++;
            if (processedCases % 25 == 0) {
                System.out.println("BERTScore progress: " + file + " " + processedCases + "/" + cases.size());
            }
        }

        int expectedCases = prompt.endsWith("2") ? 131 : 77;
        int actualCases = root.isArray() ? root.size() : root.path("iterations").size();
        FileEvaluation summary = new FileEvaluation(
                prompt, model, dataset, file.toString(), "canonical_filename_mapping", METRIC_NAMES,
                expectedCases, actualCases, round1(actualCases * 100.0 / expectedCases), cases.size(),
                totals.averageAllocation(), totals.averageTokenF1(), totals.averageCosine(),
                totals.averageRouge(), totals.averageBleu(), totals.averageBertPrecision(),
                totals.averageBertRecall(), totals.averageBertF1(), totals.averageQuality(),
                totals.averageCombined()
        );
        System.out.println("Evaluated with BERTScore: " + file + " (" + cases.size() + " LLRs)");
        return new EvaluatedFile(summary, List.copyOf(caseEvaluations));
    }

    private void writeSummary(Path path, List<FileEvaluation> files) {
        List<String> lines = new ArrayList<>();
        lines.add(SUMMARY_HEADER);
        files.forEach(file -> lines.add("file," + file.csvRow()));

        Map<String, List<FileEvaluation>> byPrompt = group(files, FileEvaluation::promptType);
        byPrompt.values().forEach(group -> lines.add(
                "prompt_average," + average(group, group.get(0).promptType(), "ALL_MODELS").csvRow()
        ));
        Map<String, List<FileEvaluation>> byModel = group(files, FileEvaluation::model);
        byModel.values().forEach(group -> lines.add(
                "model_average," + average(group, "ALL_PROMPTS", group.get(0).model()).csvRow()
        ));
        if (!files.isEmpty()) {
            lines.add("overall_average," + average(files, "ALL_PROMPTS", "ALL_MODELS").csvRow());
        }
        write(path, String.join(System.lineSeparator(), lines) + System.lineSeparator());
    }

    private static FileEvaluation average(List<FileEvaluation> rows, String prompt, String model) {
        return new FileEvaluation(
                prompt, model, rows.get(0).dataset(), "MULTIPLE", "canonical_filename_mapping", METRIC_NAMES,
                sumInt(rows, FileEvaluation::expectedCases), sumInt(rows, FileEvaluation::actualCases),
                average(rows, FileEvaluation::completionPercentage),
                sumInt(rows, FileEvaluation::generatedLlrCount),
                average(rows, FileEvaluation::allocationCorrectness),
                average(rows, FileEvaluation::tokenF1Similarity),
                average(rows, FileEvaluation::cosineSimilarity),
                average(rows, FileEvaluation::rougeL),
                average(rows, FileEvaluation::bleu),
                average(rows, FileEvaluation::bertScorePrecision),
                average(rows, FileEvaluation::bertScoreRecall),
                average(rows, FileEvaluation::bertScoreF1),
                average(rows, FileEvaluation::requirementQuality),
                average(rows, FileEvaluation::combinedScore)
        );
    }

    private static <K> Map<K, List<FileEvaluation>> group(
            List<FileEvaluation> rows,
            java.util.function.Function<FileEvaluation, K> keyFunction
    ) {
        Map<K, List<FileEvaluation>> groups = new LinkedHashMap<>();
        for (FileEvaluation row : rows) {
            groups.computeIfAbsent(keyFunction.apply(row), ignored -> new ArrayList<>()).add(row);
        }
        return groups;
    }

    private static int sumInt(List<FileEvaluation> rows, java.util.function.ToIntFunction<FileEvaluation> value) {
        return rows.stream().mapToInt(value).sum();
    }

    private static double average(
            List<FileEvaluation> rows,
            java.util.function.ToDoubleFunction<FileEvaluation> value
    ) {
        return round1(rows.stream().mapToDouble(value).average().orElse(0));
    }

    private static List<Path> generationFiles(Path directory) {
        try (Stream<Path> paths = Files.walk(directory)) {
            return paths.filter(Files::isRegularFile)
                    .filter(path -> path.getFileName().toString().endsWith(".json"))
                    .sorted()
                    .toList();
        } catch (IOException exception) {
            throw new IllegalStateException("Failed to list experiment directory: " + directory, exception);
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
                ? root.get(0).path("prompt_style").asText("unknown")
                : "unknown";
    }

    private static String modelFromFileName(Path file) {
        String name = file.getFileName().toString();
        int separator = name.indexOf('_');
        String prefix = (separator > 0 ? name.substring(0, separator) : "unknown")
                .toLowerCase(Locale.ROOT);
        return switch (prefix) {
            case "mistral" -> "mistral-medium-3.5";
            case "claude" -> "claude-sonnet-5";
            case "gemini" -> "gemini-3.5-flash";
            case "deepseek" -> "deepseek-v4-flash";
            case "command", "cohere" -> "command-a-plus-05-2026";
            default -> prefix;
        };
    }

    private static String stripJson(String fileName) {
        return fileName.endsWith(".json") ? fileName.substring(0, fileName.length() - 5) : fileName;
    }

    private static void requireDirectory(Path path, String label) {
        if (!Files.isDirectory(path)) {
            throw new IllegalArgumentException(label + " does not exist: " + path);
        }
    }

    private static void write(Path path, String content) {
        try {
            Files.createDirectories(path.toAbsolutePath().getParent());
            Files.writeString(path, content, StandardCharsets.UTF_8,
                    StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING);
        } catch (IOException exception) {
            throw new IllegalStateException("Failed to write evaluation output: " + path, exception);
        }
    }

    private static String csv(Object value) {
        String text = String.valueOf(value);
        return '"' + text.replace("\"", "\"\"") + '"';
    }

    private static double round1(double value) {
        return Math.round(value * 10.0) / 10.0;
    }

    public record FileEvaluation(
            String promptType, String model, String dataset, String sourceFile, String modelSource,
            String metricNames, int expectedCases, int actualCases, double completionPercentage,
            int generatedLlrCount, double allocationCorrectness, double tokenF1Similarity,
            double cosineSimilarity, double rougeL, double bleu, double bertScorePrecision,
            double bertScoreRecall, double bertScoreF1, double requirementQuality, double combinedScore
    ) {
        String csvRow() {
            return Stream.of(
                    promptType, model, dataset, sourceFile, modelSource, metricNames,
                    expectedCases, actualCases, completionPercentage, generatedLlrCount,
                    allocationCorrectness, tokenF1Similarity, cosineSimilarity, rougeL, bleu,
                    bertScorePrecision, bertScoreRecall, bertScoreF1,
                    requirementQuality, combinedScore
            ).map(ExperimentDirectoryEvaluationRunner::csv).reduce((left, right) -> left + "," + right).orElse("");
        }
    }

    private record EvaluatedFile(FileEvaluation summary, List<CaseEvaluation> cases) { }

    private record CaseEvaluation(
            String promptType, String model, String dataset, String sourceFile, int sourceCaseIndex,
            String highLevelRequirement, String sourceTargetAllocation, String sourceTargetSubsystem,
            String generatedAllocation, String generatedLowLevelRequirement, String metricNames,
            double allocationCorrectness, double tokenF1Similarity, double cosineSimilarity,
            double rougeL, double bleu, double bertScorePrecision, double bertScoreRecall,
            double bertScoreF1, double requirementQuality, double combinedScore
    ) {
        String csvRow() {
            return Stream.of(
                    promptType, model, dataset, sourceFile, sourceCaseIndex, highLevelRequirement,
                    nullToEmpty(sourceTargetAllocation), nullToEmpty(sourceTargetSubsystem),
                    generatedAllocation, generatedLowLevelRequirement, metricNames,
                    allocationCorrectness, tokenF1Similarity, cosineSimilarity, rougeL, bleu,
                    bertScorePrecision, bertScoreRecall, bertScoreF1,
                    requirementQuality, combinedScore
            ).map(ExperimentDirectoryEvaluationRunner::csv)
                    .reduce((left, right) -> left + "," + right).orElse("");
        }
    }

    private static String nullToEmpty(String value) {
        return value == null ? "" : value;
    }

    private static final class Totals {
        private int count;
        private double allocation;
        private double tokenF1;
        private double cosine;
        private double rouge;
        private double bleu;
        private double bertPrecision;
        private double bertRecall;
        private double bertF1;
        private double quality;
        private double combined;

        void add(
                double allocation,
                double tokenF1,
                double cosine,
                double rouge,
                double bleu,
                double bertPrecision,
                double bertRecall,
                double bertF1,
                double quality
        ) {
            count++;
            this.allocation += allocation;
            this.tokenF1 += tokenF1;
            this.cosine += cosine;
            this.rouge += rouge;
            this.bleu += bleu;
            this.bertPrecision += bertPrecision;
            this.bertRecall += bertRecall;
            this.bertF1 += bertF1;
            this.quality += quality;
            this.combined += allocation * 0.35 + tokenF1 * 0.45 + quality * 0.20;
        }

        double averageAllocation() { return average(allocation); }
        double averageTokenF1() { return average(tokenF1); }
        double averageCosine() { return average(cosine); }
        double averageRouge() { return average(rouge); }
        double averageBleu() { return average(bleu); }
        double averageBertPrecision() { return average(bertPrecision); }
        double averageBertRecall() { return average(bertRecall); }
        double averageBertF1() { return average(bertF1); }
        double averageQuality() { return average(quality); }
        double averageCombined() { return average(combined); }
        private double average(double value) { return count == 0 ? 0 : round1(value / count); }
    }
}
