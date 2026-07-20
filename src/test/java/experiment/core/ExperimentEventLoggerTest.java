package experiment.core;

import org.junit.jupiter.api.Test;

import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertEquals;

class ExperimentEventLoggerTest {

    @Test
    void routesExperimentEventLogsUnderEventlogPromptFolder() {
        Path report = Path.of(
                "results",
                "experiments",
                "llmresult",
                "fs1",
                "gpt-4.1_fs1_clarus_result.json"
        );

        assertEquals(
                Path.of(
                        "results",
                        "experiments",
                        "eventlog",
                        "fs1",
                        "gpt-4.1_fs1_clarus_result.jsonl"
                ).toAbsolutePath(),
                ExperimentEventLogger.eventLogFileFor(report)
        );
    }

    @Test
    void keepsCustomOutputEventLogsNextToTheReport() {
        Path report = Path.of("custom-output", "fs1.json");

        assertEquals(
                Path.of("custom-output", "fs1.jsonl").toAbsolutePath(),
                ExperimentEventLogger.eventLogFileFor(report)
        );
    }
}
