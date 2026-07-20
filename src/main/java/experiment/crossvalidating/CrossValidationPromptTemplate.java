package experiment.crossvalidating;

import evaluation.EvaluationInputCase;

import java.util.List;

public final class CrossValidationPromptTemplate {

    public String buildBlind(EvaluationInputCase input) {
        return basePrompt(input, """
                No reference low-level requirements are supplied. Judge only from the HLR and generated LLR.
                """);
    }

    public String buildWithGroundTruth(EvaluationInputCase input, List<String> groundTruthLlrs) {
        String references = groundTruthLlrs.isEmpty()
                ? "No ground-truth LLRs were found for this HLR."
                : String.join("\n", groundTruthLlrs.stream().map(value -> "- " + value).toList());
        return basePrompt(input, """
                Use the following dataset ground-truth LLRs as reference evidence. Compare semantic intent;
                do not require exact wording. Penalize contradictions, unsupported additions, wrong allocation,
                or failure to represent a meaningful part of the expected decomposition.

                Ground-truth LLRs for this HLR:
                %s
                """.formatted(references));
    }

    private static String basePrompt(EvaluationInputCase input, String evidenceInstruction) {
        String target = input.sourceTargetAllocation() == null
                ? "none"
                : input.sourceTargetAllocation();
        return """
                You are an independent cross-model requirements evaluator.

                Score how well the generated low-level requirement fits the supplied high-level requirement.
                Consider semantic correctness, relevance, allocation fit, non-hallucination, atomicity,
                testability, and preservation of modal intent.

                %s

                Return JSON only using exactly this schema:
                {"score":0.0,"rationale":"short evidence-based explanation"}

                The score must be a decimal from 0.0 through 1.0:
                - 1.0: fully fitting and correct
                - 0.75: mostly fitting, minor issue
                - 0.5: partly fitting, important gap or overreach
                - 0.25: weakly related or mostly incorrect
                - 0.0: unsupported or contradictory

                Source prompt type: %s
                Source target allocation: %s

                High-level requirement:
                %s

                Generated allocation:
                %s

                Generated low-level requirement:
                %s

                Evaluation JSON:
                """.formatted(
                evidenceInstruction,
                input.sourcePromptStyle(),
                target,
                input.highLevelRequirement(),
                input.generatedAllocation(),
                input.generatedLowLevelRequirement()
        );
    }
}
