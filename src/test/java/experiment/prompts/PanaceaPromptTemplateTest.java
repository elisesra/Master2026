package experiment.prompts;

import experiment.core.RequirementInput;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class PanaceaPromptTemplateTest {

    @Test
    void initialPromptUsesReusableHlrPlaceholder() {
        Panacea1PromptTemplate template = new Panacea1PromptTemplate();

        assertEquals("panacea1", template.getPromptStyle());
        assertTrue(template.initialPrompt().contains("{HLR}"));
        assertTrue(template.initialPrompt().contains("Return JSON only"));
    }

    @Test
    void panacea2InitialPromptUsesReusableHlrAndTargetPlaceholders() {
        Panacea2PromptTemplate template = new Panacea2PromptTemplate();

        assertEquals("panacea2", template.getPromptStyle());
        assertTrue(template.initialPrompt().contains("{HLR}"));
        assertTrue(template.initialPrompt().contains("{TARGET_ALLOCATION}"));
        assertTrue(template.initialPrompt().contains("{TARGET_SUBSYSTEM}"));
    }

    @Test
    void panacea2GenerationPromptContainsOneConcreteTargetAllocationAtRuntime() {
        Panacea2PromptTemplate template = new Panacea2PromptTemplate();
        String prompt = template.buildGenerationPrompt(
                template.initialPrompt(),
                "The system shall process observations.",
                "CAS"
        );

        assertTrue(prompt.contains("The system shall process observations."));
        assertTrue(prompt.contains("Target allocation code: CAS"));
        assertTrue(prompt.contains("Target subsystem: Configuration & Administration Service"));
        assertTrue(!prompt.contains("{HLR}"));
        assertTrue(!prompt.contains("{TARGET_ALLOCATION}"));
        assertTrue(!prompt.contains("{TARGET_SUBSYSTEM}"));
    }

    @Test
    void generationPromptContainsOnlyConcreteHlrAtRuntime() {
        Panacea1PromptTemplate template = new Panacea1PromptTemplate();
        String prompt = template.buildPrompt(new RequirementInput(
                "The system shall process observations.",
                java.util.List.of("CAS")
        ));

        assertTrue(prompt.contains("The system shall process observations."));
        assertTrue(!prompt.contains("{HLR}"));
    }

    @Test
    void rejectsPromptTemplatesWithoutPlaceholder() {
        Panacea1PromptTemplate template = new Panacea1PromptTemplate();

        assertThrows(IllegalArgumentException.class, () ->
                template.buildGenerationPrompt("Create an LLR.", "The system shall work.")
        );
    }

    @Test
    void panacea2RejectsPromptTemplatesWithoutTargetPlaceholders() {
        Panacea2PromptTemplate template = new Panacea2PromptTemplate();

        assertThrows(IllegalArgumentException.class, () ->
                template.buildGenerationPrompt(
                        "Create an LLR for {HLR}.",
                        "The system shall work.",
                        "CAS"
                )
        );
    }

    @Test
    void panaceaRun1ReplacesHlrInOptimizedPrompt() {
        PanaceaRun1PromptTemplate template = new PanaceaRun1PromptTemplate();
        String prompt = template.buildPrompt(
                new RequirementInput("The system shall process observations.", java.util.List.of("CAS")),
                "Optimized prompt for {HLR}"
        );

        assertEquals("panacea_run1", template.getPromptStyle());
        assertEquals("Optimized prompt for The system shall process observations.", prompt);
    }

    @Test
    void panaceaRun1RejectsOptimizedPromptWithoutHlrPlaceholder() {
        PanaceaRun1PromptTemplate template = new PanaceaRun1PromptTemplate();

        assertThrows(IllegalArgumentException.class, () ->
                template.buildPrompt(
                        new RequirementInput("The system shall work.", java.util.List.of()),
                        "Optimized prompt without placeholder"
                )
        );
    }

    @Test
    void panaceaRun2ReplacesHlrAndTargetAllocationInOptimizedPrompt() {
        PanaceaRun2PromptTemplate template = new PanaceaRun2PromptTemplate();
        String prompt = template.buildPrompt(
                new RequirementInput("The system shall process observations.", java.util.List.of("CAS")),
                "Optimized {HLR} for {TARGET_ALLOCATION} / {TARGET_SUBSYSTEM}"
        );

        assertEquals("panacea_run2", template.getPromptStyle());
        assertEquals(
                "Optimized The system shall process observations. for CAS / Configuration & Administration Service",
                prompt
        );
    }

    @Test
    void panaceaRun2RejectsOptimizedPromptWithoutTargetPlaceholders() {
        PanaceaRun2PromptTemplate template = new PanaceaRun2PromptTemplate();

        assertThrows(IllegalArgumentException.class, () ->
                template.buildPrompt(
                        new RequirementInput("The system shall work.", java.util.List.of("CAS")),
                        "Optimized prompt for {HLR}"
                )
        );
    }
}
