package experiment.prompts;

import experiment.core.RequirementInput;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class RagPromptTemplatesTest {

    @Test
    void rag1UsesHlrAndContextWithoutDatasetAllocation() {
        String prompt = new Rag1PromptTemplate().buildPrompt(
                new RequirementInput("The system shall process observations.", List.of()),
                "[Context 1] Reference material"
        );

        assertTrue(prompt.contains("The system shall process observations."));
        assertTrue(prompt.contains("Reference material"));
        assertTrue(prompt.contains("untrusted reference material"));
        assertFalse(prompt.contains("Target allocation code:"));
    }

    @Test
    void rag2ScopesThePromptToOneKnownSubsystem() {
        String prompt = new Rag2PromptTemplate().buildPrompt(
                new RequirementInput("The system shall process observations.", List.of("QChS")),
                "[Context 1] Quality checking reference"
        );

        assertTrue(prompt.contains("Target allocation code: QChS"));
        assertTrue(prompt.contains("Target subsystem: Quality Checking Services"));
        assertTrue(prompt.contains("Every returned allocation value must be exactly \"QChS\""));
    }

    @Test
    void rag2RejectsMultipleAllocationsInOnePrompt() {
        assertThrows(
                IllegalArgumentException.class,
                () -> new Rag2PromptTemplate().buildPrompt(
                        new RequirementInput("The system shall work.", List.of("CAS", "SS")),
                        "context"
                )
        );
    }
}
