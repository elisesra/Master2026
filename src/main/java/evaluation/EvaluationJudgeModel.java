package evaluation;

import experiment.llm.LlmClient;
import experiment.llm.LlmModelConfig;
import experiment.llm.LlmProvider;

public record EvaluationJudgeModel(
        String displayName,
        String modelName,
        LlmProvider provider,
        LlmClient client
) {
    public EvaluationJudgeModel {
        if (displayName == null || displayName.isBlank()) {
            throw new IllegalArgumentException("Judge display name cannot be blank.");
        }
        if (modelName == null || modelName.isBlank()) {
            throw new IllegalArgumentException("Judge model name cannot be blank.");
        }
        if (provider == null) {
            throw new IllegalArgumentException("Judge provider cannot be null.");
        }
        if (client == null) {
            throw new IllegalArgumentException("Judge client cannot be null.");
        }
    }

    public static EvaluationJudgeModel fromConfig(LlmModelConfig config, LlmClient client) {
        return new EvaluationJudgeModel(
                config.getDisplayName(),
                config.getModelName(),
                config.getProvider(),
                client
        );
    }
}
