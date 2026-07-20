package experiment.panacea;

import java.util.List;

public record PanaceaPromptEvaluation(
        int predictedLlrCount,
        int groundTruthLlrCount,
        double coverageScore,
        double precisionScore,
        long missingGroundTruthCount,
        long extraPredictionCount,
        List<String> issueCodes,
        String groundTruthFingerprint,
        String updatedPrompt
) {
}
