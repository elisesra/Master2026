package experiment.crossvalidating;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

public final class CrossValidationResponseValidator {

    private final ObjectMapper objectMapper = new ObjectMapper();

    public JsonNode validate(String response) {
        if (response == null || response.isBlank()) {
            throw new IllegalArgumentException("Cross-validation response cannot be blank.");
        }
        try {
            JsonNode root = objectMapper.readTree(response);
            JsonNode score = root.get("score");
            if (score == null || !score.isNumber() || score.asDouble() < 0 || score.asDouble() > 1) {
                throw new IllegalArgumentException("Cross-validation score must be between 0 and 1.");
            }
            JsonNode rationale = root.get("rationale");
            if (rationale == null || !rationale.isTextual() || rationale.asText().isBlank()) {
                throw new IllegalArgumentException("Cross-validation rationale must be non-blank text.");
            }
            return root;
        } catch (JsonProcessingException exception) {
            throw new IllegalArgumentException("Cross-validation response is not valid JSON.", exception);
        }
    }
}
