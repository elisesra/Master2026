package experiment.llm;

public final class LlmClientFactory {

    private LlmClientFactory() {
    }

    public static LlmClient create(LlmModelConfig config) {
        return switch (config.getProvider()) {
            case OPENAI -> new OpenAiResponsesClient(config);
            case ANTHROPIC -> new AnthropicMessagesClient(config);
            case GOOGLE_GEMINI -> new GeminiGenerateContentClient(config);
            case AZURE_OPENAI, MISTRAL, COHERE, OPENAI_COMPATIBLE -> new ChatCompletionsClient(config);
            default -> throw new IllegalArgumentException(
                    "No LLM client is implemented for provider: " + config.getProvider()
            );
        };
    }
}
