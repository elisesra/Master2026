package experiment.core;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

public final class ExperimentReportWriter {

    private ExperimentReportWriter() {
    }

    public static void writeReport(
            ObjectMapper objectMapper,
            Path outputFile,
            JsonNode report,
            String reportName
    ) {
        try {
            Path parent = outputFile.toAbsolutePath().getParent();
            if (parent != null) {
                Files.createDirectories(parent);
            }
            objectMapper.writerWithDefaultPrettyPrinter().writeValue(outputFile.toFile(), report);
        } catch (IOException exception) {
            throw new IllegalStateException("Failed to write " + reportName + " report: " + outputFile, exception);
        }
    }
}
