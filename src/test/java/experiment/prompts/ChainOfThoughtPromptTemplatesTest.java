package experiment.prompts;

import experiment.core.RequirementInput;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ChainOfThoughtPromptTemplatesTest {

    @Test
    void cot1UsesOnlyTheHlrAndNeverTheDatasetAllocation() {
        ChainOfThought1PromptTemplate template = new ChainOfThought1PromptTemplate();
        String hlr = "The system shall process observations.";

        String promptWithCas = template.buildPrompt(new RequirementInput(hlr, List.of("CAS")));
        String promptWithSs = template.buildPrompt(new RequirementInput(hlr, List.of("SS")));

        assertEquals(promptWithCas, promptWithSs);
        assertEquals("cot1", template.getPromptStyle());
        assertTrue(promptWithCas.contains("Infer the smallest justified set"));
        assertTrue(promptWithCas.contains("Do not reveal the reasoning process"));
        assertFalse(promptWithCas.contains("--- Example"));
    }

    @Test
    void cot2IncludesExactlyOneTargetAndItsMeaning() {
        ChainOfThought2PromptTemplate template = new ChainOfThought2PromptTemplate();
        String prompt = template.buildPrompt(new RequirementInput(
                "The system shall process observations.",
                List.of("QChS")
        ));

        assertEquals("cot2", template.getPromptStyle());
        assertTrue(prompt.contains("Target allocation code: QChS"));
        assertTrue(prompt.contains("Target subsystem: Quality Checking Services"));
        assertTrue(prompt.contains("Focus exclusively on the responsibility"));
        assertTrue(prompt.contains("- CAS = Configuration & Administration Service"));
        assertFalse(prompt.contains("--- Example"));
    }

    @Test
    void cot2RejectsZeroMultipleAndUnknownAllocations() {
        ChainOfThought2PromptTemplate template = new ChainOfThought2PromptTemplate();

        assertThrows(IllegalArgumentException.class, () -> template.buildPrompt(
                new RequirementInput("The system shall report status.", List.of())
        ));
        assertThrows(IllegalArgumentException.class, () -> template.buildPrompt(
                new RequirementInput("The system shall report status.", List.of("CAS", "SS"))
        ));
        assertThrows(IllegalArgumentException.class, () -> template.buildPrompt(
                new RequirementInput("The system shall report status.", List.of("UNKNOWN"))
        ));
    }
}
