package experiment.core;

import java.util.List;

public record RequirementInput(String highLevelRequirement, List<String> allocation) {

    public RequirementInput {
        if (highLevelRequirement == null || highLevelRequirement.isBlank()) {
            throw new IllegalArgumentException("High-level requirement cannot be blank.");
        }
        allocation = allocation == null
                ? List.of()
                : allocation.stream()
                        .filter(value -> value != null && !value.isBlank())
                        .map(String::trim)
                        .toList();
    }
}
