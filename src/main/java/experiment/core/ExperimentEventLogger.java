package experiment.core;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.List;

public final class ExperimentEventLogger {

    private final Path eventLogFile;
    private final ObjectMapper objectMapper;

    public ExperimentEventLogger(Path reportFile) {
        this(reportFile, new ObjectMapper());
    }

    ExperimentEventLogger(Path reportFile, ObjectMapper objectMapper) {
        if (reportFile == null) {
            throw new IllegalArgumentException("Report file cannot be null.");
        }
        this.eventLogFile = eventLogFileFor(reportFile);
        this.objectMapper = objectMapper;
    }

    public static Path eventLogFileFor(Path reportFile) {
        Path absoluteReport = reportFile.toAbsolutePath();
        Path parent = absoluteReport.getParent();
        String fileName = absoluteReport.getFileName().toString();
        String eventFileName = fileName.endsWith(".json")
                ? fileName.substring(0, fileName.length() - ".json".length()) + ".jsonl"
                : fileName + ".jsonl";
        Path eventLogParent = eventLogParentFor(parent);
        return eventLogParent == null ? Path.of(eventFileName) : eventLogParent.resolve(eventFileName);
    }

    private static Path eventLogParentFor(Path reportParent) {
        if (reportParent == null || reportParent.getFileName() == null) {
            return reportParent;
        }
        Path resultTypeDirectory = reportParent.getParent();
        if (resultTypeDirectory == null || resultTypeDirectory.getFileName() == null) {
            return reportParent;
        }
        Path experimentsDirectory = resultTypeDirectory.getParent();
        if (experimentsDirectory == null || experimentsDirectory.getFileName() == null) {
            return reportParent;
        }
        Path resultsDirectory = experimentsDirectory.getParent();
        if (resultsDirectory == null || resultsDirectory.getFileName() == null) {
            return reportParent;
        }
        if ("experiments".equals(experimentsDirectory.getFileName().toString())
                && "results".equals(resultsDirectory.getFileName().toString())
                && "llmresult".equals(resultTypeDirectory.getFileName().toString())) {
            return experimentsDirectory.resolve("eventlog").resolve(reportParent.getFileName());
        }
        return reportParent;
    }

    public Path eventLogFile() {
        return eventLogFile;
    }

    public void reset() {
        try {
            Path parent = eventLogFile.getParent();
            if (parent != null) {
                Files.createDirectories(parent);
            }
            Files.deleteIfExists(eventLogFile);
            Files.createFile(eventLogFile);
        } catch (IOException exception) {
            throw new IllegalStateException("Failed to reset experiment event log: " + eventLogFile, exception);
        }
    }

    public void request(
            String promptStyle,
            int caseIndex,
            String step,
            String highLevelRequirement,
            List<String> sourceAllocations,
            String targetAllocation,
            String targetSubsystem,
            String prompt
    ) {
        ObjectNode event = baseEvent("request", promptStyle, caseIndex, step);
        event.put("high_level_requirement", highLevelRequirement);
        event.set("source_allocations", objectMapper.valueToTree(sourceAllocations == null ? List.of() : sourceAllocations));
        putIfPresent(event, "target_allocation", targetAllocation);
        putIfPresent(event, "target_subsystem", targetSubsystem);
        event.put("prompt", prompt);
        append(event);
    }

    public void response(
            String promptStyle,
            int caseIndex,
            String step,
            String highLevelRequirement,
            String targetAllocation,
            String targetSubsystem,
            String rawResponse,
            JsonNode validatedResponse
    ) {
        ObjectNode event = baseEvent("response", promptStyle, caseIndex, step);
        event.put("high_level_requirement", highLevelRequirement);
        putIfPresent(event, "target_allocation", targetAllocation);
        putIfPresent(event, "target_subsystem", targetSubsystem);
        event.put("raw_response", rawResponse);
        event.set("validated_response", validatedResponse);
        append(event);
    }

    public void error(
            String promptStyle,
            int caseIndex,
            String step,
            String highLevelRequirement,
            String targetAllocation,
            String targetSubsystem,
            String prompt,
            String rawResponse,
            Exception exception
    ) {
        ObjectNode event = baseEvent("error", promptStyle, caseIndex, step);
        event.put("high_level_requirement", highLevelRequirement);
        putIfPresent(event, "target_allocation", targetAllocation);
        putIfPresent(event, "target_subsystem", targetSubsystem);
        event.put("prompt", prompt);
        putIfPresent(event, "raw_response", rawResponse);
        event.put("error_type", exception.getClass().getSimpleName());
        event.put("message", exception.getMessage());
        append(event);
    }

    private ObjectNode baseEvent(String eventType, String promptStyle, int caseIndex, String step) {
        ObjectNode event = objectMapper.createObjectNode();
        event.put("event", eventType);
        event.put("prompt_style", promptStyle);
        event.put("case_index", caseIndex);
        event.put("step", step);
        return event;
    }

    private void append(ObjectNode event) {
        try {
            String line = objectMapper.writeValueAsString(event) + System.lineSeparator();
            Files.writeString(
                    eventLogFile,
                    line,
                    StandardOpenOption.CREATE,
                    StandardOpenOption.APPEND
            );
        } catch (IOException exception) {
            throw new IllegalStateException("Failed to append experiment event to: " + eventLogFile, exception);
        }
    }

    private static void putIfPresent(ObjectNode node, String fieldName, String value) {
        if (value != null) {
            node.put(fieldName, value);
        }
    }
}
