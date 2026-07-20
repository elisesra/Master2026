package experiment.core;

import java.util.List;

public record PanaceaRequirementInput(
        String highLevelRequirement,
        List<String> groundTruthLowLevelRequirements
) {

    public PanaceaRequirementInput {
        if (highLevelRequirement == null || highLevelRequirement.isBlank()) {
            throw new IllegalArgumentException("High-level requirement cannot be blank.");
        }
        groundTruthLowLevelRequirements = groundTruthLowLevelRequirements == null
                ? List.of()
                : groundTruthLowLevelRequirements.stream()
                .filter(value -> value != null && !value.isBlank())
                .map(String::trim)
                .toList();
        if (groundTruthLowLevelRequirements.isEmpty()) {
            throw new IllegalArgumentException("Panacea prompting requires ground-truth LLRs for evaluation.");
        }
    }
}
