package evaluation;

public record EvaluationInputCase(
        int caseIndex,
        String highLevelRequirement,
        String sourcePromptStyle,
        String sourceTargetAllocation,
        String sourceTargetSubsystem,
        String generatedAllocation,
        String generatedLowLevelRequirement
) {
    public EvaluationInputCase {
        if (caseIndex <= 0) {
            throw new IllegalArgumentException("Case index must be positive.");
        }
        if (highLevelRequirement == null || highLevelRequirement.isBlank()) {
            throw new IllegalArgumentException("High-level requirement cannot be blank.");
        }
        if (generatedAllocation == null || generatedAllocation.isBlank()) {
            throw new IllegalArgumentException("Generated allocation cannot be blank.");
        }
        if (generatedLowLevelRequirement == null || generatedLowLevelRequirement.isBlank()) {
            throw new IllegalArgumentException("Generated low-level requirement cannot be blank.");
        }
        sourcePromptStyle = sourcePromptStyle == null || sourcePromptStyle.isBlank()
                ? "unknown"
                : sourcePromptStyle.trim();
        sourceTargetAllocation = normalize(sourceTargetAllocation);
        sourceTargetSubsystem = normalize(sourceTargetSubsystem);
        generatedAllocation = generatedAllocation.trim();
        generatedLowLevelRequirement = generatedLowLevelRequirement.trim();
    }

    private static String normalize(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }
}
