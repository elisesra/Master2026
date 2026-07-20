package evaluation;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

public final class DeterministicEvaluationRunner {

    private final GeneratedOutputLoader generatedOutputLoader;
    private final DeterministicEvaluationMethods evaluationMethods;
    private final ObjectMapper objectMapper;

    public DeterministicEvaluationRunner() {
        this(new GeneratedOutputLoader(), new DeterministicEvaluationMethods(), new ObjectMapper());
    }

    DeterministicEvaluationRunner(
            GeneratedOutputLoader generatedOutputLoader,
            DeterministicEvaluationMethods evaluationMethods,
            ObjectMapper objectMapper
    ) {
        this.generatedOutputLoader = generatedOutputLoader;
        this.evaluationMethods = evaluationMethods;
        this.objectMapper = objectMapper;
    }

    public List<JsonNode> run(Path generatedOutputFile, Path datasetFile, Path outputFile) {
        if (outputFile == null) {
            throw new IllegalArgumentException("Evaluation output file cannot be null.");
        }
        List<EvaluationInputCase> cases = generatedOutputLoader.load(generatedOutputFile);
        if (cases.isEmpty()) {
            throw new IllegalArgumentException("Generated output file contains no evaluable LLRs.");
        }
        GoldTruthIndex goldTruthIndex = GoldTruthIndex.load(datasetFile);

        ArrayNode evaluations = objectMapper.createArrayNode();
        double allocationTotal = 0;
        double similarityTotal = 0;
        double cosineTotal = 0;
        double rougeTotal = 0;
        double bleuTotal = 0;
        double qualityTotal = 0;

        for (EvaluationInputCase inputCase : cases) {
            ObjectNode allocation = evaluationMethods.allocationCorrectness(inputCase, goldTruthIndex);
            ObjectNode similarity = evaluationMethods.goldTruthSimilarity(inputCase, goldTruthIndex);
            ObjectNode quality = evaluationMethods.requirementQuality(inputCase);

            allocationTotal += allocation.path("percentage_score").asDouble();
            similarityTotal += similarity.path("percentage_score").asDouble();
            cosineTotal += similarity.path("cosine_similarity_percentage").asDouble();
            rougeTotal += similarity.path("rouge_l_percentage").asDouble();
            bleuTotal += similarity.path("bleu_percentage").asDouble();
            qualityTotal += quality.path("percentage_score").asDouble();

            ObjectNode result = objectMapper.createObjectNode();
            result.put("evaluation_index", evaluations.size() + 1);
            result.set("input_case", inputCaseNode(inputCase));
            result.set("allocation_correctness", allocation);
            result.set("gold_truth_text_similarity", similarity);
            result.set("requirement_quality_rules", quality);
            evaluations.add(result);
        }

        ObjectNode summary = objectMapper.createObjectNode();
        summary.put("average_allocation_correctness_percentage", allocationTotal / cases.size());
        summary.put("average_gold_truth_similarity_percentage", similarityTotal / cases.size());
        summary.put("average_cosine_similarity_percentage", cosineTotal / cases.size());
        summary.put("average_rouge_l_percentage", rougeTotal / cases.size());
        summary.put("average_bleu_percentage", bleuTotal / cases.size());
        summary.put("average_requirement_quality_percentage", qualityTotal / cases.size());

        ObjectNode report = objectMapper.createObjectNode();
        report.put("experiment_type", "deterministic_correctness_evaluation");
        report.put("source_output_file", generatedOutputFile.toString());
        report.put("dataset_file", datasetFile.toString());
        report.put("cases_count", cases.size());
        report.set("summary", summary);
        report.set("evaluations", evaluations);
        writeReport(outputFile, report);
        return List.of(report);
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
            throw new IllegalStateException("Failed to write deterministic evaluation report: " + outputFile, exception);
        }
    }
}
