package experiment.validation;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

public final class LowLevelRequirementResponseValidator {

    private final ObjectMapper objectMapper;
    private final String responseLabel;

    public LowLevelRequirementResponseValidator() {
        this(new ObjectMapper(), "response");
    }

    public LowLevelRequirementResponseValidator(String responseLabel) {
        this(new ObjectMapper(), responseLabel);
    }

    LowLevelRequirementResponseValidator(ObjectMapper objectMapper, String responseLabel) {
        this.objectMapper = objectMapper;
        this.responseLabel = responseLabel == null || responseLabel.isBlank()
                ? "response"
                : responseLabel.trim();
    }

    public JsonNode validate(String responseText) {
        if (responseText == null || responseText.isBlank()) {
            throw new IllegalArgumentException("Model response cannot be blank.");
        }

        try {
            JsonNode root = objectMapper.readTree(stripCodeFence(responseText));
            JsonNode requirements = root.get("low_level_requirements");
            if (requirements == null || !requirements.isArray() || requirements.isEmpty()) {
                throw new IllegalArgumentException(
                        "Response must contain a non-empty low_level_requirements array."
                );
            }
            for (JsonNode requirement : requirements) {
                requireText(requirement, "allocation");
                requireText(requirement, "requirement");
            }
            return root;
        } catch (JsonProcessingException exception) {
            throw new IllegalArgumentException("Model response is not valid JSON.", exception);
        }
    }

    public JsonNode validate(String responseText, String targetAllocation) {
        if (targetAllocation == null || targetAllocation.isBlank()) {
            throw new IllegalArgumentException("Target allocation cannot be blank.");
        }

        String expectedAllocation = targetAllocation.trim();
        JsonNode response = validate(responseText);
        for (JsonNode requirement : response.path("low_level_requirements")) {
            String actualAllocation = requirement.path("allocation").asText();
            if (!expectedAllocation.equals(actualAllocation)) {
                throw new IllegalArgumentException(
                        responseLabel + " response contains allocation " + actualAllocation
                                + " but the target allocation is " + expectedAllocation + "."
                );
            }
        }
        return response;
    }

    private static void requireText(JsonNode parent, String fieldName) {
        JsonNode value = parent.get(fieldName);
        if (value == null || !value.isTextual() || value.asText().isBlank()) {
            throw new IllegalArgumentException(
                    "Each low-level requirement must contain a non-blank " + fieldName + "."
            );
        }
    }

    private static String stripCodeFence(String responseText) {
        String trimmed = responseText.trim();
        if (!trimmed.startsWith("```")) {
            return trimmed;
        }

        int firstLineEnd = trimmed.indexOf('\n');
        if (firstLineEnd < 0 || !trimmed.endsWith("```")) {
            return trimmed;
        }
        return trimmed.substring(firstLineEnd + 1, trimmed.length() - 3).trim();
    }
}
