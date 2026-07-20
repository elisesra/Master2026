package evaluation;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

public final class DeterministicEvaluationMethods {

    private final ObjectMapper objectMapper = new ObjectMapper();

    public ObjectNode allocationCorrectness(EvaluationInputCase inputCase, GoldTruthIndex goldTruthIndex) {
        boolean correct;
        String rationale;
        if (inputCase.sourceTargetAllocation() != null) {
            correct = inputCase.sourceTargetAllocation().equals(inputCase.generatedAllocation());
            rationale = correct
                    ? "Generated allocation matches the target allocation for this case."
                    : "Generated allocation does not match the target allocation for this case.";
        } else {
            correct = goldTruthIndex.hasAllocation(inputCase.highLevelRequirement(), inputCase.generatedAllocation());
            rationale = correct
                    ? "Generated allocation exists among the gold allocations for this HLR."
                    : "Generated allocation is not present among the gold allocations for this HLR.";
        }

        ObjectNode node = objectMapper.createObjectNode();
        node.put("method", "allocation_correctness");
        node.put("correct", correct);
        node.put("percentage_score", correct ? 100 : 0);
        node.put("rationale", rationale);
        return node;
    }

    public ObjectNode goldTruthSimilarity(EvaluationInputCase inputCase, GoldTruthIndex goldTruthIndex) {
        List<String> goldRequirements = goldTruthIndex.goldRequirements(
                inputCase.highLevelRequirement(),
                inputCase.generatedAllocation()
        );
        double bestScore = 0;
        double bestCosine = 0;
        double bestRouge = 0;
        double bestBleu = 0;
        String bestMatch = null;
        for (String gold : goldRequirements) {
            double score = TextSimilarity.tokenF1(inputCase.generatedLowLevelRequirement(), gold);
            if (score > bestScore) {
                bestScore = score;
                bestCosine = TextSimilarity.cosineSimilarity(inputCase.generatedLowLevelRequirement(), gold);
                bestRouge = TextSimilarity.rougeL(inputCase.generatedLowLevelRequirement(), gold);
                bestBleu = TextSimilarity.bleu(inputCase.generatedLowLevelRequirement(), gold);
                bestMatch = gold;
            }
        }

        ObjectNode node = objectMapper.createObjectNode();
        node.put("method", "gold_truth_text_similarity");
        node.put("percentage_score", Math.round(bestScore * 100));
        node.put("similarity_score", bestScore);
        node.put("token_f1_score", bestScore);
        node.put("token_f1_percentage", Math.round(bestScore * 100));
        node.put("cosine_similarity_score", bestCosine);
        node.put("cosine_similarity_percentage", Math.round(bestCosine * 100));
        node.put("rouge_l_score", bestRouge);
        node.put("rouge_l_percentage", Math.round(bestRouge * 100));
        node.put("bleu_score", bestBleu);
        node.put("bleu_percentage", Math.round(bestBleu * 100));
        node.put("gold_candidates_count", goldRequirements.size());
        if (bestMatch != null) {
            node.put("best_matching_gold_requirement", bestMatch);
        }
        node.put("rationale", goldRequirements.isEmpty()
                ? "No gold requirement was available for this HLR/allocation."
                : "Score is token-F1 similarity against the best matching gold LLR for this HLR/allocation.");
        return node;
    }

    public ObjectNode requirementQuality(EvaluationInputCase inputCase) {
        String requirement = inputCase.generatedLowLevelRequirement();
        String normalized = requirement.toLowerCase(Locale.ROOT);
        List<String> passed = new ArrayList<>();
        List<String> failed = new ArrayList<>();

        check(normalized.contains("shall"), "uses_shall_language", passed, failed);
        check(requirement.length() >= 25 && requirement.length() <= 250, "reasonable_length", passed, failed);
        check(!normalized.contains(" and/or "), "avoids_and_or", passed, failed);
        check(!normalized.contains(" as appropriate")
                && !normalized.contains(" if necessary")
                && !normalized.contains(" as needed"), "avoids_vague_escape_clauses", passed, failed);
        check(!normalized.contains("maybe")
                && !normalized.contains("possibly")
                && !normalized.contains("should "), "uses_mandatory_not_optional_language", passed, failed);
        check(startsWithAllocationOrArticle(requirement, inputCase.generatedAllocation()),
                "names_responsible_allocation", passed, failed);

        int percentage = (int) Math.round((passed.size() / 6.0) * 100);
        ObjectNode node = objectMapper.createObjectNode();
        node.put("method", "requirement_quality_rules");
        node.put("percentage_score", percentage);
        node.set("passed_checks", objectMapper.valueToTree(passed));
        node.set("failed_checks", objectMapper.valueToTree(failed));
        node.put("rationale", "Score is the percentage of transparent static requirement-quality checks passed.");
        return node;
    }

    private static void check(boolean condition, String checkName, List<String> passed, List<String> failed) {
        if (condition) {
            passed.add(checkName);
        } else {
            failed.add(checkName);
        }
    }

    private static boolean startsWithAllocationOrArticle(String requirement, String allocation) {
        String trimmed = requirement.trim();
        return trimmed.startsWith(allocation + " ")
                || trimmed.startsWith("The " + allocation + " ")
                || trimmed.startsWith(allocation + " system ")
                || trimmed.startsWith("The " + allocation + " system ");
    }
}
