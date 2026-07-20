package experiment.validation;

import com.fasterxml.jackson.databind.JsonNode;

public final class FewShot2ResponseValidator {

    private final LowLevelRequirementResponseValidator validator;

    public FewShot2ResponseValidator() {
        this(new LowLevelRequirementResponseValidator("fs2"));
    }

    FewShot2ResponseValidator(LowLevelRequirementResponseValidator validator) {
        this.validator = validator;
    }

    public JsonNode validate(String responseText, String targetAllocation) {
        return validator.validate(responseText, targetAllocation);
    }
}
