package experiment.prompts;

import experiment.core.RequirementInput;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Collectors;

public final class ChainOfThought2PromptTemplate implements PromptTemplate {

    private static final String PROMPT_STYLE = "cot2";
    private static final Map<String, String> ALLOCATION_INFORMATION = allocationInformation();

    @Override
    public String getPromptStyle() {
        return PROMPT_STYLE;
    }

    @Override
    public String buildPrompt(RequirementInput input) {
        Objects.requireNonNull(input, "Requirement input cannot be null.");
        if (input.allocation().size() != 1) {
            throw new IllegalArgumentException("cot2 requires exactly one target allocation per prompt.");
        }

        String targetAllocation = input.allocation().get(0);
        String targetSubsystem = allocationDescription(targetAllocation);

        return """
                You are a requirements engineer. Derive atomic, testable low-level requirements for exactly one target subsystem.

                Reason through the following process internally before producing the answer:
                1. Identify the HLR's required action, affected information or behavior, constraints, and modal wording.
                2. Focus exclusively on the responsibility of the specified target subsystem.
                3. Translate that responsibility into one or more atomic, testable LLRs without inventing behavior.
                4. Verify that every LLR preserves the HLR's intent and modal wording.
                5. Remove requirements belonging to any other subsystem, along with speculative or duplicate requirements.
                6. Verify that every allocation value is exactly "%s".

                Do not reveal the reasoning process. Return only the final JSON object, with no Markdown or explanatory prose, using exactly this schema:
                {"low_level_requirements":[{"allocation":"%s","requirement":"requirement text"}]}

                Allocation catalogue:
                %s

                High-level requirement:
                %s

                Target allocation code: %s
                Target subsystem: %s

                Final JSON:
                """.formatted(
                targetAllocation,
                targetAllocation,
                formattedAllocationCatalogue(),
                input.highLevelRequirement(),
                targetAllocation,
                targetSubsystem
        );
    }

    public String allocationDescription(String allocationCode) {
        String description = ALLOCATION_INFORMATION.get(allocationCode);
        if (description == null) {
            throw new IllegalArgumentException("Unknown allocation code for cot2: " + allocationCode);
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
        return java.util.Collections.unmodifiableMap(allocations);
    }
}
