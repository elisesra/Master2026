package experiment.core;

import java.util.List;

public record PanaceaAllocationRequirementInput(
        String highLevelRequirement,
        List<String> sourceAllocations,
        String targetAllocation,
        List<String> groundTruthLowLevelRequirements
) {

    public PanaceaAllocationRequirementInput {
        if (highLevelRequirement == null || highLevelRequirement.isBlank()) {
            throw new IllegalArgumentException("High-level requirement cannot be blank.");
        }
        sourceAllocations = sourceAllocations == null
                ? List.of()
                : sourceAllocations.stream()
                .filter(value -> value != null && !value.isBlank())
                .map(String::trim)
                .toList();
        if (targetAllocation == null || targetAllocation.isBlank()) {
            throw new IllegalArgumentException("Target allocation cannot be blank.");
        }
        targetAllocation = targetAllocation.trim();
        groundTruthLowLevelRequirements = groundTruthLowLevelRequirements == null
                ? List.of()
                : groundTruthLowLevelRequirements.stream()
                .filter(value -> value != null && !value.isBlank())
                .map(String::trim)
                .toList();
        if (groundTruthLowLevelRequirements.isEmpty()) {
            throw new IllegalArgumentException("Panacea2 requires target-allocation ground-truth LLRs.");
        }
    }
}
