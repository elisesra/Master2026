package experiment.prompts;

import experiment.core.RequirementInput;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class FewShotPromptTemplatesTest {

    @Test
    void fs1AndFs2RemainIndependentClasses() {
        assertNotEquals(FewShot1PromptTemplate.class, FewShot2PromptTemplate.class);
        assertEquals("fs1", new FewShot1PromptTemplate().getPromptStyle());
        assertEquals("fs2", new FewShot2PromptTemplate().getPromptStyle());
    }

    @Test
    void fs1RetainsTheOriginalMultiAllocationPrompt() {
        RequirementInput input = new RequirementInput(
                "The system shall archive environmental observations.",
                List.of("EMC", "EMS")
        );
        String prompt = new FewShot1PromptTemplate().buildPrompt(input);

        assertEquals(1, occurrences(prompt, input.highLevelRequirement()));
        assertTrue(prompt.contains("Allocation: [EMC, EMS]"));
        assertTrue(prompt.contains("--- Example 1 ---"));
        assertTrue(prompt.contains("Return JSON only"));
    }

    @Test
    void fs2IncludesOneTargetSubsystemAndTheFullAllocationCatalogue() {
        RequirementInput input = new RequirementInput(
                "The system shall archive environmental observations.",
                List.of("EMC")
        );
        String prompt = new FewShot2PromptTemplate().buildPrompt(input);

        assertEquals(1, occurrences(prompt, input.highLevelRequirement()));
        assertTrue(prompt.contains("Target allocation code: EMC"));
        assertTrue(prompt.contains("Target subsystem: Environmental Metadata Cache"));
        assertTrue(prompt.contains("- CAS = Configuration & Administration Service"));
        assertTrue(prompt.contains("- QChS = Quality Checking Services"));
        assertTrue(prompt.contains("Generate only the LLR or LLRs that belong to the target subsystem"));
        assertTrue(prompt.contains("Every returned allocation value must be exactly \"EMC\""));
    }

    @Test
    void fs2RejectsZeroMultipleAndUnknownAllocations() {
        FewShot2PromptTemplate template = new FewShot2PromptTemplate();

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

    private static int occurrences(String text, String value) {
        return (text.length() - text.replace(value, "").length()) / value.length();
    }
}
