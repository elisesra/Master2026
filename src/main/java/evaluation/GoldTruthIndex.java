package evaluation;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Locale;
import java.util.Map;

public final class GoldTruthIndex {

    private final Map<String, Map<String, List<String>>> byHlrAndAllocation;

    private GoldTruthIndex(Map<String, Map<String, List<String>>> byHlrAndAllocation) {
        this.byHlrAndAllocation = byHlrAndAllocation;
    }

    public static GoldTruthIndex load(Path datasetFile) {
        if (datasetFile == null) {
            throw new IllegalArgumentException("Dataset file cannot be null.");
        }
        if (!Files.isRegularFile(datasetFile)) {
            throw new IllegalArgumentException("Dataset file does not exist: " + datasetFile);
        }

        ObjectMapper objectMapper = new ObjectMapper();
        try {
            JsonNode root = objectMapper.readTree(datasetFile.toFile());
            if (!root.isArray()) {
                throw new IllegalArgumentException("Expected dataset JSON root to be an array: " + datasetFile);
            }

            Map<String, Map<String, List<String>>> index = new HashMap<>();
            for (JsonNode node : root) {
                String hlr = node.path("high_level_requirement").asText(null);
                if (hlr == null || hlr.isBlank()) {
                    continue;
                }
                Map<String, List<String>> allocationRequirements = readAllocationRequirements(node);
                if (!allocationRequirements.isEmpty()) {
                    index.put(normalizeHlr(hlr), allocationRequirements);
                }
            }
            return new GoldTruthIndex(Map.copyOf(index));
        } catch (IOException exception) {
            throw new IllegalStateException("Failed to read dataset file: " + datasetFile, exception);
        }
    }

    public List<String> goldRequirements(String highLevelRequirement, String allocation) {
        Map<String, List<String>> byAllocation = byHlrAndAllocation.get(normalizeHlr(highLevelRequirement));
        if (byAllocation == null) {
            return List.of();
        }
        if (allocation != null && byAllocation.containsKey(allocation)) {
            return byAllocation.get(allocation);
        }
        return byAllocation.values().stream().flatMap(List::stream).toList();
    }

    public boolean hasAllocation(String highLevelRequirement, String allocation) {
        Map<String, List<String>> byAllocation = byHlrAndAllocation.get(normalizeHlr(highLevelRequirement));
        return byAllocation != null && allocation != null && byAllocation.containsKey(allocation);
    }

    private static Map<String, List<String>> readAllocationRequirements(JsonNode node) {
        JsonNode allocationRequirements = node.path("allocation_requirements");
        if (allocationRequirements.isObject()) {
            Map<String, List<String>> result = new HashMap<>();
            Iterator<Map.Entry<String, JsonNode>> fields = allocationRequirements.fields();
            while (fields.hasNext()) {
                Map.Entry<String, JsonNode> entry = fields.next();
                List<String> requirements = readLowLevelRequirementTexts(
                        entry.getValue().path("low_level_requirements")
                );
                if (!requirements.isEmpty()) {
                    result.put(entry.getKey(), requirements);
                }
            }
            return result;
        }

        List<String> flatRequirements = readTextList(node.path("low level requirement"));
        List<String> allocations = readTextList(node.path("Allocation"));
        if (flatRequirements.isEmpty() || allocations.isEmpty()) {
            return Map.of();
        }
        Map<String, List<String>> fallback = new HashMap<>();
        for (String allocation : allocations) {
            fallback.put(allocation, flatRequirements);
        }
        return fallback;
    }

    private static List<String> readLowLevelRequirementTexts(JsonNode lowLevelRequirements) {
        if (!lowLevelRequirements.isArray()) {
            return List.of();
        }
        List<String> values = new ArrayList<>();
        for (JsonNode requirement : lowLevelRequirements) {
            String text = requirement.path("text").asText(null);
            if (text != null && !text.isBlank()) {
                values.add(text.trim());
            }
        }
        return List.copyOf(values);
    }

    private static List<String> readTextList(JsonNode node) {
        if (node == null || node.isMissingNode() || node.isNull()) {
            return List.of();
        }
        if (node.isArray()) {
            List<String> values = new ArrayList<>();
            for (JsonNode value : node) {
                if (!value.asText().isBlank()) {
                    values.add(value.asText().trim());
                }
            }
            return List.copyOf(values);
        }
        return node.asText().isBlank() ? List.of() : List.of(node.asText().trim());
    }

    private static String normalizeHlr(String highLevelRequirement) {
        return highLevelRequirement == null
                ? ""
                : highLevelRequirement.trim().replaceAll("\\s+", " ").toLowerCase(Locale.ROOT);
    }
}
