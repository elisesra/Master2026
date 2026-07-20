package experiment.prompts;

import experiment.core.RequirementInput;

import java.util.Objects;

public final class PanaceaRun2PromptTemplate implements PromptTemplate {

    public static final String HLR_PLACEHOLDER = "{HLR}";
    public static final String TARGET_ALLOCATION_PLACEHOLDER = "{TARGET_ALLOCATION}";
    public static final String TARGET_SUBSYSTEM_PLACEHOLDER = "{TARGET_SUBSYSTEM}";

    private static final String PROMPT_STYLE = "panacea_run2";
    private final Panacea2PromptTemplate allocationTemplate = new Panacea2PromptTemplate();

    @Override
    public String getPromptStyle() {
        return PROMPT_STYLE;
    }

    @Override
    public String buildPrompt(RequirementInput input) {
        throw new UnsupportedOperationException("panacea_run2 requires an optimized Panacea2 final prompt.");
    }

    public String buildPrompt(RequirementInput input, String optimizedPrompt) {
        Objects.requireNonNull(input, "Requirement input cannot be null.");
        if (input.allocation().size() != 1) {
            throw new IllegalArgumentException("panacea_run2 requires exactly one target allocation.");
        }
        if (optimizedPrompt == null || optimizedPrompt.isBlank()) {
            throw new IllegalArgumentException("Optimized Panacea2 prompt cannot be blank.");
        }
        if (!optimizedPrompt.contains(HLR_PLACEHOLDER)) {
            throw new IllegalArgumentException("Optimized Panacea2 prompt must contain " + HLR_PLACEHOLDER + ".");
        }
        if (!optimizedPrompt.contains(TARGET_ALLOCATION_PLACEHOLDER)) {
            throw new IllegalArgumentException(
                    "Optimized Panacea2 prompt must contain " + TARGET_ALLOCATION_PLACEHOLDER + "."
            );
        }
        if (!optimizedPrompt.contains(TARGET_SUBSYSTEM_PLACEHOLDER)) {
            throw new IllegalArgumentException(
                    "Optimized Panacea2 prompt must contain " + TARGET_SUBSYSTEM_PLACEHOLDER + "."
            );
        }

        String targetAllocation = input.allocation().get(0);
        return optimizedPrompt
                .replace(HLR_PLACEHOLDER, input.highLevelRequirement())
                .replace(TARGET_ALLOCATION_PLACEHOLDER, targetAllocation)
                .replace(TARGET_SUBSYSTEM_PLACEHOLDER, allocationTemplate.allocationDescription(targetAllocation));
    }

    public String allocationDescription(String allocationCode) {
        return allocationTemplate.allocationDescription(allocationCode);
    }
}
