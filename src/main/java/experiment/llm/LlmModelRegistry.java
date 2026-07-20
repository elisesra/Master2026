package experiment.llm;

import java.util.List;
import java.util.Optional;

/**
 * Registry of supported LLM models.
 *
 * Add or remove models here as needed.
 */
public final class LlmModelRegistry {

    private LlmModelRegistry() {
        // Utility class.
    }

    public static List<LlmModelConfig> getDefaultModels() {
        return List.of(
                openAiGpt(),
                anthropicClaude(),
                googleGemini(),
                deepSeekV4Flash(),
                cohereCommand(),
                azureDeepSeek(),
                mistralMedium35(),
                gptOss20b(),
                gptOss120b(),
                qwen3()
        );
    }

    public static LlmModelConfig openAiGpt() {
        return LlmModelConfig.builder()
                .provider(LlmProvider.OPENAI)
                .displayName("OpenAI GPT")
                .modelName("gpt-4.1")
                .baseUrl("https://api.openai.com/v1")
                .apiKeyEnvironmentVariable("OPENAI_API_KEY")
                .build();
    }

    public static LlmModelConfig anthropicClaude() {
        return LlmModelConfig.builder()
                .provider(LlmProvider.ANTHROPIC)
                .displayName("Anthropic Claude")
                .modelName("claude-sonnet-5")
                .baseUrl("https://api.anthropic.com/v1")
                .apiKeyEnvironmentVariable("ANTHROPIC_API_KEY")
                .build();
    }

    public static LlmModelConfig googleGemini() {
        return LlmModelConfig.builder()
                .provider(LlmProvider.GOOGLE_GEMINI)
                .displayName("Google Gemini")
                .modelName("gemini-3.5-flash")
                .baseUrl("https://generativelanguage.googleapis.com/v1beta")
                .apiKeyEnvironmentVariable("GEMINI_API_KEY")
                .build();
    }

    public static LlmModelConfig deepSeekV4Flash() {
        return LlmModelConfig.builder()
                .provider(LlmProvider.OPENAI_COMPATIBLE)
                .displayName("DeepSeek V4 Flash")
                .modelName("deepseek-v4-flash")
                .baseUrl("https://api.deepseek.com")
                .apiKeyEnvironmentVariable("DEEPSEEK_API_KEY")
                .build();
    }

    public static LlmModelConfig mistralMedium35() {
        return LlmModelConfig.builder()
                .provider(LlmProvider.MISTRAL)
                .displayName("Mistral Medium 3.5")
                .modelName("mistral-medium-3-5")
                .baseUrl("https://api.mistral.ai/v1")
                .apiKeyEnvironmentVariable("MISTRAL_API_KEY")
                .build();
    }

    public static LlmModelConfig azureDeepSeek() {
        String deploymentName = System.getenv("AZURE_DEEPSEEK_DEPLOYMENT");
        if (deploymentName == null || deploymentName.isBlank()) {
            deploymentName = "deepseek-r1";
        }
        return LlmModelConfig.builder()
                .provider(LlmProvider.AZURE_OPENAI)
                .displayName("Azure DeepSeek")
                .modelName(deploymentName)
                .baseUrlEnvironmentVariable("AZURE_DEEPSEEK_ENDPOINT")
                .apiKeyEnvironmentVariable("AZURE_DEEPSEEK_API_KEY")
                .build();
    }

    public static LlmModelConfig gptOss20b() {
        return localOpenAiCompatibleModel(
                "gpt-oss 20B",
                environmentOrDefault("GPT_OSS_20B_MODEL", "gpt-oss:20b")
        );
    }

    public static LlmModelConfig gptOss120b() {
        return localOpenAiCompatibleModel(
                "gpt-oss 120B",
                environmentOrDefault("GPT_OSS_120B_MODEL", "gpt-oss:120b")
        );
    }

    public static LlmModelConfig qwen3() {
        return localOpenAiCompatibleModel(
                "Qwen3 30B-A3B",
                environmentOrDefault("QWEN3_MODEL", "qwen3:30b-a3b")
        );
    }

    private static LlmModelConfig localOpenAiCompatibleModel(String displayName, String modelName) {
        return LlmModelConfig.builder()
                .provider(LlmProvider.OPENAI_COMPATIBLE)
                .displayName(displayName)
                .modelName(modelName)
                .baseUrl("http://localhost:11434/v1")
                .baseUrlEnvironmentVariable("LOCAL_LLM_BASE_URL")
                .apiKeyEnvironmentVariable("LOCAL_LLM_API_KEY")
                .build();
    }

    private static String environmentOrDefault(String environmentVariable, String defaultValue) {
        String value = System.getenv(environmentVariable);
        return value == null || value.isBlank() ? defaultValue : value.trim();
    }

    public static LlmModelConfig cohereCommand() {
        return LlmModelConfig.builder()
                .provider(LlmProvider.COHERE)
                .displayName("Cohere Command A+")
                .modelName("command-a-plus-05-2026")
                .baseUrl("https://api.cohere.ai/compatibility/v1")
                .apiKeyEnvironmentVariable("COHERE_API_KEY")
                .build();
    }

    public static LlmModelConfig xaiGrok() {
        return LlmModelConfig.builder()
                .provider(LlmProvider.XAI)
                .displayName("xAI Grok")
                .modelName("grok-4.3")
                .baseUrl("https://api.x.ai/v1")
                .apiKeyEnvironmentVariable("XAI_API_KEY")
                .build();
    }

    public static Optional<LlmModelConfig> findByDisplayName(String displayName) {
        if (displayName == null || displayName.isBlank()) {
            return Optional.empty();
        }

        return getDefaultModels()
                .stream()
                .filter(model -> model.getDisplayName().equalsIgnoreCase(displayName.trim()))
                .findFirst();
    }

    public static Optional<LlmModelConfig> findByModelName(String modelName) {
        if (modelName == null || modelName.isBlank()) {
            return Optional.empty();
        }

        return getDefaultModels()
                .stream()
                .filter(model -> model.getModelName().equalsIgnoreCase(modelName.trim()))
                .findFirst();
    }
}
