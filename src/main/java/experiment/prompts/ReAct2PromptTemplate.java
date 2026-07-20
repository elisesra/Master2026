package experiment.prompts;

import experiment.core.RequirementInput;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Collectors;

public final class ReAct2PromptTemplate implements PromptTemplate {

    private static final String PROMPT_STYLE = "react2";
    private static final Map<String, String> ALLOCATIONS = allocations();
    private static final String ISO_CRITERIA = """
            ISO/IEC/IEEE 29148-inspired requirement quality criteria:
            - Necessary: each LLR is justified by the HLR and target subsystem.
            - Correct: each LLR preserves the HLR intent and target subsystem responsibility.
            - Unambiguous: each LLR should have one reasonable interpretation.
            - Complete enough: each LLR names the responsible system behavior and relevant object.
            - Singular/atomic: each LLR expresses one requirement.
            - Feasible: each LLR describes implementable behavior.
            - Verifiable: each LLR can be checked by test, inspection, demonstration, or analysis.
            - Consistent: each LLR does not contradict the HLR or another generated LLR.
            - Implementation-independent: each LLR avoids unnecessary design choices.
            - Correctly allocated: every returned allocation is exactly the target allocation.
            - Uses requirement language: mandatory behavior uses "shall".
            """;

    @Override
    public String getPromptStyle() {
        return PROMPT_STYLE;
    }

    @Override
    public String buildPrompt(RequirementInput input) {
        Objects.requireNonNull(input, "Requirement input cannot be null.");
        String target = targetAllocation(input);
        return """
                You are a requirements engineer acting as a ReAct-style agent.

                Action: draft_llrs
                Goal: Draft candidate low-level requirements for exactly one target subsystem.

                Scope rules:
                - Generate only LLRs that belong to the target subsystem.
                - Every returned allocation value must be exactly "%s".
                - Do not generate requirements for another subsystem.

                Return JSON only, with no Markdown or explanatory prose, using exactly this schema:
                {"low_level_requirements":[{"allocation":"%s","requirement":"requirement text"}]}

                Allocation catalogue:
                %s

                High-level requirement:
                %s

                Target allocation code: %s
                Target subsystem: %s

                Draft JSON:
                """.formatted(
                target,
                target,
                formattedCatalogue(),
                input.highLevelRequirement(),
                target,
                allocationDescription(target)
        );
    }

    public String buildAssessmentPrompt(RequirementInput input, String draftJson) {
        Objects.requireNonNull(input, "Requirement input cannot be null.");
        if (draftJson == null || draftJson.isBlank()) {
            throw new IllegalArgumentException("Draft JSON cannot be blank.");
        }
        String target = targetAllocation(input);

        return """
                You are a requirements engineer acting as a ReAct-style agent.

                Action: assess_iso_quality_and_revise
                Goal: Assess the drafted target-subsystem LLRs against ISO/IEC/IEEE 29148-inspired requirement quality criteria, then revise them.

                Work internally through this checklist for every candidate LLR:
                %s

                Revision rules:
                - Keep only LLRs justified by both the HLR and the target subsystem.
                - Remove LLRs belonging to another subsystem.
                - Split compound LLRs into atomic LLRs only when necessary.
                - Remove speculative, duplicate, unverifiable, ambiguous, or implementation-specific LLRs.
                - Every returned allocation value must be exactly "%s".
                - Preserve the HLR's intent and modal wording.

                Do not reveal private reasoning. Return only the revised final JSON object, with no Markdown or explanatory prose, using exactly this schema:
                {"low_level_requirements":[{"allocation":"%s","requirement":"requirement text"}]}

                Allocation catalogue:
                %s

                High-level requirement:
                %s

                Target allocation code: %s
                Target subsystem: %s

                Draft candidate JSON:
                %s

                Revised final JSON:
                """.formatted(
                ISO_CRITERIA.strip(),
                target,
                target,
                formattedCatalogue(),
                input.highLevelRequirement(),
                target,
                allocationDescription(target),
                draftJson
        );
    }

    public String allocationDescription(String allocationCode) {
        String description = ALLOCATIONS.get(allocationCode);
        if (description == null) {
            throw new IllegalArgumentException("Unknown allocation code for react2: " + allocationCode);
        }
        return description;
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
                "correctly_allocated",
                "uses_requirement_language"
        );
    }

    private static String targetAllocation(RequirementInput input) {
        if (input.allocation().size() != 1) {
            throw new IllegalArgumentException("react2 requires exactly one target allocation.");
        }
        String target = input.allocation().get(0);
        if (!ALLOCATIONS.containsKey(target)) {
            throw new IllegalArgumentException("Unknown allocation code for react2: " + target);
        }
        return target;
    }

    private static String formattedCatalogue() {
        return ALLOCATIONS.entrySet().stream()
                .map(entry -> "- " + entry.getKey() + " = " + entry.getValue())
                .collect(Collectors.joining("\n"));
    }

    private static Map<String, String> allocations() {
        Map<String, String> values = new LinkedHashMap<>();
        values.put("CAS", "Configuration & Administration Service");
        values.put("CAUI", "Configuration & Administration User Interface");
        values.put("CS", "Collector Services");
        values.put("DOG", "Watchdog");
        values.put("EMC", "Environmental Metadata Cache");
        values.put("EMS", "Environmental Metadata Services");
        values.put("MEUI", "Manual Entry User Interface");
        values.put("QEDC", "Qualified Environmental Data");
        values.put("QEDS", "Qualified Environmental Data Services");
        values.put("QChS", "Quality Checking Services");
        values.put("SS", "Schedule Service");
        return Collections.unmodifiableMap(values);
    }
}
