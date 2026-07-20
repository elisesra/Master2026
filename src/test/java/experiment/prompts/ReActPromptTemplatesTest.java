package experiment.prompts;

import experiment.core.RequirementInput;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ReActPromptTemplatesTest {

    @Test
    void react1DraftIgnoresDatasetAllocationAndAssessmentUsesIsoCriteria() {
        ReAct1PromptTemplate template = new ReAct1PromptTemplate();
        String hlr = "The system shall process observations.";

        String promptWithCas = template.buildPrompt(new RequirementInput(hlr, List.of("CAS")));
        String promptWithSs = template.buildPrompt(new RequirementInput(hlr, List.of("SS")));
        String assessment = template.buildAssessmentPrompt(
                new RequirementInput(hlr, List.of()),
                "{\"low_level_requirements\":[{\"allocation\":\"CAS\",\"requirement\":\"The CAS shall process observations.\"}]}"
        );

        assertEquals(promptWithCas, promptWithSs);
        assertEquals("react1", template.getPromptStyle());
        assertTrue(promptWithCas.contains("Action: draft_llrs"));
        assertTrue(promptWithCas.contains("Do not use dataset allocation labels"));
        assertTrue(assessment.contains("Action: assess_iso_quality_and_revise"));
        assertTrue(assessment.contains("ISO/IEC/IEEE 29148-inspired"));
        assertTrue(assessment.contains("Verifiable"));
        assertFalse(promptWithCas.contains("--- Example"));
    }

    @Test
    void react2IncludesTargetSubsystemAndCorrectAllocationCriterion() {
        ReAct2PromptTemplate template = new ReAct2PromptTemplate();
        RequirementInput input = new RequirementInput(
                "The system shall process observations.",
                List.of("QChS")
        );

        String draft = template.buildPrompt(input);
        String assessment = template.buildAssessmentPrompt(
                input,
                "{\"low_level_requirements\":[{\"allocation\":\"QChS\",\"requirement\":\"The QChS shall process observations.\"}]}"
        );

        assertEquals("react2", template.getPromptStyle());
        assertTrue(draft.contains("Target allocation code: QChS"));
        assertTrue(draft.contains("Target subsystem: Quality Checking Services"));
        assertTrue(assessment.contains("Correctly allocated"));
        assertTrue(assessment.contains("Every returned allocation value must be exactly \"QChS\""));
    }

    @Test
    void react2RejectsInvalidTargetAllocationInputs() {
        ReAct2PromptTemplate template = new ReAct2PromptTemplate();

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
