package experiment.prompts;

import experiment.core.RequirementInput;

import java.util.Objects;

public final class Panacea1PromptTemplate implements PromptTemplate {

    public static final String HLR_PLACEHOLDER = "{HLR}";

    private static final String PROMPT_STYLE = "panacea1";

    private static final String INITIAL_PROMPT = """
            Create low-level requirements for the following high-level requirement.

            Return JSON only, with no Markdown or explanatory prose, using exactly this schema:
            {"low_level_requirements":[{"allocation":"service acronym","requirement":"requirement text"}]}

            High-level requirement:
            {HLR}
            """;

    @Override
    public String getPromptStyle() {
        return PROMPT_STYLE;
    }

    @Override
    public String buildPrompt(RequirementInput input) {
        Objects.requireNonNull(input, "Requirement input cannot be null.");
        return buildGenerationPrompt(initialPrompt(), input.highLevelRequirement());
    }

    public String initialPrompt() {
        return INITIAL_PROMPT.strip();
    }

    public String buildGenerationPrompt(String promptTemplate, String highLevelRequirement) {
        if (promptTemplate == null || promptTemplate.isBlank()) {
            throw new IllegalArgumentException("Prompt template cannot be blank.");
        }
        if (!promptTemplate.contains(HLR_PLACEHOLDER)) {
            throw new IllegalArgumentException("Prompt template must contain " + HLR_PLACEHOLDER + ".");
        }
        if (highLevelRequirement == null || highLevelRequirement.isBlank()) {
            throw new IllegalArgumentException("High-level requirement cannot be blank.");
        }
        return promptTemplate.replace(HLR_PLACEHOLDER, highLevelRequirement);
    }
}
