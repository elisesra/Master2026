package experiment.prompts;

import experiment.core.RequirementInput;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Collectors;

public final class ChainOfThought1PromptTemplate implements PromptTemplate {

    private static final String PROMPT_STYLE = "cot1";
    private static final Map<String, String> ALLOCATION_INFORMATION = allocationInformation();

    @Override
    public String getPromptStyle() {
        return PROMPT_STYLE;
    }

    @Override
    public String buildPrompt(RequirementInput input) {
        Objects.requireNonNull(input, "Requirement input cannot be null.");

        return """
                You are a requirements engineer. Derive atomic, testable low-level requirements from one high-level requirement.

                Reason through the following process internally before producing the answer:
                1. Identify the HLR's required action, affected information or behavior, constraints, and modal wording.
                2. Infer the smallest justified set of responsible subsystems from the allocation catalogue.
                3. Determine the responsibility contributed by each inferred subsystem without inventing behavior.
                4. Split compound responsibilities into atomic, testable LLRs only when necessary.
                5. Verify that every LLR preserves the HLR's intent and modal wording and names its responsible subsystem.
                6. Remove speculative, duplicate, or cross-subsystem requirements.

                Do not reveal the reasoning process. Return only the final JSON object, with no Markdown or explanatory prose, using exactly this schema:
                {"low_level_requirements":[{"allocation":"service acronym","requirement":"requirement text"}]}

                Allocation catalogue:
                %s

                High-level requirement:
                %s

                Final JSON:
                """.formatted(
                formattedAllocationCatalogue(),
                input.highLevelRequirement()
        );
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
