package experiment.prompts;

import experiment.core.PanaceaAllocationRequirementInput;
import experiment.core.RequirementInput;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Collectors;

public final class Panacea2PromptTemplate implements PromptTemplate {

    public static final String HLR_PLACEHOLDER = "{HLR}";
    public static final String TARGET_ALLOCATION_PLACEHOLDER = "{TARGET_ALLOCATION}";
    public static final String TARGET_SUBSYSTEM_PLACEHOLDER = "{TARGET_SUBSYSTEM}";

    private static final String PROMPT_STYLE = "panacea2";
    private static final Map<String, String> ALLOCATIONS = allocations();

    private static final String INITIAL_PROMPT = """
            Create low-level requirements for exactly one target subsystem.

            Scope rules:
            - Generate only singular, atomic low-level requirements that belong to the target subsystem.
            - Do not generate requirements for any other subsystem, even if the HLR could involve them.
            - Every returned allocation value must be exactly "{TARGET_ALLOCATION}".
            - Return at least one low-level requirement for the target subsystem.

            Return JSON only, with no Markdown or explanatory prose, using exactly this schema:
            {"low_level_requirements":[{"allocation":"{TARGET_ALLOCATION}","requirement":"requirement text"}]}

            Allocation catalogue:
            %s

            High-level requirement:
            {HLR}

            Target allocation code: {TARGET_ALLOCATION}
            Target subsystem: {TARGET_SUBSYSTEM}
            """.formatted(formattedCatalogue());

    @Override
    public String getPromptStyle() {
        return PROMPT_STYLE;
    }

    @Override
    public String buildPrompt(RequirementInput input) {
        Objects.requireNonNull(input, "Requirement input cannot be null.");
        if (input.allocation().size() != 1) {
            throw new IllegalArgumentException("panacea2 requires exactly one target allocation.");
        }
        return buildGenerationPrompt(initialPrompt(), input.highLevelRequirement(), input.allocation().get(0));
    }

    public String buildPrompt(PanaceaAllocationRequirementInput input) {
        Objects.requireNonNull(input, "Panacea2 input cannot be null.");
        return buildGenerationPrompt(initialPrompt(), input.highLevelRequirement(), input.targetAllocation());
    }

    public String initialPrompt() {
        return INITIAL_PROMPT.strip();
    }

    public String buildGenerationPrompt(
            String promptTemplate,
            String highLevelRequirement,
            String targetAllocation
    ) {
        if (promptTemplate == null || promptTemplate.isBlank()) {
            throw new IllegalArgumentException("Prompt template cannot be blank.");
        }
        if (!promptTemplate.contains(HLR_PLACEHOLDER)) {
            throw new IllegalArgumentException("Prompt template must contain " + HLR_PLACEHOLDER + ".");
        }
        if (!promptTemplate.contains(TARGET_ALLOCATION_PLACEHOLDER)) {
            throw new IllegalArgumentException(
                    "Prompt template must contain " + TARGET_ALLOCATION_PLACEHOLDER + "."
            );
        }
        if (!promptTemplate.contains(TARGET_SUBSYSTEM_PLACEHOLDER)) {
            throw new IllegalArgumentException(
                    "Prompt template must contain " + TARGET_SUBSYSTEM_PLACEHOLDER + "."
            );
        }
        if (highLevelRequirement == null || highLevelRequirement.isBlank()) {
            throw new IllegalArgumentException("High-level requirement cannot be blank.");
        }
        String targetSubsystem = allocationDescription(targetAllocation);
        return promptTemplate
                .replace(HLR_PLACEHOLDER, highLevelRequirement)
                .replace(TARGET_ALLOCATION_PLACEHOLDER, targetAllocation.trim())
                .replace(TARGET_SUBSYSTEM_PLACEHOLDER, targetSubsystem);
    }

    public String allocationDescription(String allocationCode) {
        String description = ALLOCATIONS.get(allocationCode);
        if (description == null) {
            throw new IllegalArgumentException("Unknown allocation code for panacea2: " + allocationCode);
        }
        return description;
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
