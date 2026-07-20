package experiment.prompts;

import experiment.core.RequirementInput;

import java.util.LinkedHashMap;
import java.util.Collections;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Collectors;

public final class FewShot2PromptTemplate implements PromptTemplate {

    private static final String PROMPT_STYLE = "fs2";

    private static final Map<String, String> ALLOCATION_INFORMATION = allocationInformation();

    private static final String EXAMPLES = """
            --- Example 1 ---
            High-level requirement: The Clarus system shall disseminate data using standard Internet protocols.
            Target allocation code: QEDS
            Target subsystem: Qualified Environmental Data Services
            Output:
            {"low_level_requirements":[
              {"allocation":"QEDS","requirement":"The QEDS system shall disseminate environmental data using standard Internet protocols."}
            ]}

            --- Example 2 ---
            High-level requirement: The Clarus system shall be able to implement quality checking rules for specific environmental situations.
            Target allocation code: CAS
            Target subsystem: Configuration & Administration Service
            Output:
            {"low_level_requirements":[
              {"allocation":"CAS","requirement":"The CAS shall be able to configure quality checking rules for specific environmental situations."}
            ]}

            --- Example 3 ---
            High-level requirement: The Clarus system shall be able to access remotely sensed environmental observations from data collectors.
            Target allocation code: CS
            Target subsystem: Collector Services
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
        if (input.allocation().size() != 1) {
            throw new IllegalArgumentException("fs2 requires exactly one target allocation per prompt.");
        }

        String targetAllocation = input.allocation().get(0);
        String targetSubsystem = allocationDescription(targetAllocation);

        return """
                You are a requirements engineer.

                Decompose the supplied high-level requirement into atomic, testable low-level requirements for exactly one target subsystem. Preserve the original intent and modal wording. Do not invent behavior or technical constraints.

                Scope rules:
                - Generate only the LLR or LLRs that belong to the target subsystem.
                - Do not generate requirements for any other subsystem, even if the HLR could involve them.
                - Every returned allocation value must be exactly "%s".
                - Return at least one LLR for the target subsystem.

                Return JSON only, with no Markdown or explanatory prose, using exactly this schema:
                {"low_level_requirements":[{"allocation":"%s","requirement":"requirement text"}]}

                Allocation catalogue:
                %s

                %s

                --- New input ---
                High-level requirement: %s
                Target allocation code: %s
                Target subsystem: %s
                Output:
                """.formatted(
                targetAllocation,
                targetAllocation,
                formattedAllocationCatalogue(),
                EXAMPLES.strip(),
                input.highLevelRequirement(),
                targetAllocation,
                targetSubsystem
        );
    }

    public String allocationDescription(String allocationCode) {
        String description = ALLOCATION_INFORMATION.get(allocationCode);
        if (description == null) {
            throw new IllegalArgumentException("Unknown allocation code for fs2: " + allocationCode);
        }
        return description;
    }

    private static String formattedAllocationCatalogue() {
        return ALLOCATION_INFORMATION.entrySet().stream()
                .map(entry -> "- " + entry.getKey() + " = " + entry.getValue())
                .collect(Collectors.joining("\n"));
    }

    private static Map<String, String> allocationInformation() {
        Map<String, String> allocations = new LinkedHashMap<>();
        allocations.put("CAS", "Configuration & Administration Service");
        allocations.put("CAUI", "Configuration & Administration User Interface");
        allocations.put("CS", "Collector Services");
        allocations.put("DOG", "Watchdog");
        allocations.put("EMC", "Environmental Metadata Cache");
        allocations.put("EMS", "Environmental Metadata Services");
        allocations.put("MEUI", "Manual Entry User Interface");
        allocations.put("QEDC", "Qualified Environmental Data");
        allocations.put("QEDS", "Qualified Environmental Data Services");
        allocations.put("QChS", "Quality Checking Services");
        allocations.put("SS", "Schedule Service");
        return Collections.unmodifiableMap(allocations);
    }
}
