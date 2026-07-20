package evaluation;

public final class EvaluationPromptTemplate {

    public String buildPrompt(EvaluationInputCase inputCase) {
        String targetContext = inputCase.sourceTargetAllocation() == null
                ? "No explicit target subsystem was supplied by the generation experiment."
                : "Target allocation from generation experiment: " + inputCase.sourceTargetAllocation()
                + "\nTarget subsystem: " + inputCase.sourceTargetSubsystem();

        return """
                You are an independent requirements-quality evaluator.

                Task:
                Evaluate whether the generated low-level requirement is correct with respect to the high-level requirement.

                Use only the information in this prompt. Do not assume access to the original dataset ground truth.

                Evaluation criteria:
                - Correctness: the LLR preserves the HLR intent and does not contradict it.
                - Coverage: the LLR captures a meaningful part of the HLR.
                - Allocation fit: the generated allocation/subsystem is plausible for the stated behavior.
                - Specificity: the LLR is concrete enough to be useful.
                - Requirement quality: the LLR is atomic, testable, unambiguous, and uses shall-language when appropriate.
                - Non-hallucination: the LLR does not add unjustified behavior, technology, constraints, or subsystem responsibilities.

                Return JSON only, with no Markdown or explanatory prose, using exactly this schema:
                {
                  "correctness_level":"correct|mostly_correct|partly_correct|incorrect|not_enough_information",
                  "score":0.0,
                  "percentage_score":0,
                  "rationale":"short explanation",
                  "issues":["short issue code or explanation"]
                }

                Scoring guide:
                - score is a decimal from 0.0 to 1.0.
                - percentage_score is the same rating as a percentage from 0 to 100.
                - 100 / 1.00 = fully correct and well allocated
                - 75 / 0.75 = mostly correct, minor issue
                - 50 / 0.50 = partly correct, important gaps or overreach
                - 25 / 0.25 = mostly incorrect but some relation to the HLR
                - 0 / 0.00 = incorrect or unsupported

                Source prompt style: %s
                %s

                High-level requirement:
                %s

                Generated allocation:
                %s

                Generated low-level requirement:
                %s

                Evaluation JSON:
                """.formatted(
                inputCase.sourcePromptStyle(),
                targetContext,
                inputCase.highLevelRequirement(),
                inputCase.generatedAllocation(),
                inputCase.generatedLowLevelRequirement()
        );
    }
}
