package experiment.validation;

import com.fasterxml.jackson.databind.JsonNode;

public final class ChainOfThoughtResponseValidator {

    private final LowLevelRequirementResponseValidator validator =
            new LowLevelRequirementResponseValidator("chain-of-thought");

    public JsonNode validate(String responseText) {
        return validator.validate(responseText);
    }
}
