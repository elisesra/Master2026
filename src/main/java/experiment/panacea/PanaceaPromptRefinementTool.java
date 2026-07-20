package experiment.panacea;

import com.fasterxml.jackson.databind.JsonNode;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HexFormat;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.regex.Pattern;

public final class PanaceaPromptRefinementTool {

    private static final Pattern NON_WORD = Pattern.compile("[^a-z0-9]+");

    public PanaceaPromptEvaluation evaluateAndRefine(
            String currentPrompt,
            JsonNode predictedResponse,
            List<String> groundTruthLowLevelRequirements
    ) {
        if (currentPrompt == null || currentPrompt.isBlank()) {
            throw new IllegalArgumentException("Current prompt cannot be blank.");
        }
        List<String> predicted = predictedRequirements(predictedResponse);
        List<String> groundTruth = groundTruthLowLevelRequirements == null
                ? List.of()
                : groundTruthLowLevelRequirements;
        if (groundTruth.isEmpty()) {
            throw new IllegalArgumentException("Ground-truth LLRs are required for Panacea evaluation.");
        }

        double coverage = averageBestOverlap(groundTruth, predicted);
        double precision = averageBestOverlap(predicted, groundTruth);
        long missing = groundTruth.stream()
                .filter(expected -> bestOverlap(expected, predicted) < 0.45)
                .count();
        long extra = predicted.stream()
                .filter(actual -> bestOverlap(actual, groundTruth) < 0.35)
                .count();
        boolean countMismatch = predicted.size() != groundTruth.size();
        boolean missingShall = predicted.stream()
                .anyMatch(requirement -> !requirement.toLowerCase(Locale.ROOT).contains("shall"));
        boolean compoundLikely = predicted.stream()
                .anyMatch(requirement -> requirement.toLowerCase(Locale.ROOT).contains(" and "));

        List<String> issues = new ArrayList<>();
        List<String> guidance = new ArrayList<>();
        if (coverage < 0.70 || missing > 0) {
            issues.add("coverage_gap");
            guidance.add("Cover every distinct behavior, condition, and constraint expressed by the HLR.");
        }
        if (precision < 0.65 || extra > 0) {
            issues.add("extra_or_speculative_behavior");
            guidance.add("Do not add capabilities, technologies, constraints, or subsystem behavior not justified by the HLR.");
        }
        if (countMismatch) {
            issues.add("llr_count_mismatch");
            guidance.add("Generate as many LLRs as necessary; do not force exactly one LLR when the HLR contains multiple responsibilities.");
        }
        if (missingShall) {
            issues.add("weak_requirement_language");
            guidance.add("Write mandatory requirements using shall-language.");
        }
        if (compoundLikely) {
            issues.add("possible_non_atomic_requirement");
            guidance.add("Split compound requirements into separate atomic, testable LLRs when they describe different responsibilities.");
        }
        if (issues.isEmpty()) {
            issues.add("no_major_issue_detected");
            guidance.add("Keep the prompt stable when the prediction already matches the reference well.");
        }

        String updatedPrompt = refinePrompt(currentPrompt, guidance);
        guardAgainstLeakage(updatedPrompt, groundTruth);

        return new PanaceaPromptEvaluation(
                predicted.size(),
                groundTruth.size(),
                coverage,
                precision,
                missing,
                extra,
                List.copyOf(issues),
                fingerprint(groundTruth),
                updatedPrompt
        );
    }

    public void guardGenerationPrompt(String generationPrompt, List<String> groundTruthLowLevelRequirements) {
        guardAgainstLeakage(generationPrompt, groundTruthLowLevelRequirements);
        String normalized = generationPrompt.toLowerCase(Locale.ROOT);
        if (normalized.contains("ground truth")
                || normalized.contains("ground-truth")
                || normalized.contains("reference llr")
                || normalized.contains("expected llr")) {
            throw new IllegalStateException(
                    "Generation prompt contains evaluation-only terminology and may leak ground truth."
            );
        }
    }

    private static List<String> predictedRequirements(JsonNode predictedResponse) {
        if (predictedResponse == null) {
            return List.of();
        }
        List<String> requirements = new ArrayList<>();
        for (JsonNode requirement : predictedResponse.path("low_level_requirements")) {
            String text = requirement.path("requirement").asText();
            if (!text.isBlank()) {
                requirements.add(text);
            }
        }
        return List.copyOf(requirements);
    }

    private static String refinePrompt(String currentPrompt, List<String> guidance) {
        Set<String> lines = new LinkedHashSet<>();
        for (String line : currentPrompt.strip().split("\\R")) {
            lines.add(line);
        }
        lines.add("");
        lines.add("Prompt refinement guidance:");
        guidance.stream()
                .sorted(Comparator.naturalOrder())
                .map(line -> "- " + line)
                .forEach(lines::add);
        return String.join("\n", lines).strip();
    }

    private static double averageBestOverlap(List<String> source, List<String> candidates) {
        if (source.isEmpty()) {
            return 0;
        }
        return source.stream()
                .mapToDouble(value -> bestOverlap(value, candidates))
                .average()
                .orElse(0);
    }

    private static double bestOverlap(String value, List<String> candidates) {
        Set<String> sourceTokens = tokens(value);
        if (sourceTokens.isEmpty() || candidates.isEmpty()) {
            return 0;
        }
        return candidates.stream()
                .map(PanaceaPromptRefinementTool::tokens)
                .mapToDouble(candidateTokens -> overlap(sourceTokens, candidateTokens))
                .max()
                .orElse(0);
    }

    private static double overlap(Set<String> expected, Set<String> actual) {
        if (expected.isEmpty()) {
            return 0;
        }
        long shared = expected.stream().filter(actual::contains).count();
        return shared / (double) expected.size();
    }

    private static Set<String> tokens(String value) {
        String[] raw = NON_WORD.split(value.toLowerCase(Locale.ROOT));
        Set<String> tokens = new LinkedHashSet<>();
        for (String token : raw) {
            if (token.length() > 3 && !token.equals("shall") && !token.equals("system")) {
                tokens.add(token);
            }
        }
        return tokens;
    }

    private static void guardAgainstLeakage(String candidatePrompt, List<String> groundTruthLowLevelRequirements) {
        if (candidatePrompt == null) {
            throw new IllegalArgumentException("Candidate prompt cannot be null.");
        }
        for (String truth : groundTruthLowLevelRequirements == null ? List.<String>of() : groundTruthLowLevelRequirements) {
            if (!truth.isBlank() && candidatePrompt.contains(truth)) {
                throw new IllegalStateException(
                        "Ground-truth LLR text leaked into a generation prompt or optimized prompt."
                );
            }
        }
    }

    private static String fingerprint(List<String> groundTruthLowLevelRequirements) {
        return sha256(String.join("\n", groundTruthLowLevelRequirements));
    }

    private static String sha256(String value) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            return HexFormat.of().formatHex(digest.digest(value.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("SHA-256 is unavailable.", exception);
        }
    }
}
