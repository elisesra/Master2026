package evaluation;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Locale;
import java.util.Set;
import java.util.regex.Pattern;

final class TextSimilarity {

    private static final Pattern NON_WORD = Pattern.compile("[^a-z0-9]+");

    private TextSimilarity() {
    }

    static double tokenF1(String left, String right) {
        Set<String> leftTokens = tokenSet(left);
        Set<String> rightTokens = tokenSet(right);
        if (leftTokens.isEmpty() || rightTokens.isEmpty()) {
            return 0;
        }
        long shared = leftTokens.stream().filter(rightTokens::contains).count();
        double precision = shared / (double) leftTokens.size();
        double recall = shared / (double) rightTokens.size();
        if (precision + recall == 0) {
            return 0;
        }
        return 2 * precision * recall / (precision + recall);
    }

    static double cosineSimilarity(String left, String right) {
        Map<String, Integer> leftCounts = tokenCounts(left);
        Map<String, Integer> rightCounts = tokenCounts(right);
        if (leftCounts.isEmpty() || rightCounts.isEmpty()) {
            return 0;
        }

        double dot = 0;
        for (Map.Entry<String, Integer> entry : leftCounts.entrySet()) {
            dot += entry.getValue() * rightCounts.getOrDefault(entry.getKey(), 0);
        }
        double leftNorm = leftCounts.values().stream()
                .mapToDouble(value -> value * value)
                .sum();
        double rightNorm = rightCounts.values().stream()
                .mapToDouble(value -> value * value)
                .sum();
        if (leftNorm == 0 || rightNorm == 0) {
            return 0;
        }
        return clamp(dot / (Math.sqrt(leftNorm) * Math.sqrt(rightNorm)));
    }

    static double rougeL(String candidate, String reference) {
        List<String> candidateTokens = tokens(candidate);
        List<String> referenceTokens = tokens(reference);
        if (candidateTokens.isEmpty() || referenceTokens.isEmpty()) {
            return 0;
        }
        int lcs = longestCommonSubsequence(candidateTokens, referenceTokens);
        double precision = lcs / (double) candidateTokens.size();
        double recall = lcs / (double) referenceTokens.size();
        if (precision + recall == 0) {
            return 0;
        }
        return clamp(2 * precision * recall / (precision + recall));
    }

    static double bleu(String candidate, String reference) {
        List<String> candidateTokens = tokens(candidate);
        List<String> referenceTokens = tokens(reference);
        if (candidateTokens.isEmpty() || referenceTokens.isEmpty()) {
            return 0;
        }
        int maxN = Math.min(4, candidateTokens.size());
        double logPrecisionTotal = 0;
        for (int n = 1; n <= maxN; n++) {
            Map<String, Integer> candidateNgrams = ngrams(candidateTokens, n);
            Map<String, Integer> referenceNgrams = ngrams(referenceTokens, n);
            int overlap = 0;
            int total = 0;
            for (Map.Entry<String, Integer> entry : candidateNgrams.entrySet()) {
                overlap += Math.min(entry.getValue(), referenceNgrams.getOrDefault(entry.getKey(), 0));
                total += entry.getValue();
            }
            double precision = n == 1
                    ? overlap / (double) Math.max(total, 1)
                    : (overlap + 1.0) / (total + 1.0);
            if (precision == 0) {
                return 0;
            }
            logPrecisionTotal += Math.log(precision);
        }
        double brevityPenalty = candidateTokens.size() > referenceTokens.size()
                ? 1
                : Math.exp(1 - referenceTokens.size() / (double) candidateTokens.size());
        return clamp(brevityPenalty * Math.exp(logPrecisionTotal / maxN));
    }

    private static Set<String> tokenSet(String text) {
        return new LinkedHashSet<>(tokens(text));
    }

    private static Map<String, Integer> tokenCounts(String text) {
        Map<String, Integer> counts = new HashMap<>();
        for (String token : tokens(text)) {
            counts.merge(token, 1, Integer::sum);
        }
        return counts;
    }

    private static List<String> tokens(String text) {
        Set<String> tokens = new LinkedHashSet<>();
        if (text == null) {
            return List.of();
        }
        for (String token : NON_WORD.split(text.toLowerCase(Locale.ROOT))) {
            if (token.length() > 2 && !token.equals("the") && !token.equals("shall")) {
                tokens.add(token);
            }
        }
        return new ArrayList<>(tokens);
    }

    private static int longestCommonSubsequence(List<String> left, List<String> right) {
        int[][] lengths = new int[left.size() + 1][right.size() + 1];
        for (int leftIndex = 1; leftIndex <= left.size(); leftIndex++) {
            for (int rightIndex = 1; rightIndex <= right.size(); rightIndex++) {
                if (left.get(leftIndex - 1).equals(right.get(rightIndex - 1))) {
                    lengths[leftIndex][rightIndex] = lengths[leftIndex - 1][rightIndex - 1] + 1;
                } else {
                    lengths[leftIndex][rightIndex] = Math.max(
                            lengths[leftIndex - 1][rightIndex],
                            lengths[leftIndex][rightIndex - 1]
                    );
                }
            }
        }
        return lengths[left.size()][right.size()];
    }

    private static Map<String, Integer> ngrams(List<String> tokens, int size) {
        Map<String, Integer> ngrams = new HashMap<>();
        for (int index = 0; index <= tokens.size() - size; index++) {
            String ngram = String.join(" ", tokens.subList(index, index + size));
            ngrams.merge(ngram, 1, Integer::sum);
        }
        return ngrams;
    }

    private static double clamp(double value) {
        if (value < 0) {
            return 0;
        }
        if (value > 1) {
            return 1;
        }
        return value;
    }
}
