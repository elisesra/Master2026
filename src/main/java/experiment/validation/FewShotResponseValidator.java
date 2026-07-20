package experiment.validation;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

public final class FewShotResponseValidator {

    private final LowLevelRequirementResponseValidator validator;

    public FewShotResponseValidator() {
        this(new ObjectMapper());
    }

    FewShotResponseValidator(ObjectMapper objectMapper) {
        this.validator = new LowLevelRequirementResponseValidator(objectMapper, "few-shot");
    }

    public JsonNode validate(String responseText) {
        return validator.validate(responseText);
    }
}
