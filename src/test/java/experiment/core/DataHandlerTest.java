package experiment.core;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class DataHandlerTest {

    @TempDir
    Path temporaryDirectory;

    @Test
    void discoversAllocationRequirementFiles() throws Exception {
        Path datasetFolder = temporaryDirectory.resolve("clarus");
        Files.createDirectories(datasetFolder);
        Path requirementFile = datasetFolder.resolve("allocation_requirements_cleaned.json");
        Files.writeString(requirementFile, "[]");

        assertEquals(List.of(requirementFile), new DataHandler().findRequirementFiles(temporaryDirectory));
        assertEquals(
                requirementFile.toAbsolutePath(),
                new DataHandler().resolveRequirementFile(temporaryDirectory, "clarus")
        );
    }

    @Test
    void rejectsDatasetNamesOutsideTheDatasetRoot() {
        assertThrows(
                IllegalArgumentException.class,
                () -> new DataHandler().resolveRequirementFile(temporaryDirectory, "../outside")
        );
    }

    @Test
    void readsHlrAndArrayAllocationFromSyntheticInput() throws Exception {
        Path input = temporaryDirectory.resolve("requirements.json");
        Files.writeString(input, """
                [{
                  "high_level_requirement":"The system shall collect observations.",
                  "allocation":["CS"]
                }]
                """);

        List<RequirementInput> requirements = new DataHandler().readRequirements(input);

        assertEquals(1, requirements.size());
        assertEquals("The system shall collect observations.", requirements.get(0).highLevelRequirement());
        assertEquals(List.of("CS"), requirements.get(0).allocation());
    }

    @Test
    void readsTheClarusFieldNames() throws Exception {
        Path input = temporaryDirectory.resolve("allocation_requirements_cleaned.json");
        Files.writeString(input, """
                [{
                  "id":"F-101",
                  "high_level_requirement":"The Clarus system shall implement quality checking processes.",
                  "Allocation":["CAS","QChS","SS"]
                }]
                """);

        RequirementInput requirement = new DataHandler().readRequirements(input).get(0);

        assertEquals(
                "The Clarus system shall implement quality checking processes.",
                requirement.highLevelRequirement()
        );
        assertEquals(List.of("CAS", "QChS", "SS"), requirement.allocation());
    }

    @Test
    void readsAllRequirementsFromTheRealClarusFileWithoutWritingToIt() {
        Path clarusFile = Path.of("datasets", "clarus", "allocation_requirements_cleaned.json");

        List<RequirementInput> requirements = new DataHandler().readRequirements(clarusFile);

        assertEquals(77, requirements.size());
        assertEquals(
                "The Clarus system shall implement quality checking processes as soon as data become available.",
                requirements.get(0).highLevelRequirement()
        );
        assertEquals(List.of("CAS", "QChS", "SS"), requirements.get(0).allocation());
    }

    @Test
    void readsPanaceaExamplesWithGroundTruthFromSyntheticInput() throws Exception {
        Path input = temporaryDirectory.resolve("allocation_requirements_cleaned.json");
        Files.writeString(input, """
                [{
                  "high_level_requirement":"The system shall process observations.",
                  "low level requirement":["The CS shall process observations."]
                }]
                """);

        List<PanaceaRequirementInput> examples = new DataHandler().readPanaceaRequirements(input);

        assertEquals(1, examples.size());
        assertEquals("The system shall process observations.", examples.get(0).highLevelRequirement());
        assertEquals(List.of("The CS shall process observations."), examples.get(0)
                .groundTruthLowLevelRequirements());
    }
}
