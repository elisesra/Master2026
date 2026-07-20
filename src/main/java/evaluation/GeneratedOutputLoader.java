package evaluation;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

public final class GeneratedOutputLoader {

    private final ObjectMapper objectMapper;

    public GeneratedOutputLoader() {
        this(new ObjectMapper());
    }

    GeneratedOutputLoader(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    public List<EvaluationInputCase> load(Path generatedOutputFile) {
        if (generatedOutputFile == null) {
            throw new IllegalArgumentException("Generated output file cannot be null.");
        }
        if (!Files.isRegularFile(generatedOutputFile)) {
            throw new IllegalArgumentException("Generated output file does not exist: " + generatedOutputFile);
        }

        try {
            JsonNode root = objectMapper.readTree(generatedOutputFile.toFile());
            if (root.isArray()) {
                return loadArrayReport(root);
            }
            if (root.isObject() && root.path("iterations").isArray()) {
                return loadPanaceaReport(root);
            }
            throw new IllegalArgumentException(
                    "Unsupported generated output format. Expected an array report or a Panacea report."
            );
        } catch (IOException exception) {
            throw new IllegalStateException("Failed to read generated output file: " + generatedOutputFile, exception);
        }
    }

    private List<EvaluationInputCase> loadArrayReport(JsonNode report) {
        List<EvaluationInputCase> cases = new ArrayList<>();
        int caseIndex = 1;
        for (JsonNode item : report) {
            String hlr = item.path("high_level_requirement").asText(null);
            String promptStyle = item.path("prompt_style").asText("unknown");
            String targetAllocation = textOrNull(item, "target_allocation");
            String targetSubsystem = textOrNull(item, "target_subsystem");
            JsonNode requirements = item.path("response").path("low_level_requirements");
            if (!requirements.isArray()) {
                continue;
            }
            for (JsonNode requirement : requirements) {
                String allocation = requirement.path("allocation").asText(null);
                String text = requirement.path("requirement").asText(null);
                if (isBlank(hlr) || isBlank(allocation) || isBlank(text)) {
                    continue;
                }
                cases.add(new EvaluationInputCase(
                        caseIndex++,
                        hlr,
                        promptStyle,
                        targetAllocation,
                        targetSubsystem,
                        allocation,
                        text
                ));
            }
        }
        return List.copyOf(cases);
    }

    private List<EvaluationInputCase> loadPanaceaReport(JsonNode report) {
        List<EvaluationInputCase> cases = new ArrayList<>();
        int caseIndex = 1;
        String promptStyle = report.path("prompt_style").asText("panacea1");
        for (JsonNode iteration : report.path("iterations")) {
            String hlr = iteration.path("high_level_requirement").asText(null);
            String targetAllocation = textOrNull(iteration, "target_allocation");
            String targetSubsystem = textOrNull(iteration, "target_subsystem");
            JsonNode requirements = iteration.path("predicted_response").path("low_level_requirements");
            if (!requirements.isArray()) {
                continue;
            }
            for (JsonNode requirement : requirements) {
                String allocation = requirement.path("allocation").asText(null);
                String text = requirement.path("requirement").asText(null);
                if (isBlank(hlr) || isBlank(allocation) || isBlank(text)) {
                    continue;
                }
                cases.add(new EvaluationInputCase(
                        caseIndex++,
                        hlr,
                        promptStyle,
                        targetAllocation,
                        targetSubsystem,
                        allocation,
                        text
                ));
            }
        }
        return List.copyOf(cases);
    }

    private static String textOrNull(JsonNode node, String fieldName) {
        JsonNode value = node.get(fieldName);
        if (value == null || value.isNull() || value.asText().isBlank()) {
            return null;
        }
        return value.asText();
    }

    private static boolean isBlank(String value) {
        return value == null || value.isBlank();
    }
}
