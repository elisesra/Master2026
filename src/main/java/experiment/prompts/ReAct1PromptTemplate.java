package experiment.prompts;

import experiment.core.RequirementInput;

import java.util.List;
import java.util.Objects;

public final class ReAct1PromptTemplate implements PromptTemplate {

    private static final String PROMPT_STYLE = "react1";
    private static final String ISO_CRITERIA = """
            ISO/IEC/IEEE 29148-inspired requirement quality criteria:
            - Necessary: each LLR is justified by the HLR.
            - Correct: each LLR preserves the HLR intent and domain meaning.
            - Unambiguous: each LLR should have one reasonable interpretation.
            - Complete enough: each LLR names the responsible system behavior and relevant object.
            - Singular/atomic: each LLR expresses one requirement.
            - Feasible: each LLR describes implementable behavior.
            - Verifiable: each LLR can be checked by test, inspection, demonstration, or analysis.
            - Consistent: each LLR does not contradict the HLR or another generated LLR.
            - Implementation-independent: each LLR avoids unnecessary design choices.
            - Uses requirement language: mandatory behavior uses "shall".
            """;

    @Override
    public String getPromptStyle() {
        return PROMPT_STYLE;
    }

    @Override
    public String buildPrompt(RequirementInput input) {
        Objects.requireNonNull(input, "Requirement input cannot be null.");
        return """
                You are a requirements engineer acting as a ReAct-style agent.

                Action: draft_llrs
                Goal: Draft candidate low-level requirements from the high-level requirement.

                Use only the HLR and the allocation catalogue. Do not use dataset allocation labels for this react1 condition. Infer the smallest justified set of responsible subsystems.

                Return JSON only, with no Markdown or explanatory prose, using exactly this schema:
                {"low_level_requirements":[{"allocation":"service acronym","requirement":"requirement text"}]}

                Allocation catalogue:
                %s

                High-level requirement:
                %s

                Draft JSON:
                """.formatted(allocationCatalogue(), input.highLevelRequirement());
    }

    public String buildAssessmentPrompt(RequirementInput input, String draftJson) {
        Objects.requireNonNull(input, "Requirement input cannot be null.");
        if (draftJson == null || draftJson.isBlank()) {
            throw new IllegalArgumentException("Draft JSON cannot be blank.");
        }

        return """
                You are a requirements engineer acting as a ReAct-style agent.

                Action: assess_iso_quality_and_revise
                Goal: Assess the drafted LLRs against ISO/IEC/IEEE 29148-inspired requirement quality criteria, then revise them.

                Work internally through this checklist for every candidate LLR:
                %s

                Revision rules:
                - Keep only LLRs justified by the HLR.
                - Split compound LLRs into atomic LLRs only when necessary.
                - Remove speculative, duplicate, unverifiable, ambiguous, or implementation-specific LLRs.
                - Preserve the HLR's intent and modal wording.
                - Infer allocations only from the HLR and allocation catalogue.

                Do not reveal private reasoning. Return only the revised final JSON object, with no Markdown or explanatory prose, using exactly this schema:
                {"low_level_requirements":[{"allocation":"service acronym","requirement":"requirement text"}]}

                Allocation catalogue:
                %s

                High-level requirement:
                %s

                Draft candidate JSON:
                %s

                Revised final JSON:
                """.formatted(
                ISO_CRITERIA.strip(),
                allocationCatalogue(),
                input.highLevelRequirement(),
                draftJson
        );
    }

    public List<String> isoCriteria() {
        return List.of(
                "necessary",
                "correct",
                "unambiguous",
                "complete_enough",
                "singular_atomic",
                "feasible",
                "verifiable",
                "consistent",
                "implementation_independent",
                "uses_requirement_language"
        );
    }

    private static String allocationCatalogue() {
        return """
                - CAS = Configuration & Administration Service
                - CAUI = Configuration & Administration User Interface
                - CS = Collector Services
                - DOG = Watchdog
                - EMC = Environmental Metadata Cache
                - EMS = Environmental Metadata Services
                - MEUI = Manual Entry User Interface
                - QEDC = Qualified Environmental Data
                - QEDS = Qualified Environmental Data Services
                - QChS = Quality Checking Services
                - SS = Schedule Service
                """.strip();
    }
}
