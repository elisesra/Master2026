package evaluation;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertEquals;

class GeneratedOutputLoaderTest {

    @TempDir
    Path temporaryDirectory;

    @Test
    void loadsOneCasePerGeneratedLowLevelRequirement() throws Exception {
        Path report = temporaryDirectory.resolve("fs2_report.json");
        Files.writeString(report, """
                [{
                  "high_level_requirement":"The system shall process observations.",
                  "source_allocations":["CAS","QChS"],
                  "target_allocation":"CAS",
                  "target_subsystem":"Configuration & Administration Service",
                  "prompt_style":"fs2",
                  "response":{
                    "low_level_requirements":[
                      {"allocation":"CAS","requirement":"The CAS shall configure processing."},
                      {"allocation":"CAS","requirement":"The CAS shall store configuration."}
                    ]
                  }
                }]
                """);

        var cases = new GeneratedOutputLoader().load(report);

        assertEquals(2, cases.size());
        assertEquals(1, cases.get(0).caseIndex());
        assertEquals("fs2", cases.get(0).sourcePromptStyle());
        assertEquals("CAS", cases.get(0).sourceTargetAllocation());
        assertEquals("CAS", cases.get(0).generatedAllocation());
        assertEquals("The CAS shall configure processing.", cases.get(0).generatedLowLevelRequirement());
    }
}
