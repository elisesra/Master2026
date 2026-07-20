package experiment.core;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Stream;

public final class DataHandler {

    public static final String REQUIREMENT_FILE_NAME = "allocation_requirements_cleaned.json";

    private final ObjectMapper objectMapper;

    public DataHandler() {
        this(new ObjectMapper());
    }

    DataHandler(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    public List<Path> findRequirementFiles(Path datasetsDirectory) {
        if (datasetsDirectory == null) {
            throw new IllegalArgumentException("Datasets directory cannot be null.");
        }
        if (!Files.isDirectory(datasetsDirectory)) {
            throw new IllegalArgumentException("Datasets directory does not exist: " + datasetsDirectory);
        }

        try (Stream<Path> folders = Files.list(datasetsDirectory)) {
            return folders
                    .filter(Files::isDirectory)
                    .map(folder -> folder.resolve(REQUIREMENT_FILE_NAME))
                    .filter(Files::isRegularFile)
                    .sorted()
                    .toList();
        } catch (IOException exception) {
            throw new IllegalStateException("Failed to read datasets directory: " + datasetsDirectory, exception);
        }
    }

    public Path resolveRequirementFile(Path datasetsDirectory, String datasetName) {
        if (datasetsDirectory == null) {
            throw new IllegalArgumentException("Datasets directory cannot be null.");
        }
        if (datasetName == null || datasetName.isBlank()) {
            throw new IllegalArgumentException("Dataset name cannot be blank.");
        }

        Path root = datasetsDirectory.toAbsolutePath().normalize();
        Path datasetFolder = root.resolve(datasetName.trim()).normalize();
        if (!datasetFolder.startsWith(root)) {
            throw new IllegalArgumentException("Dataset name must stay inside: " + datasetsDirectory);
        }

        Path requirementFile = datasetFolder.resolve(REQUIREMENT_FILE_NAME);
        if (!Files.isRegularFile(requirementFile)) {
            throw new IllegalArgumentException("Requirement file does not exist: " + requirementFile);
        }
        return requirementFile;
    }

    public List<RequirementInput> readRequirements(Path jsonFile) {
        if (jsonFile == null) {
            throw new IllegalArgumentException("JSON file cannot be null.");
        }

        try {
            JsonNode root = objectMapper.readTree(jsonFile.toFile());
            if (!root.isArray()) {
                throw new IllegalArgumentException("Expected JSON root to be an array: " + jsonFile);
            }

            List<RequirementInput> requirements = new ArrayList<>();
            for (JsonNode node : root) {
                JsonNode hlrNode = node.get("high_level_requirement");
                if (hlrNode == null || hlrNode.isNull() || hlrNode.asText().isBlank()) {
                    continue;
                }
                JsonNode allocationNode = firstPresent(node, "Allocation", "allocation");
                requirements.add(new RequirementInput(hlrNode.asText(), readAllocation(allocationNode)));
            }
            return List.copyOf(requirements);
        } catch (IOException exception) {
            throw new IllegalStateException("Failed to read requirements from: " + jsonFile, exception);
        }
    }

    public List<PanaceaRequirementInput> readPanaceaRequirements(Path jsonFile) {
        if (jsonFile == null) {
            throw new IllegalArgumentException("JSON file cannot be null.");
        }

        try {
            JsonNode root = objectMapper.readTree(jsonFile.toFile());
            if (!root.isArray()) {
                throw new IllegalArgumentException("Expected JSON root to be an array: " + jsonFile);
            }

            List<PanaceaRequirementInput> examples = new ArrayList<>();
            for (JsonNode node : root) {
                JsonNode hlrNode = node.get("high_level_requirement");
                if (hlrNode == null || hlrNode.isNull() || hlrNode.asText().isBlank()) {
                    continue;
                }
                JsonNode lowLevelNode = firstPresent(
                        node,
                        "low level requirement",
                        "low_level",
                        "lowLevelRequirements",
                        "lowLevelRequirement"
                );
                List<String> groundTruth = readTextList(lowLevelNode);
                if (!groundTruth.isEmpty()) {
                    examples.add(new PanaceaRequirementInput(hlrNode.asText(), groundTruth));
                }
            }
            return List.copyOf(examples);
        } catch (IOException exception) {
            throw new IllegalStateException("Failed to read Panacea examples from: " + jsonFile, exception);
        }
    }

    public List<PanaceaAllocationRequirementInput> readPanaceaAllocationRequirements(Path jsonFile) {
        if (jsonFile == null) {
            throw new IllegalArgumentException("JSON file cannot be null.");
        }

        try {
            JsonNode root = objectMapper.readTree(jsonFile.toFile());
            if (!root.isArray()) {
                throw new IllegalArgumentException("Expected JSON root to be an array: " + jsonFile);
            }

            List<PanaceaAllocationRequirementInput> examples = new ArrayList<>();
            for (JsonNode node : root) {
                JsonNode hlrNode = node.get("high_level_requirement");
                if (hlrNode == null || hlrNode.isNull() || hlrNode.asText().isBlank()) {
                    continue;
                }
                String highLevelRequirement = hlrNode.asText();
                List<String> sourceAllocations = readAllocation(firstPresent(node, "Allocation", "allocation"));
                JsonNode allocationRequirements = node.path("allocation_requirements");
                if (allocationRequirements.isObject()) {
                    allocationRequirements.fields().forEachRemaining(entry -> {
                        List<String> groundTruth = readAllocationGroundTruth(entry.getValue());
                        if (!groundTruth.isEmpty()) {
                            examples.add(new PanaceaAllocationRequirementInput(
                                    highLevelRequirement,
                                    sourceAllocations,
                                    entry.getKey(),
                                    groundTruth
                            ));
                        }
                    });
                }
            }
            return List.copyOf(examples);
        } catch (IOException exception) {
            throw new IllegalStateException("Failed to read Panacea2 examples from: " + jsonFile, exception);
        }
    }

    public List<String> readHighLevelRequirements(Path jsonFile) {
        return readRequirements(jsonFile).stream()
                .map(RequirementInput::highLevelRequirement)
                .toList();
    }

    private static List<String> readAllocation(JsonNode allocationNode) {
        if (allocationNode == null || allocationNode.isNull()) {
            return List.of();
        }
        if (allocationNode.isArray()) {
            List<String> allocation = new ArrayList<>();
            allocationNode.forEach(value -> allocation.add(value.asText()));
            return allocation;
        }
        String value = allocationNode.asText().trim();
        return value.isEmpty() ? List.of() : List.of(value);
    }

    private static List<String> readTextList(JsonNode node) {
        if (node == null || node.isNull()) {
            return List.of();
        }
        if (node.isArray()) {
            List<String> values = new ArrayList<>();
            node.forEach(value -> {
                String text = value.asText().trim();
                if (!text.isEmpty()) {
                    values.add(text);
                }
            });
            return values;
        }
        String value = node.asText().trim();
        return value.isEmpty() ? List.of() : List.of(value);
    }

    private static List<String> readAllocationGroundTruth(JsonNode allocationNode) {
        JsonNode lowLevelRequirements = allocationNode.path("low_level_requirements");
        if (!lowLevelRequirements.isArray()) {
            return List.of();
        }
        List<String> values = new ArrayList<>();
        for (JsonNode requirement : lowLevelRequirements) {
            String text = requirement.path("text").asText("").trim();
            if (!text.isEmpty()) {
                values.add(text);
            }
        }
        return List.copyOf(values);
    }

    private static JsonNode firstPresent(JsonNode parent, String... fieldNames) {
        for (String fieldName : fieldNames) {
            JsonNode value = parent.get(fieldName);
            if (value != null && !value.isNull()) {
                return value;
            }
        }
        return null;
    }
}
