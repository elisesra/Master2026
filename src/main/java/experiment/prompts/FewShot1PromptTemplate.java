package experiment.prompts;

import experiment.core.RequirementInput;

import java.util.List;
import java.util.Objects;
import java.util.stream.Collectors;

public final class FewShot1PromptTemplate implements PromptTemplate {

    private static final String PROMPT_STYLE = "fs1";

    private static final List<String> ALLOCATION_INFORMATION = List.of(
            "Configuration & Administration Service (CAS)",
            "Configuration & Administration User Interface (CAUI)",
            "Collector Services (CS)",
            "Watchdog (DOG)",
            "Environmental Metadata Cache (EMC)",
            "Environmental Metadata Services (EMS)",
            "Manual Entry User Interface (MEUI)",
            "Qualified Environmental Data (QEDC)",
            "Qualified Environmental Data Services (QEDS)",
            "Quality Checking Services (QChS)",
            "Schedule Service (SS)"
    );

    private static final String EXAMPLES = """
            --- Example 1 ---
            High-level requirement: The Clarus system shall disseminate data using standard Internet protocols.
            Allocation: [QEDS, EMS]
            Output:
            {"low_level_requirements":[
              {"allocation":"QEDS","requirement":"The QEDS system shall disseminate environmental data using standard Internet protocols."},
              {"allocation":"EMS","requirement":"The EMS shall disseminate metadata using standard Internet protocols."}
            ]}

            --- Example 2 ---
            High-level requirement: The Clarus system shall be able to implement quality checking rules for specific environmental situations.
            Allocation: [CAS, QChS]
            Output:
            {"low_level_requirements":[
              {"allocation":"CAS","requirement":"The CAS shall be able to configure quality checking rules for specific environmental situations."},
              {"allocation":"QChS","requirement":"The QChS shall be able to implement quality checking rules for specific environmental situations."}
            ]}

            --- Example 3 ---
            High-level requirement: The Clarus system shall be able to access remotely sensed environmental observations from data collectors.
            Allocation: [CS]
            Output:
            {"low_level_requirements":[
              {"allocation":"CS","requirement":"The CS shall be able to retrieve remotely sensed environmental observations from data collectors."}
            ]}
            """;

    @Override
    public String getPromptStyle() {
        return PROMPT_STYLE;
    }

    @Override
    public String buildPrompt(RequirementInput input) {
        Objects.requireNonNull(input, "Requirement input cannot be null.");

        String requestedAllocation = input.allocation().isEmpty()
                ? "Not supplied. Infer the smallest justified allocation from the catalogue and the examples."
                : input.allocation().stream().collect(Collectors.joining(", ", "[", "]"));

        return """
                You are a requirements engineer.

                Decompose the supplied high-level requirement into atomic, testable low-level requirements. Preserve the original intent and modal wording. Do not invent behavior or technical constraints. Create only the requirements justified by the high-level requirement and its allocation.

                Return JSON only, with no Markdown or explanatory prose, using exactly this schema:
                {"low_level_requirements":[{"allocation":"service acronym","requirement":"requirement text"}]}

                Valid allocation catalogue:
                %s

                %s

                --- New input ---
                High-level requirement: %s
                Allocation: %s
                Output:
                """.formatted(
                String.join(", ", ALLOCATION_INFORMATION),
                EXAMPLES.strip(),
                input.highLevelRequirement(),
                requestedAllocation
        );
    }
}
