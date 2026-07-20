package experiment.validation;

import com.fasterxml.jackson.databind.JsonNode;

public final class ChainOfThought2ResponseValidator {

    private final LowLevelRequirementResponseValidator validator =
            new LowLevelRequirementResponseValidator("cot2");

    public JsonNode validate(String responseText, String targetAllocation) {
        return validator.validate(responseText, targetAllocation);
    }
}
