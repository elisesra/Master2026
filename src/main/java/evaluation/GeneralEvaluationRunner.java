package evaluation;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public final class GeneralEvaluationRunner {

    private final GeneratedOutputLoader generatedOutputLoader;
    private final DeterministicEvaluationMethods evaluationMethods;
    private final ObjectMapper objectMapper;

    public GeneralEvaluationRunner() {
        this(new GeneratedOutputLoader(), new DeterministicEvaluationMethods(), new ObjectMapper());
    }

    GeneralEvaluationRunner(
            GeneratedOutputLoader generatedOutputLoader,
            DeterministicEvaluationMethods evaluationMethods,
            ObjectMapper objectMapper
    ) {
        this.generatedOutputLoader = generatedOutputLoader;
        this.evaluationMethods = evaluationMethods;
        this.objectMapper = objectMapper;
    }

    public List<JsonNode> run(List<Path> generatedOutputFiles, Path datasetFile, Path outputFile) {
        if (generatedOutputFiles == null || generatedOutputFiles.isEmpty()) {
            throw new IllegalArgumentException("At least one generated output file is required.");
        }
        if (outputFile == null) {
            throw new IllegalArgumentException("General evaluation output file cannot be null.");
        }

        GoldTruthIndex goldTruthIndex = GoldTruthIndex.load(datasetFile);
        ArrayNode skippedFiles = objectMapper.createArrayNode();
        ArrayNode evaluations = objectMapper.createArrayNode();
        Map<String, SummaryAccumulator> summaries = new LinkedHashMap<>();

        for (Path generatedOutputFile : generatedOutputFiles) {
            PromptReportInfo info = promptReportInfo(generatedOutputFile);
            if (info.skipReason() != null) {
                ObjectNode skipped = skippedFiles.addObject();
                skipped.put("file", generatedOutputFile.toString());
                skipped.put("prompt_style", info.promptStyle());
                skipped.put("reason", info.skipReason());
                continue;
            }

            List<EvaluationInputCase> cases = generatedOutputLoader.load(generatedOutputFile);
            if (cases.isEmpty()) {
                ObjectNode skipped = skippedFiles.addObject();
                skipped.put("file", generatedOutputFile.toString());
                skipped.put("prompt_style", info.promptStyle());
                skipped.put("reason", "Generated output file contains no evaluable LLRs.");
                continue;
            }

            for (EvaluationInputCase inputCase : cases) {
                ObjectNode allocation = evaluationMethods.allocationCorrectness(inputCase, goldTruthIndex);
                ObjectNode similarity = evaluationMethods.goldTruthSimilarity(inputCase, goldTruthIndex);
                ObjectNode quality = evaluationMethods.requirementQuality(inputCase);
                double combined = combinedScore(allocation, similarity, quality);
                String key = generatedOutputFile + "\n" + inputCase.sourcePromptStyle();
                summaries.computeIfAbsent(
                        key,
                        ignored -> new SummaryAccumulator(generatedOutputFile, inputCase.sourcePromptStyle())
                ).add(allocation, similarity, quality, combined);

                ObjectNode result = objectMapper.createObjectNode();
                result.put("evaluation_index", evaluations.size() + 1);
                result.put("source_output_file", generatedOutputFile.toString());
                result.set("input_case", inputCaseNode(inputCase));
                result.set("allocation_correctness", allocation);
                result.set("gold_truth_text_similarity", similarity);
                result.set("requirement_quality_rules", quality);
                result.put("combined_score", combined);
                evaluations.add(result);
            }
        }

        ArrayNode summaryNodes = objectMapper.createArrayNode();
        summaries.values().stream()
                .sorted(Comparator.comparing(SummaryAccumulator::combinedAverage).reversed())
                .forEach(summary -> summaryNodes.add(summary.toNode(objectMapper)));

        ArrayNode ranking = objectMapper.createArrayNode();
        int rank = 1;
        for (JsonNode summary : summaryNodes) {
            ObjectNode ranked = ranking.addObject();
            ranked.put("rank", rank++);
            ranked.put("source_output_file", summary.path("source_output_file").asText());
            ranked.put("prompt_style", summary.path("prompt_style").asText());
            ranked.put("combined_score", summary.path("average_combined_score").asDouble());
            ranked.put("allocation_correctness", summary.path("average_allocation_correctness_percentage").asDouble());
            ranked.put("gold_similarity", summary.path("average_gold_truth_similarity_percentage").asDouble());
            ranked.put("cosine_similarity", summary.path("average_cosine_similarity_percentage").asDouble());
            ranked.put("rouge_l", summary.path("average_rouge_l_percentage").asDouble());
            ranked.put("bleu", summary.path("average_bleu_percentage").asDouble());
            ranked.put("requirement_quality", summary.path("average_requirement_quality_percentage").asDouble());
            ranked.put("generated_llr_count", summary.path("generated_llr_count").asInt());
        }

        ObjectNode report = objectMapper.createObjectNode();
        report.put("evaluation_style", "eval_general");
        report.put("dataset_file", datasetFile.toString());
        report.set("generated_files", objectMapper.valueToTree(generatedOutputFiles.stream()
                .map(Path::toString)
                .toList()));
        report.put("evaluated_llr_count", evaluations.size());
        report.set("skipped_files", skippedFiles);
        report.set("ranking", ranking);
        report.set("summaries", summaryNodes);
        report.set("cases", evaluations);
        writeReport(outputFile, report);
        return List.of(report);
    }

    private PromptReportInfo promptReportInfo(Path generatedOutputFile) {
        if (generatedOutputFile == null) {
            throw new IllegalArgumentException("Generated output file cannot be null.");
        }
        if (!Files.isRegularFile(generatedOutputFile)) {
            throw new IllegalArgumentException("Generated output file does not exist: " + generatedOutputFile);
        }
        try {
            JsonNode root = objectMapper.readTree(generatedOutputFile.toFile());
            String promptStyle = promptStyle(root);
            if ("panacea1".equals(promptStyle) || "panacea2".equals(promptStyle)) {
                return new PromptReportInfo(
                        promptStyle,
                        promptStyle + " is prompt optimization output, not a generation result."
                );
            }
            return new PromptReportInfo(promptStyle, null);
        } catch (IOException exception) {
            throw new IllegalStateException("Failed to read generated output file: " + generatedOutputFile, exception);
        }
    }

    private static String promptStyle(JsonNode root) {
        if (root.isObject()) {
            return root.path("prompt_style").asText("unknown");
        }
        if (root.isArray() && !root.isEmpty()) {
            return root.get(0).path("prompt_style").asText("unknown");
        }
        return "unknown";
    }

    private static double combinedScore(ObjectNode allocation, ObjectNode similarity, ObjectNode quality) {
        return round1(
                allocation.path("percentage_score").asDouble() * 0.35
                        + similarity.path("percentage_score").asDouble() * 0.45
                        + quality.path("percentage_score").asDouble() * 0.20
        );
    }

    private ObjectNode inputCaseNode(EvaluationInputCase inputCase) {
        ObjectNode node = objectMapper.createObjectNode();
        node.put("case_index", inputCase.caseIndex());
        node.put("high_level_requirement", inputCase.highLevelRequirement());
        node.put("source_prompt_style", inputCase.sourcePromptStyle());
        if (inputCase.sourceTargetAllocation() != null) {
            node.put("source_target_allocation", inputCase.sourceTargetAllocation());
        }
        if (inputCase.sourceTargetSubsystem() != null) {
            node.put("source_target_subsystem", inputCase.sourceTargetSubsystem());
        }
        node.put("generated_allocation", inputCase.generatedAllocation());
        node.put("generated_low_level_requirement", inputCase.generatedLowLevelRequirement());
        return node;
    }

    private void writeReport(Path outputFile, ObjectNode report) {
        try {
            Path parent = outputFile.toAbsolutePath().getParent();
            if (parent != null) {
                Files.createDirectories(parent);
            }
            objectMapper.writerWithDefaultPrettyPrinter().writeValue(outputFile.toFile(), report);
        } catch (IOException exception) {
            throw new IllegalStateException("Failed to write general evaluation report: " + outputFile, exception);
        }
    }

    private static double round1(double value) {
        return Math.round(value * 10.0) / 10.0;
    }

    private record PromptReportInfo(String promptStyle, String skipReason) {
    }

    private static final class SummaryAccumulator {
        private final Path sourceOutputFile;
        private final String promptStyle;
        private int count;
        private double allocationTotal;
        private double similarityTotal;
        private double cosineTotal;
        private double rougeTotal;
        private double bleuTotal;
        private double qualityTotal;
        private double combinedTotal;

        private SummaryAccumulator(Path sourceOutputFile, String promptStyle) {
            this.sourceOutputFile = sourceOutputFile;
            this.promptStyle = promptStyle;
        }

        private void add(ObjectNode allocation, ObjectNode similarity, ObjectNode quality, double combined) {
            count++;
            allocationTotal += allocation.path("percentage_score").asDouble();
            similarityTotal += similarity.path("percentage_score").asDouble();
            cosineTotal += similarity.path("cosine_similarity_percentage").asDouble();
            rougeTotal += similarity.path("rouge_l_percentage").asDouble();
            bleuTotal += similarity.path("bleu_percentage").asDouble();
            qualityTotal += quality.path("percentage_score").asDouble();
            combinedTotal += combined;
        }

        private double combinedAverage() {
            return count == 0 ? 0 : combinedTotal / count;
        }

        private ObjectNode toNode(ObjectMapper objectMapper) {
            ObjectNode node = objectMapper.createObjectNode();
            node.put("source_output_file", sourceOutputFile.toString());
            node.put("prompt_style", promptStyle);
            node.put("generated_llr_count", count);
            node.put("average_allocation_correctness_percentage", round1(allocationTotal / count));
            node.put("average_gold_truth_similarity_percentage", round1(similarityTotal / count));
            node.put("average_cosine_similarity_percentage", round1(cosineTotal / count));
            node.put("average_rouge_l_percentage", round1(rougeTotal / count));
            node.put("average_bleu_percentage", round1(bleuTotal / count));
            node.put("average_requirement_quality_percentage", round1(qualityTotal / count));
            node.put("average_combined_score", round1(combinedAverage()));
            return node;
        }
    }
}
