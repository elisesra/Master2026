package experiment.prompts;

import experiment.core.RequirementInput;

public interface PromptTemplate {

    String getPromptStyle();

    String buildPrompt(RequirementInput requirementInput);

    default String buildPrompt(String highLevelRequirement) {
        return buildPrompt(new RequirementInput(highLevelRequirement, java.util.List.of()));
    }

    default String getJsonlFileName() {
        return getPromptStyle() + "_prompts.jsonl";
    }
}
