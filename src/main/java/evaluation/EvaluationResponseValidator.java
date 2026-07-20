package evaluation;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.util.Set;

public final class EvaluationResponseValidator {

    private static final Set<String> LEVELS = Set.of(
            "correct",
            "mostly_correct",
            "partly_correct",
            "incorrect",
            "not_enough_information"
    );

    private final ObjectMapper objectMapper = new ObjectMapper();

    public JsonNode validate(String responseText) {
        if (responseText == null || responseText.isBlank()) {
            throw new IllegalArgumentException("Evaluation response cannot be blank.");
        }

        try {
            JsonNode root = objectMapper.readTree(responseText);
            String level = requiredText(root, "correctness_level");
            if (!LEVELS.contains(level)) {
                throw new IllegalArgumentException("Unsupported correctness_level: " + level);
            }
            JsonNode score = root.get("score");
            if (score == null || !score.isNumber() || score.asDouble() < 0 || score.asDouble() > 1) {
                throw new IllegalArgumentException("Evaluation score must be a number between 0 and 1.");
            }
            JsonNode percentageScore = root.get("percentage_score");
            if (percentageScore == null
                    || !percentageScore.isNumber()
                    || percentageScore.asDouble() < 0
                    || percentageScore.asDouble() > 100) {
                throw new IllegalArgumentException(
                        "Evaluation percentage_score must be a number between 0 and 100."
                );
            }
            requiredText(root, "rationale");
            JsonNode issues = root.get("issues");
            if (issues == null || !issues.isArray()) {
                throw new IllegalArgumentException("Evaluation issues must be an array.");
            }
            return root;
        } catch (JsonProcessingException exception) {
            throw new IllegalArgumentException("Evaluation response is not valid JSON.", exception);
        }
    }

    private static String requiredText(JsonNode root, String fieldName) {
        JsonNode value = root.get(fieldName);
        if (value == null || !value.isTextual() || value.asText().isBlank()) {
            throw new IllegalArgumentException("Evaluation response must contain non-blank " + fieldName + ".");
        }
        return value.asText();
    }
}
