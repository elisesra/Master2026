package experiment.prompts;

import experiment.core.RequirementInput;

import java.util.Objects;

public final class PanaceaRun1PromptTemplate implements PromptTemplate {

    public static final String HLR_PLACEHOLDER = "{HLR}";

    private static final String PROMPT_STYLE = "panacea_run1";

    @Override
    public String getPromptStyle() {
        return PROMPT_STYLE;
    }

    @Override
    public String buildPrompt(RequirementInput input) {
        throw new UnsupportedOperationException("panacea_run1 requires an optimized Panacea final prompt.");
    }

    public String buildPrompt(RequirementInput input, String optimizedPrompt) {
        Objects.requireNonNull(input, "Requirement input cannot be null.");
        if (optimizedPrompt == null || optimizedPrompt.isBlank()) {
            throw new IllegalArgumentException("Optimized Panacea prompt cannot be blank.");
        }
        if (!optimizedPrompt.contains(HLR_PLACEHOLDER)) {
            throw new IllegalArgumentException("Optimized Panacea prompt must contain " + HLR_PLACEHOLDER + ".");
        }
        return optimizedPrompt.replace(HLR_PLACEHOLDER, input.highLevelRequirement());
    }
}
